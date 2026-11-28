package com.orion.voicelab.engineering

import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubBranchSummary
import com.orion.voicelab.connectors.github.GitHubCommitResult

data class CodeEngineeringSession(
    val grant: CodeEngineeringGrant,
    val branch: GitHubBranchSummary
)

data class PreparedCodeChange(
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
        val branch = api.createBranch(repository, workBranch, baseBranch).getOrThrow()
        val grant = authority.grant(repository, task, workBranch, userConfirmed = true)
        CodeEngineeringSession(grant, branch)
    }

    suspend fun prepare(
        session: CodeEngineeringSession,
        task: String,
        selectedPaths: List<String>
    ): Result<PreparedCodeChange> = runCatching {
        val snapshot = workspace.open(session.grant.repository, session.grant.workBranch, selectedPaths).getOrThrow()
        val proposal = generator.generate(task, snapshot).getOrThrow()
        val validation = CodeChangeValidator.validate(proposal)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(proposal.repository == session.grant.repository &&
            proposal.workBranch == session.grant.workBranch) {
            "El modelo ha propuesto cambios fuera de la sesión autorizada."
        }
        require(proposal.baseBranch.isNotBlank()) { "La propuesta no identifica la rama base." }
        val selected = snapshot.files.associateBy { it.path }
        proposal.changes.filter { it.operation != CodeFileOperation.CREATE }.forEach { change ->
            val original = selected[change.path] ?: error("El archivo ${change.path} no forma parte del workspace inspeccionado.")
            require(original.sha.equals(change.expectedBlobSha, true)) {
                "El SHA de ${change.path} no coincide con el archivo inspeccionado."
            }
        }
        PreparedCodeChange(snapshot, proposal, CodeEngineeringSessionAuthority.proposalDigest(proposal), validation)
    }

    suspend fun commitApproved(
        session: CodeEngineeringSession,
        prepared: PreparedCodeChange,
        userConfirmedExactDiff: Boolean,
        reviewedDiffDigest: String
    ): Result<GitHubCommitResult> = runCatching {
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

    fun revoke(session: CodeEngineeringSession) = authority.revoke(session.grant.id)
}
