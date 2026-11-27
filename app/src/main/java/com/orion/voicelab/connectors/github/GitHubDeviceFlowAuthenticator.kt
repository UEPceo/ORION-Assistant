package com.orion.voicelab.connectors.github

import com.orion.voicelab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubDeviceAuthorization(
    val userCode: String,
    val verificationUri: String,
    internal val deviceCode: String,
    val expiresInSeconds: Long,
    val pollingIntervalSeconds: Long
)

sealed interface GitHubDeviceAuthorizationResult {
    data class Authorized(val accessToken: String, val tokenType: String, val scope: String) :
        GitHubDeviceAuthorizationResult
    data object AccessDenied : GitHubDeviceAuthorizationResult
    data object Expired : GitHubDeviceAuthorizationResult
    data class Failed(val message: String, val retryable: Boolean) :
        GitHubDeviceAuthorizationResult
}

/**
 * GitHub OAuth App Device Flow for installed Android apps.
 *
 * The OAuth App must have Device Flow enabled in GitHub settings. The client ID is public;
 * no client secret is embedded in the APK. Device codes remain in memory and are never logged.
 */
class GitHubDeviceFlowAuthenticator(
    private val clientId: String = BuildConfig.ORION_GITHUB_CLIENT_ID
) {
    fun isConfigured(): Boolean = clientId.isNotBlank()

    suspend fun requestAuthorization(): Result<GitHubDeviceAuthorization> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Falta configurar ORION_GITHUB_CLIENT_ID.")
            )
        }

        runCatching {
            val response = postForm(
                DEVICE_CODE_URL,
                mapOf(
                    "client_id" to clientId,
                    "scope" to REQUESTED_SCOPES
                )
            )
            val json = JSONObject(response)
            val deviceCode = json.getString("device_code")
            val userCode = json.getString("user_code")
            val verificationUri = json.optString("verification_uri", VERIFICATION_URI)
            require(verificationUri == VERIFICATION_URI) { "Unexpected GitHub verification URI" }

            GitHubDeviceAuthorization(
                userCode = userCode,
                verificationUri = verificationUri,
                deviceCode = deviceCode,
                expiresInSeconds = json.getLong("expires_in"),
                pollingIntervalSeconds = json.optLong("interval", DEFAULT_POLL_INTERVAL)
                    .coerceAtLeast(DEFAULT_POLL_INTERVAL)
            )
        }
    }

    suspend fun awaitAuthorization(
        authorization: GitHubDeviceAuthorization,
        onWaiting: (Long) -> Unit = {}
    ): GitHubDeviceAuthorizationResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1_000L
        var intervalSeconds = authorization.pollingIntervalSeconds

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1_000L)
            onWaiting(((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1_000L))

            val payload = try {
                postForm(
                    ACCESS_TOKEN_URL,
                    mapOf(
                        "client_id" to clientId,
                        "device_code" to authorization.deviceCode,
                        "grant_type" to DEVICE_GRANT_TYPE
                    )
                )
            } catch (_: Exception) {
                continue
            }

            val json = JSONObject(payload)
            when (val error = json.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds += SLOW_DOWN_INCREMENT_SECONDS
                "access_denied" -> return@withContext GitHubDeviceAuthorizationResult.AccessDenied
                "expired_token" -> return@withContext GitHubDeviceAuthorizationResult.Expired
                "" -> {
                    val token = json.optString("access_token")
                    if (token.isBlank()) {
                        return@withContext GitHubDeviceAuthorizationResult.Failed(
                            "GitHub devolvió una respuesta de autorización incompleta.",
                            retryable = false
                        )
                    }
                    return@withContext GitHubDeviceAuthorizationResult.Authorized(
                        accessToken = token,
                        tokenType = json.optString("token_type", "bearer"),
                        scope = json.optString("scope")
                    )
                }
                else -> return@withContext GitHubDeviceAuthorizationResult.Failed(
                    safeErrorMessage(error),
                    retryable = error == "temporarily_unavailable" || error == "server_error"
                )
            }
        }

        GitHubDeviceAuthorizationResult.Expired
    }

    private fun postForm(endpoint: String, values: Map<String, String>): String {
        val body = values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "ORION-Android")
        }

        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("GitHub OAuth HTTP $status")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun safeErrorMessage(error: String): String = when (error) {
        "incorrect_client_credentials" -> "La configuración OAuth de GitHub no es válida."
        "unsupported_grant_type" -> "GitHub rechazó el método de autorización solicitado."
        "incorrect_device_code" -> "El código temporal de GitHub ya no es válido."
        "verification_uri_mismatch" -> "La dirección de autorización de GitHub no coincide."
        "temporarily_unavailable", "server_error" -> "GitHub no está disponible temporalmente."
        else -> "No se pudo completar la autorización de GitHub."
    }

    private companion object {
        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val VERIFICATION_URI = "https://github.com/login/device"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val REQUESTED_SCOPES = "read:user"
        const val DEFAULT_POLL_INTERVAL = 5L
        const val SLOW_DOWN_INCREMENT_SECONDS = 5L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
