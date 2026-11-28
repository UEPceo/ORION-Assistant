package com.orion.voicelab.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.orion.voicelab.data.SystemTelephonyState

sealed interface CallDispatchResult {
    data class DirectCallStarted(val sanitizedNumber: String) : CallDispatchResult
    data class DialerOpened(val sanitizedNumber: String) : CallDispatchResult
    data class Failed(val message: String) : CallDispatchResult
}

/**
 * Encapsula la integración oficial con telefonía Android sin obligar a ORION a ser la app
 * de teléfono predeterminada (RoleManager).
 *
 * Limitación conocida de Android: las apps estándar no pueden capturar ni inyectar audio
 * de una llamada celular en curso (VOICE_DOWNLINK / VOICE_UPLINK reservados al sistema).
 */
class PhoneCallManager(private val context: Context) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    @Volatile
    private var modernCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    @Volatile
    private var legacyListener: PhoneStateListener? = null

    fun isObservingCallState(): Boolean = modernCallback != null || legacyListener != null

    fun isTelephonySupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Reconciles call state immediately after ACTION_CALL dispatch. A telephony callback can
     * arrive before the call record is created, so the caller must query current state too.
     */
    fun currentCallState(): SystemTelephonyState {
        if (!hasPhoneStatePermission()) return SystemTelephonyState.UNAVAILABLE
        val tm = telephonyManager ?: return SystemTelephonyState.UNAVAILABLE
        return try {
            mapTelephonyState(tm.callState)
        } catch (_: SecurityException) {
            SystemTelephonyState.UNAVAILABLE
        } catch (_: Exception) {
            SystemTelephonyState.UNAVAILABLE
        }
    }

    fun dispatchCall(phoneNumber: String, preferDirectCall: Boolean): CallDispatchResult {
        val sanitized = sanitizeDialString(phoneNumber)
        if (sanitized.isEmpty() || sanitized.none { it.isDigit() }) {
            return CallDispatchResult.Failed("Número de teléfono no válido.")
        }
        val uri = Uri.parse("tel:$sanitized")

        if (preferDirectCall && hasCallPhonePermission()) {
            return try {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                CallDispatchResult.DirectCallStarted(sanitized)
            } catch (_: SecurityException) {
                openDialerFallback(sanitized, uri)
            } catch (e: Exception) {
                CallDispatchResult.Failed(e.message ?: "No se pudo iniciar la llamada telefónica.")
            }
        }
        return openDialerFallback(sanitized, uri)
    }

    private fun openDialerFallback(sanitized: String, uri: Uri): CallDispatchResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CallDispatchResult.DialerOpened(sanitized)
        } catch (e: Exception) {
            CallDispatchResult.Failed(e.message ?: "No hay aplicación de marcador disponible en este dispositivo.")
        }
    }

    fun startObservingCallState(onStateChanged: (SystemTelephonyState) -> Unit) {
        val tm = telephonyManager ?: run {
            onStateChanged(SystemTelephonyState.UNAVAILABLE)
            return
        }
        if (!hasPhoneStatePermission()) {
            stopObservingCallState()
            onStateChanged(SystemTelephonyState.IDLE)
            return
        }
        if (isObservingCallState()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        onStateChanged(mapTelephonyState(state))
                    }
                }
                modernCallback = callback
                tm.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                registerLegacyPhoneStateListener(tm, onStateChanged)
            }
        } catch (_: SecurityException) {
            stopObservingCallState()
            onStateChanged(SystemTelephonyState.UNAVAILABLE)
        } catch (_: Exception) {
            stopObservingCallState()
            onStateChanged(SystemTelephonyState.UNAVAILABLE)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneStateListener(
        tm: TelephonyManager,
        onStateChanged: (SystemTelephonyState) -> Unit
    ) {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                onStateChanged(mapTelephonyState(state))
            }
        }
        legacyListener = listener
        tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    fun stopObservingCallState() {
        val tm = telephonyManager
        try {
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modernCallback?.let { tm.unregisterTelephonyCallback(it) }
            } else if (tm != null) {
                unregisterLegacyListener(tm)
            }
        } catch (_: Exception) {
            // Ignore cleanup errors on shutdown/revoked permission
        } finally {
            modernCallback = null
            legacyListener = null
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterLegacyListener(tm: TelephonyManager) {
        legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        legacyListener = null
    }

    private fun mapTelephonyState(state: Int): SystemTelephonyState = when (state) {
        TelephonyManager.CALL_STATE_OFFHOOK -> SystemTelephonyState.OFFHOOK
        TelephonyManager.CALL_STATE_RINGING -> SystemTelephonyState.RINGING
        TelephonyManager.CALL_STATE_IDLE -> SystemTelephonyState.IDLE
        else -> SystemTelephonyState.IDLE
    }

    private fun sanitizeDialString(raw: String): String =
        raw.trim().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
}
