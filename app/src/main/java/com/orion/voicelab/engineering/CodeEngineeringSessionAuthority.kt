package com.orion.voicelab.engineering

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

data class CodeEngineeringGrant(val id:String,val repository:String,val taskDigest:String,val workBranch:String,val expiresAtMs:Long)
data class CodePatchApproval internal constructor(val sessionId:String,val repository:String,val workBranch:String,val expectedHeadSha:String,val proposalDigest:String,val expiresAtMs:Long,internal val nonce:String)

/** Process-scoped authorization; a process restart revokes all sessions and approvals. */
class CodeEngineeringSessionAuthority(private val nowMs:()->Long=System::currentTimeMillis,private val sessionTtlMs:Long=30*60_000L,private val approvalTtlMs:Long=5*60_000L) {
    private val random=SecureRandom()
    private val grants=ConcurrentHashMap<String,CodeEngineeringGrant>()
    private val approvals=ConcurrentHashMap<String,CodePatchApproval>()

    fun grant(repository:String,task:String,workBranch:String,userConfirmed:Boolean):CodeEngineeringGrant {
        require(userConfirmed) { "La autorización debe confirmarse expresamente." }
        require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))) { "Repositorio no válido." }
        require(task.isNotBlank()&&task.length<=4_000) { "La tarea debe estar definida y tener como máximo 4.000 caracteres." }
        require(CodeChangeValidator.isValidBranch(workBranch)&&!workBranch.equals("main",true)&&!workBranch.equals("master",true)) { "Se necesita una rama distinta de main/master." }
        val grant=CodeEngineeringGrant(randomToken(),repository,sha256(task.trim()),workBranch,nowMs()+sessionTtlMs)
        grants[grant.id]=grant
        return grant
    }
    fun isTaskAuthorized(sessionId:String,task:String):Boolean=runCatching { activeGrant(sessionId).taskDigest==sha256(task.trim()) }.getOrDefault(false)
    fun isActive(sessionId:String):Boolean=runCatching { activeGrant(sessionId); true }.getOrDefault(false)
    fun approvePatch(sessionId:String,proposal:CodeChangeProposal,expectedHeadSha:String,reviewedDiffDigest:String,userConfirmed:Boolean):Result<CodePatchApproval> = runCatching {
        require(userConfirmed) { "El usuario debe aprobar expresamente el diff." }
        val grant=activeGrant(sessionId)
        val validation=CodeChangeValidator.validate(proposal)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(proposal.repository==grant.repository&&proposal.workBranch==grant.workBranch) { "Propuesta fuera del alcance autorizado." }
        require(expectedHeadSha.matches(Regex("[a-fA-F0-9]{40}"))) { "SHA de rama no válido." }
        val digest=proposalDigest(proposal)
        require(digest.equals(reviewedDiffDigest,true)) { "El diff cambió después de la revisión." }
        CodePatchApproval(sessionId,proposal.repository,proposal.workBranch,expectedHeadSha.lowercase(),digest,minOf(grant.expiresAtMs,nowMs()+approvalTtlMs),randomToken()).also { approvals[it.nonce]=it }
    }
    fun consumeApproval(approval:CodePatchApproval,proposal:CodeChangeProposal,currentHeadSha:String):Boolean {
        val stored=approvals.remove(approval.nonce)?:return false
        val grant=grants[approval.sessionId]?:return false
        if(stored!=approval||grant.expiresAtMs<=nowMs()||approval.expiresAtMs<=nowMs()) return false
        if(approval.repository!=proposal.repository||approval.workBranch!=proposal.workBranch||!approval.expectedHeadSha.equals(currentHeadSha,true)) return false
        return approval.proposalDigest==proposalDigest(proposal)
    }
    fun revoke(sessionId:String):Boolean { grants.remove(sessionId)?:return false; approvals.entries.removeIf { it.value.sessionId==sessionId }; return true }
    fun revokeAll() { grants.clear(); approvals.clear() }
    private fun activeGrant(id:String):CodeEngineeringGrant {
        val grant=grants[id]?:error("La sesión no existe o ha sido revocada.")
        if(grant.expiresAtMs<=nowMs()) { revoke(id); error("La sesión ha caducado.") }
        return grant
    }
    private fun randomToken()=ByteArray(24).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    companion object {
        fun proposalDigest(proposal:CodeChangeProposal):String {
            val canonical=buildString {
                append(proposal.repository).append('\n'); append(proposal.baseBranch).append('\n'); append(proposal.workBranch).append('\n'); append(proposal.title).append('\n'); append(proposal.summary).append('\n')
                proposal.changes.sortedBy { it.path }.forEach { c -> append(c.operation.name).append('|').append(c.path).append('|').append(c.expectedBlobSha.orEmpty()).append('|').append(c.content.orEmpty().length).append('|').append(c.content.orEmpty()).append('\n') }
            }
            return sha256(canonical)
        }
        private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
