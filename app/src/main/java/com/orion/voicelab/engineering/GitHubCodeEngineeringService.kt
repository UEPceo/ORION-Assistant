package com.orion.voicelab.engineering

import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubBranchSummary
import com.orion.voicelab.connectors.github.GitHubCommitResult
import com.orion.voicelab.connectors.github.GitHubPullRequestSummary
import com.orion.voicelab.connectors.github.GitHubWorkflowRunSummary
import kotlinx.coroutines.CancellationException

data class CodeEngineeringSession(
    val grant: CodeEngineeringGrant,
    val baseBranch: String,
    val branch: GitHubBranchSummary
)

data class CodeEngineeringCiInspection(
    val run: GitHubWorkflowRunSummary?,
    val jobs: List<com.orion.voicelab.connectors.github.GitHubWorkflowJobSummary>,
    val failureLogs: String?,
    val logRetrievalError: String? = null
) {
    val completedSuccessfully: Boolean
        get() = run?.status == "completed" && run.conclusion == "success"
    val failed: Boolean
        get() = run?.status == "completed" && run.conclusion == "failure"
}

data class PreparedCodeChange(
    val task: String,
    val snapshot: CodeWorkspaceSnapshot,
    val proposal: CodeChangeProposal,
    val diffDigest: String,
    val validation: CodeChangeValidation
)

/** Coordinates a user-scoped task to a reviewed GitHub commit. Model output is untrusted. */
class GitHubCodeEngineeringService(
    private val api: GitHubApiClient,
    private val workspace: GitHubCodeWorkspace,
    private val authority: CodeEngineeringSessionAuthority,
    private val generator: CodePatchGenerator
) {
    suspend fun startSession(
        repository: String,
        baseBranch: String,
        workBranch: String,
        task: String,
        userConfirmed: Boolean
    ): Result<CodeEngineeringSession> = runCatching {
        require(userConfirmed) { "Confirma la sesión de ingeniería antes de crear la rama." }
        val grant = authority.grant(repository, task, workBranch, userConfirmed = true)
        val branch = try {
            api.createBranch(repository, workBranch, baseBranch).getOrThrow()
        } catch (error: Throwable) {
            authority.revoke(grant.id)
            throw error
        }
        CodeEngineeringSession(grant, baseBranch, branch)
    }

    suspend fun prepare(
        session: CodeEngineeringSession,
        task: String,
        selectedPaths: List<String>
    ): Result<PreparedCodeChange> = runCatching {
        require(authority.isTaskAuthorized(session.grant.id, task)) {
            "La tarea no coincide con la autorización de esta sesión."
        }
        val snapshot = workspace.open(session.grant.repository, session.grant.workBranch, selectedPaths).getOrThrow()
        val proposal = generator.generate(task, snapshot, session.baseBranch).getOrThrow()
        val validation = CodeChangeValidator.validate(proposal)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(proposal.repository == session.grant.repository &&
            proposal.workBranch == session.grant.workBranch) {
            "El modelo ha propuesto cambios fuera de la sesión autorizada."
        }
        require(proposal.baseBranch == session.baseBranch) { "La propuesta usa una rama base distinta de la autorizada." }
        val selected = snapshot.files.associateBy { it.path }
        proposal.changes.filter { it.operation != CodeFileOperation.CREATE }.forEach { change ->
            val original = selected[change.path] ?: error("El archivo ${change.path} no forma parte del workspace inspeccionado.")
            require(original.sha.equals(change.expectedBlobSha, true)) {
                "El SHA de ${change.path} no coincide con el archivo inspeccionado."
            }
        }
        PreparedCodeChange(task, snapshot, proposal, CodeEngineeringSessionAuthority.proposalDigest(proposal), validation)
    }

    suspend fun prepareRepair(
        session: CodeEngineeringSession,
        inspection: CodeEngineeringCiInspection,
        attempt: Int,
        selectedPaths: List<String>,
        userConfirmedSourceContext: Boolean
    ): Result<PreparedCodeChange> = runCatching {
        require(userConfirmedSourceContext) { "Confirma el uso de estos archivos como contexto para la reparación." }
        require(CodeRepairBudget.canPrepareRepair(attempt - 1, inspection)) {
            "La reparación requiere una ejecución fallida del SHA actual, logs disponibles y presupuesto de intentos."
        }
        require(attempt in 1..CodeRepairBudget.MAX_ATTEMPTS) { "Se ha agotado el presupuesto de reparación." }
        require(authority.isTaskAuthorized(session.grant.id, session.grant.task)) {
            "La sesión de ingeniería ya no autoriza esta tarea."
        }
        val failedRun = inspection.run ?: error("No existe una ejecución CI asociada.")
        require(failedRun.branch == session.grant.workBranch) {
            "La ejecución fallida pertenece a otra rama."
        }
        val snapshot = workspace.open(session.grant.repository, session.grant.workBranch, selectedPaths).getOrThrow()
        require(snapshot.headSha.equals(failedRun.headSha, true)) {
            "La rama ha cambiado desde la ejecución fallida. Consulta CI de nuevo antes de reparar."
        }
        val diagnostics = CodeRepairBudget.summarizeLogs(inspection.failureLogs.orEmpty())
        require(diagnostics.isNotBlank()) { "Los logs no contienen diagnósticos utilizables." }
        val proposal = generator.generateRepair(
            task = session.grant.task,
            snapshot = snapshot,
            baseBranch = session.baseBranch,
            diagnosticContext = diagnostics,
            attempt = attempt
        ).getOrThrow()
        val validation = CodeChangeValidator.validate(proposal)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(proposal.repository == session.grant.repository &&
            proposal.workBranch == session.grant.workBranch) {
            "El modelo ha propuesto cambios fuera de la sesión autorizada."
        }
        require(proposal.baseBranch == session.baseBranch) {
            "La propuesta de reparación usa una rama base distinta de la autorizada."
        }
        val selected = snapshot.files.associateBy { it.path }
        proposal.changes.filter { it.operation != CodeFileOperation.CREATE }.forEach { change ->
            val original = selected[change.path] ?: error("El archivo ${change.path} no forma parte del workspace inspeccionado.")
            require(original.sha.equals(change.expectedBlobSha, true)) {
                "El SHA de ${change.path} no coincide con el archivo inspeccionado."
            }
        }
        PreparedCodeChange(
            session.grant.task,
            snapshot,
            proposal,
            CodeEngineeringSessionAuthority.proposalDigest(proposal),
            validation
        )
    }

    suspend fun commitApproved(
        session: CodeEngineeringSession,
        prepared: PreparedCodeChange,
        userConfirmedExactDiff: Boolean,
        reviewedDiffDigest: String
    ): Result<GitHubCommitResult> = runCatching {
        require(authority.isTaskAuthorized(session.grant.id, prepared.task)) {
            "La tarea preparada no pertenece a esta sesión."
        }
        require(prepared.snapshot.repository == session.grant.repository &&
            prepared.snapshot.branch == session.grant.workBranch) { "El workspace no pertenece a esta sesión." }
        val approval = authority.approvePatch(
            sessionId = session.grant.id,
            proposal = prepared.proposal,
            expectedHeadSha = prepared.snapshot.headSha,
            reviewedDiffDigest = reviewedDiffDigest,
            userConfirmed = userConfirmedExactDiff
        ).getOrThrow()
        require(authority.consumeApproval(approval, prepared.proposal, prepared.snapshot.headSha)) {
            "La aprobación del diff ha caducado, se ha consumido o no coincide."
        }
        api.commitChanges(prepared.proposal, prepared.snapshot.headSha).getOrThrow()
    }

    /**
     * Correlates CI strictly to the exact reviewed branch head. Stale runs from previous
     * commits are never treated as validation of the current proposal.
     */
    suspend fun inspectCiForCurrentHead(
        repository: String,
        branch: String,
        expectedHeadSha: String
    ): Result<CodeEngineeringCiInspection> {
        return try {
            require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))) {
                "Repositorio no válido."
            }
            require(branch.isNotBlank() && branch.length <= 120) { "Rama no válida." }
            require(expectedHeadSha.matches(Regex("[a-fA-F0-9]{40}"))) { "SHA esperado no válido." }
            val run = api.listWorkflowRuns(repository).getOrThrow()
                .filter { it.branch == branch && it.headSha.equals(expectedHeadSha, true) }
                .maxByOrNull { it.runId }
            if (run == null) return Result.success(CodeEngineeringCiInspection(null, emptyList(), null))
            val jobs = if (run.runId > 0L) api.listWorkflowRunJobs(repository, run.runId).getOrThrow() else emptyList()
            val logs = if (run.status == "completed" && run.conclusion == "failure") {
                api.downloadWorkflowRunLogs(repository, run.runId).getOrNull()
            } else null
            val logError = if (run.status == "completed" && run.conclusion == "failure" && logs == null) {
                "La ejecución ha fallado, pero ORION no ha podido recuperar sus logs."
            } else null
            Result.success(CodeEngineeringCiInspection(run, jobs, logs, logError))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun createDraftPullRequest(
        session: CodeEngineeringSession,
        title: String,
        body: String,
        userConfirmed: Boolean
    ): Result<GitHubPullRequestSummary> = runCatching {
        require(userConfirmed) { "La creación del pull request necesita confirmación expresa." }
        require(authority.isActive(session.grant.id)) { "La sesión de ingeniería ha caducado o se ha revocado." }
        val head = api.getBranchHead(session.grant.repository, session.grant.workBranch).getOrThrow()
        require(!head.sha.equals(session.branch.sha, true)) { "La rama no contiene commits nuevos que revisar." }
        api.createPullRequest(
            repository = session.grant.repository,
            title = title.take(256).ifBlank { "Cambios ORION: ${session.grant.workBranch}" },
            body = body.take(20_000),
            head = session.grant.workBranch,
            base = session.baseBranch,
            draft = true
        ).getOrThrow()
    }

    fun revoke(session: CodeEngineeringSession) = authority.revoke(session.grant.id)
}
