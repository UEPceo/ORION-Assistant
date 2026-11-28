package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainPromptComposerBudgetTest {
    @Test fun zeroEvidenceBudgetProducesAnEmptyUnverifiedContextWithoutThrowing() {
        val evidence = KnowledgeEvidence(
            id = "source-1",
            title = "Technical source",
            canonicalUrl = "https://example.org/source",
            publisherId = "publisher-a",
            domain = KnowledgeDomain.PROGRAMMING,
            authority = EvidenceAuthority.PRIMARY_TECHNICAL,
            retrievedAtMs = 10L,
            content = "A technical fact."
        )
        val context = BrainContext(
            query = BrainQuery("Question", "question", KnowledgeDomain.PROGRAMMING, false, false),
            documents = listOf(KnowledgeDocument(evidence)),
            verification = KnowledgeVerification(
                verdict = KnowledgeVerdict.SUPPORTED,
                evidenceCount = 1,
                independentPublisherCount = 1,
                hasPrimaryEvidence = true,
                hasOfficialPrimaryEvidence = false,
                newestEvidenceAtMs = 10L,
                reason = "Source available."
            ),
            presentation = BrainPresentation.EVIDENCE_SUPPORTED
        )
        val bundle = BrainPromptComposer.composeBundle(context, maxChars = 0)
        assertTrue(bundle.prompt.isEmpty())
        assertTrue(bundle.evidenceContext.documents.isEmpty())
        assertEquals(KnowledgeVerdict.NEEDS_CORROBORATION, bundle.evidenceContext.verification.verdict)
        assertEquals(BrainPresentation.UNVERIFIED, bundle.evidenceContext.presentation)
    }
}
