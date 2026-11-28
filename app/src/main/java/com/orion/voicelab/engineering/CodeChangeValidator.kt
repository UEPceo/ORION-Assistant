package com.orion.voicelab.engineering

import java.nio.charset.StandardCharsets

enum class CodeFileOperation { CREATE, UPDATE, DELETE }

data class CodeFileChange(
    val path: String,
    val operation: CodeFileOperation,
    val content: String? = null,
    val expectedBlobSha: String? = null
)

data class CodeChangeProposal(
    val repository: String,
    val baseBranch: String,
    val workBranch: String,
    val title: String,
    val summary: String,
    val changes: List<CodeFileChange>
)

data class CodeChangeValidation(
    val errors: List<String>,
    val warnings: List<String>,
    val totalContentBytes: Long,
    val approvalRequired: Boolean = true
) {
    val valid: Boolean get() = errors.isEmpty()
    val requiresElevatedReview: Boolean get() = warnings.isNotEmpty()
}

/**
 * Preflight validation for proposed repository patches.
 *
 * This class does not write files, execute code, or grant authorization. It is the
 * deterministic safety boundary that a future workspace adapter must call before
 * presenting a diff and requesting a user approval.
 */
object CodeChangeValidator {
    const val MAX_FILES = 25
    const val MAX_FILE_BYTES = 200 * 1024
    const val MAX_TOTAL_BYTES = 1024 * 1024

    private val repositoryPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")
    private val shaPattern = Regex("[a-fA-F0-9]{40}")
    private val branchPattern = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,119}")
    private val sensitivePathPatterns = listOf(
        Regex("""(^|/)\\.env(?:\\.[^/]*)?$""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)local\\.properties$""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)\\.npmrc$|(^|/)\\.pypirc$""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)\\.aws(?:/|$)|(^|/)\\.ssh(?:/|$)""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)(?:secrets?|credentials?)\\.(?:json|ya?ml|properties|toml|txt)$""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)(?:client_secret|service-account|firebase-adminsdk)[^/]*\\.json$""", RegexOption.IGNORE_CASE),
        Regex("""(^|/)(?:id_rsa|id_ed25519|id_ecdsa|id_dsa)(?:\\.pub)?$""", RegexOption.IGNORE_CASE),
        Regex("""\\.(?:jks|keystore|p12|pfx|pem|key|p8)$""", RegexOption.IGNORE_CASE)
    )
    private val secretPatterns = listOf(
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{20,}\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        Regex("(?i)(?:api[_-]?key|client[_-]?secret|access[_-]?token)\\s*[:=]\\s*[\"'][^\"']{12,}[\"']")
    )

    fun validate(proposal: CodeChangeProposal): CodeChangeValidation {
        val errors = mutableListOf<String>()
        val warnings = linkedSetOf<String>()

        if (!repositoryPattern.matches(proposal.repository) ||
            proposal.repository.split('/').any { it == "." || it == ".." }
        ) errors += "El repositorio debe tener formato propietario/nombre válido."
        if (!isValidBranch(proposal.baseBranch)) errors += "La rama base no es válida."
        if (!isValidBranch(proposal.workBranch)) errors += "La rama de trabajo no es válida."
        if (proposal.workBranch.equals("main", true) || proposal.workBranch.equals("master", true)) {
            errors += "ORION no puede proponer escrituras directas en main/master."
        }
        if (proposal.workBranch.equals(proposal.baseBranch, true)) {
            errors += "La rama de trabajo debe ser distinta de la rama base."
        }
        if (proposal.title.isBlank() || proposal.title.length > 120) errors += "El título debe tener entre 1 y 120 caracteres."
        if (proposal.summary.isBlank() || proposal.summary.length > 2_000) errors += "El resumen debe tener entre 1 y 2.000 caracteres."
        if (proposal.changes.isEmpty()) errors += "La propuesta no contiene cambios."
        if (proposal.changes.size > MAX_FILES) errors += "La propuesta supera el límite de $MAX_FILES archivos."

        val duplicatePaths = proposal.changes.groupBy { it.path }.filterValues { it.size > 1 }.keys
        if (duplicatePaths.isNotEmpty()) errors += "La propuesta contiene rutas duplicadas."

        var totalBytes = 0L
        proposal.changes.forEach { change ->
            if (!isSafePath(change.path)) {
                errors += "Ruta no permitida: ${change.path.take(120)}."
                return@forEach
            }
            if (change.path.startsWith(".github/workflows/") ||
                change.path.endsWith("build.gradle") ||
                change.path.endsWith("build.gradle.kts") ||
                change.path.endsWith("settings.gradle") ||
                change.path.endsWith("settings.gradle.kts") ||
                change.path.endsWith("AndroidManifest.xml")
            ) warnings += "El cambio afecta a workflows, compilación o permisos Android y necesita revisión técnica reforzada."

            when (change.operation) {
                CodeFileOperation.CREATE -> {
                    if (change.content == null) errors += "CREATE necesita contenido: ${change.path}."
                    if (change.expectedBlobSha != null) errors += "CREATE no debe incluir SHA previo: ${change.path}."
                }
                CodeFileOperation.UPDATE -> {
                    if (change.content == null) errors += "UPDATE necesita contenido: ${change.path}."
                    if (change.expectedBlobSha == null || !shaPattern.matches(change.expectedBlobSha)) {
                        errors += "UPDATE necesita el SHA del archivo base: ${change.path}."
                    }
                }
                CodeFileOperation.DELETE -> {
                    if (change.content != null) errors += "DELETE no debe incluir contenido: ${change.path}."
                    if (change.expectedBlobSha == null || !shaPattern.matches(change.expectedBlobSha)) {
                        errors += "DELETE necesita el SHA del archivo base: ${change.path}."
                    }
                }
            }

            val content = change.content ?: return@forEach
            val bytes = content.toByteArray(StandardCharsets.UTF_8).size.toLong()
            totalBytes += bytes
            if (bytes > MAX_FILE_BYTES) errors += "El archivo supera el límite de $MAX_FILE_BYTES bytes: ${change.path}."
            if (secretPatterns.any { it.containsMatchIn(content) }) {
                errors += "Se ha detectado un posible secreto o clave privada en ${change.path}; retíralo antes de continuar."
            }
        }

        if (totalBytes > MAX_TOTAL_BYTES) errors += "El conjunto de cambios supera el límite de $MAX_TOTAL_BYTES bytes."
        return CodeChangeValidation(errors.distinct(), warnings.toList(), totalBytes)
    }

    fun isSafePath(path: String): Boolean {
        if (path.isBlank() || path.length > 500 || path.startsWith('/') ||
            path.contains('\\') || path.contains('\u0000') || path.contains(':')
        ) return false
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." || it.equals(".git", true) }) return false
        return true
    }

    fun isValidBranch(branch: String): Boolean {
        if (!branchPattern.matches(branch) || branch.startsWith('/') || branch.endsWith('/') ||
            branch.contains("//") || branch.contains("..") || branch.contains("@{") ||
            branch.endsWith(".") || branch.endsWith(".lock")
        ) return false
        return branch.split('/').all { segment ->
            segment.isNotBlank() && !segment.startsWith('.') && !segment.endsWith('.')
        }
    }
}
