package com.orion.voicelab.brain

/**
 * Prevents credentials from entering durable plan definitions or execution summaries.
 * This is a conservative recognizer, not a general-purpose secret scanner.
 */
object PlanSecretGuard {
    private val secretPatterns = listOf(
        Regex("(?i)\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
        Regex("(?i)\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
        Regex("\\bAIza[0-9A-Za-z_-]{30,}\\b"),
        Regex("(?i)\\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\\b"),
        Regex("(?i)\\bsk-[A-Za-z0-9_-]{20,}\\b"),
        Regex("(?i)\\b(?:xox[baprs]-)[A-Za-z0-9-]{10,}\\b"),
        Regex("(?i)\\b(?:hf|npm|pypi)-[A-Za-z0-9_-]{16,}\\b"),
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}"),
        Regex("(?i)\\bBasic\\s+[A-Za-z0-9+/=]{12,}"),
        Regex("-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
        Regex("(?i)\\b(?:authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|client[_-]?secret|secret|password|passwd|credential|jwt|session[_-]?key)\\b\\s*[:=]\\s*[\\\"\\\']?[^\\s,;\\\"\\\']{6,}"),
        Regex("(?i)([?&](?:access_token|token|api_key|key|password|secret|sig|signature|x-amz-signature|x-amz-credential|se)=)[^&\\s]+")
    )
    private val sensitiveKeyMarkers = listOf(
        "password", "passwd", "secret", "token", "authorization",
        "api_key", "apikey", "credential", "private_key"
    )

    fun containsSecret(value: String): Boolean =
        secretPatterns.any { it.containsMatchIn(value.take(24_000)) }

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase().replace('-', '_')
        return sensitiveKeyMarkers.any(normalized::contains)
    }

    fun redact(value: String, maxChars: Int = 6_000): String {
        var safe = value
        secretPatterns.forEach { pattern -> safe = pattern.replace(safe, "[REDACTED]") }
        return safe.take(maxChars.coerceIn(0, 1_500_000))
    }
}
