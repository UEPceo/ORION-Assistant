package com.orion.voicelab.brain

/**
 * Prevents credentials from entering durable plan definitions or execution summaries.
 * This is a conservative recognizer, not a general-purpose secret scanner.
 */
object PlanSecretGuard {
    private val secretPatterns = listOf(
        Regex("(?i)\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("\\bAIza[0-9A-Za-z_-]{30,}\\b"),
        Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        Regex("(?i)\\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd)\\s*[:=]\\s*[\"']?[^\\s\"']{8,}")
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

    fun redact(value: String): String {
        var safe = value.take(6_000)
        secretPatterns.forEach { pattern -> safe = pattern.replace(safe, "[REDACTED]") }
        return safe
    }
}
