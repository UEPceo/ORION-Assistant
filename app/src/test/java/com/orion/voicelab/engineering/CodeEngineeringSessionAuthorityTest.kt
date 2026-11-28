package com.orion.voicelab.engineering

import org.junit.Assert.*
import org.junit.Test

class CodeEngineeringSessionAuthorityTest {
    private val head="a".repeat(40)
    private fun proposal()=CodeChangeProposal("owner/repo","main","orion/task","Add","Create",listOf(CodeFileChange("src/A.kt",CodeFileOperation.CREATE,"class A")))
    @Test fun exactApprovalIsSingleUse() {
        val a=CodeEngineeringSessionAuthority(); val g=a.grant("owner/repo","task","orion/task",true); val p=proposal()
        val approval=a.approvePatch(g.id,p,head,CodeEngineeringSessionAuthority.proposalDigest(p),true).getOrThrow()
        assertTrue(a.consumeApproval(approval,p,head)); assertFalse(a.consumeApproval(approval,p,head))
    }
    @Test fun modifiedPatchCannotReuseApproval() {
        val a=CodeEngineeringSessionAuthority(); val g=a.grant("owner/repo","task","orion/task",true); val p=proposal()
        val approval=a.approvePatch(g.id,p,head,CodeEngineeringSessionAuthority.proposalDigest(p),true).getOrThrow()
        val changed=p.copy(changes=listOf(CodeFileChange("src/A.kt",CodeFileOperation.CREATE,"class B")))
        assertFalse(a.consumeApproval(approval,changed,head))
    }
    @Test fun branchScopeAndConfirmationAreMandatory() {
        val a=CodeEngineeringSessionAuthority()
        assertThrows(IllegalArgumentException::class.java) { a.grant("owner/repo","task","main",true) }
        assertThrows(IllegalArgumentException::class.java) { a.grant("owner/repo","task","orion/task",false) }
    }
    @Test fun taskAndRevocationAreEnforced() {
        val a=CodeEngineeringSessionAuthority(); val g=a.grant("owner/repo","task A","orion/task",true)
        assertTrue(a.isTaskAuthorized(g.id,"task A")); assertFalse(a.isTaskAuthorized(g.id,"task B"))
        assertTrue(a.revoke(g.id)); assertFalse(a.isTaskAuthorized(g.id,"task A"))
    }
}
