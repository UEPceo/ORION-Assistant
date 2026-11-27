package com.orion.voicelab.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Gestiona el enrutamiento de audio en llamada virtual entre altavoz, auriculares con cable y Bluetooth.
 * Restaura siempre el modo de audio original del sistema al finalizar.
 */
class AudioRoutingController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var active = false

    @Volatile
    private var previousMode: Int = AudioManager.MODE_NORMAL

    @Volatile
    private var previousSpeakerphone: Boolean = false

    @Volatile
    private var listener: ((String) -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (active) applyPreferredRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (active) applyPreferredRoute()
        }
    }

    fun startCallRouting(onRouteChanged: (String) -> Unit): String {
        listener = onRouteChanged
        if (!active) {
            active = true
            previousMode = audioManager.mode
            @Suppress("DEPRECATION")
            previousSpeakerphone = audioManager.isSpeakerphoneOn
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            } catch (_: Exception) {
            }
            try {
                audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            } catch (_: Exception) {
            }
        }
        return applyPreferredRoute()
    }

    fun currentRouteLabel(): String {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return describeOutputRoute(outputs)
    }

    fun stopCallRouting() {
        if (!active) return
        active = false
        listener = null
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = previousSpeakerphone
            }
        } catch (_: Exception) {
        }
        try {
            audioManager.mode = previousMode
        } catch (_: Exception) {
        }
    }

    private fun applyPreferredRoute(): String {
        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray()
        }

        val hasBluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == AudioDeviceInfo.TYPE_HEARING_AID))
        }
        val hasWired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevices = audioManager.availableCommunicationDevices
                val preferred = when {
                    hasWired -> commDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    }
                    hasBluetooth -> commDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_HEARING_AID
                    }
                    else -> commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                }
                if (preferred != null) {
                    audioManager.setCommunicationDevice(preferred)
                } else if (hasWired || hasBluetooth) {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = !(hasWired || hasBluetooth)
            }
        } catch (_: Exception) {
        }

        val label = describeOutputRoute(outputs)
        listener?.invoke(label)
        return label
    }

    internal fun describeOutputRoute(outputs: Array<out AudioDeviceInfo>): String {
        val hasWired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (hasWired) return "Auriculares"

        val hasBluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == AudioDeviceInfo.TYPE_HEARING_AID))
        }
        if (hasBluetooth) return "Bluetooth"

        return "Altavoz"
    }
}
