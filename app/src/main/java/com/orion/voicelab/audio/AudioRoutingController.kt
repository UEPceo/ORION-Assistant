package com.orion.voicelab.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

internal enum class OutputDeviceKind {
    WIRED_HEADSET,
    BLUETOOTH,
    SPEAKER,
    OTHER
}

internal interface AudioRoutingBackend {
    val isApiSOrAbove: Boolean
    fun getMode(): Int
    fun setMode(mode: Int)
    fun isSpeakerphoneOn(): Boolean
    fun setSpeakerphoneOn(on: Boolean)
    fun getOutputDevices(): List<OutputDeviceKind>
    fun selectCommunicationDevice(preferredKind: OutputDeviceKind): Boolean
    fun clearCommunicationDevice()
    fun registerDeviceCallback(onDevicesChanged: () -> Unit)
    fun unregisterDeviceCallback()
}

private class AndroidAudioRoutingBackend(context: Context) : AudioRoutingBackend {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var deviceCallback: AudioDeviceCallback? = null

    override val isApiSOrAbove: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun getMode(): Int = audioManager.mode

    override fun setMode(mode: Int) {
        audioManager.mode = mode
    }

    @Suppress("DEPRECATION")
    override fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn

    @Suppress("DEPRECATION")
    override fun setSpeakerphoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    override fun getOutputDevices(): List<OutputDeviceKind> {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.map { classifyDeviceType(it.type) }
    }

    override fun selectCommunicationDevice(preferredKind: OutputDeviceKind): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val commDevices = audioManager.availableCommunicationDevices
        val preferred = when (preferredKind) {
            OutputDeviceKind.WIRED_HEADSET -> commDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            OutputDeviceKind.BLUETOOTH -> commDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
            OutputDeviceKind.SPEAKER -> commDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            OutputDeviceKind.OTHER -> null
        }
        return if (preferred != null) {
            audioManager.setCommunicationDevice(preferred)
            true
        } else {
            audioManager.clearCommunicationDevice()
            false
        }
    }

    override fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    override fun registerDeviceCallback(onDevicesChanged: () -> Unit) {
        unregisterDeviceCallback()
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                onDevicesChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                onDevicesChanged()
            }
        }
        deviceCallback = cb
        audioManager.registerAudioDeviceCallback(cb, mainHandler)
    }

    override fun unregisterDeviceCallback() {
        deviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
            deviceCallback = null
        }
    }

    private fun classifyDeviceType(type: Int): OutputDeviceKind = when {
        type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET -> OutputDeviceKind.WIRED_HEADSET
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    type == AudioDeviceInfo.TYPE_HEARING_AID)) -> OutputDeviceKind.BLUETOOTH
        type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputDeviceKind.SPEAKER
        else -> OutputDeviceKind.OTHER
    }
}

/**
 * Gestiona el enrutamiento de audio en llamada virtual entre altavoz, auriculares con cable y Bluetooth.
 * Restaura siempre el modo de audio original del sistema al finalizar.
 */
class AudioRoutingController internal constructor(
    private val backend: AudioRoutingBackend
) {
    constructor(context: Context) : this(AndroidAudioRoutingBackend(context))

    private val lock = Any()
    private var active = false
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false
    private var forceSpeakerEnabled: Boolean = false
    private var listener: ((String) -> Unit)? = null

    fun startCallRouting(onRouteChanged: (String) -> Unit): String {
        val (label, cb) = synchronized(lock) {
            listener = onRouteChanged
            if (!active) {
                active = true
                previousMode = try {
                    backend.getMode()
                } catch (_: Exception) {
                    AudioManager.MODE_NORMAL
                }
                previousSpeakerphone = try {
                    backend.isSpeakerphoneOn()
                } catch (_: Exception) {
                    false
                }
                try {
                    backend.setMode(AudioManager.MODE_IN_COMMUNICATION)
                } catch (_: Exception) {
                }
                try {
                    backend.registerDeviceCallback {
                        onDevicesUpdatedFromSystem()
                    }
                } catch (_: Exception) {
                }
            }
            applyPreferredRouteLocked() to listener
        }
        cb?.invoke(label)
        return label
    }

    fun setSpeakerEnabled(enabled: Boolean): String {
        val (label, callback) = synchronized(lock) {
            forceSpeakerEnabled = enabled
            val route = if (active) applyPreferredRouteLocked() else if (enabled) "Altavoz" else currentRouteLabel()
            route to listener
        }
        callback?.invoke(label)
        return label
    }

    fun currentRouteLabel(): String {
        val outputs = try {
            backend.getOutputDevices()
        } catch (_: Exception) {
            emptyList()
        }
        return describeOutputRoute(outputs)
    }

    fun stopCallRouting() {
        synchronized(lock) {
            if (!active) return
            active = false
            listener = null
            try {
                backend.unregisterDeviceCallback()
            } catch (_: Exception) {
            }
            try {
                if (backend.isApiSOrAbove) {
                    backend.clearCommunicationDevice()
                } else {
                    backend.setSpeakerphoneOn(previousSpeakerphone)
                }
            } catch (_: Exception) {
            }
            try {
                backend.setMode(previousMode)
            } catch (_: Exception) {
            }
        }
    }

    private fun onDevicesUpdatedFromSystem() {
        val (label, cb) = synchronized(lock) {
            if (!active) return
            applyPreferredRouteLocked() to listener
        }
        cb?.invoke(label)
    }

    private fun applyPreferredRouteLocked(): String {
        val outputs = try {
            backend.getOutputDevices()
        } catch (_: Exception) {
            emptyList()
        }

        val hasWired = outputs.any { it == OutputDeviceKind.WIRED_HEADSET }
        val hasBluetooth = outputs.any { it == OutputDeviceKind.BLUETOOTH }

        try {
            if (backend.isApiSOrAbove) {
                val targetKind = when {
                    forceSpeakerEnabled -> OutputDeviceKind.SPEAKER
                    hasWired -> OutputDeviceKind.WIRED_HEADSET
                    hasBluetooth -> OutputDeviceKind.BLUETOOTH
                    else -> OutputDeviceKind.SPEAKER
                }
                val selected = backend.selectCommunicationDevice(targetKind)
                if (!selected) {
                    backend.clearCommunicationDevice()
                }
            } else {
                backend.setSpeakerphoneOn(forceSpeakerEnabled || !(hasWired || hasBluetooth))
            }
        } catch (_: Exception) {
        }

        return if (forceSpeakerEnabled) "Altavoz" else describeOutputRoute(outputs)
    }

    internal fun describeOutputRoute(outputs: List<OutputDeviceKind>): String {
        if (outputs.any { it == OutputDeviceKind.WIRED_HEADSET }) return "Auriculares"
        if (outputs.any { it == OutputDeviceKind.BLUETOOTH }) return "Bluetooth"
        return "Altavoz"
    }
}
