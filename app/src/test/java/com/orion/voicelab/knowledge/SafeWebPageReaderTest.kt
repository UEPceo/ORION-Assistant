package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeWebPageReaderTest {
    @Test
    fun rejectsNonHttpsAndLocalDestinations() {
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("http://example.org"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://localhost/path"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://127.0.0.1/path"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://10.0.0.8/path"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://192.168.1.1/path"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://100.64.0.1/path"))
        assertNull(SafeWebPageReader.normalizePublicHttpsUrl("https://user:pass@example.org"))
    }

    @Test
    fun knownPublisherClassificationIsConservative() {
        assertEquals(EvidenceAuthority.OFFICIAL_PRIMARY, WebAuthorityClassifier.classify("www.boe.es"))
        assertEquals(EvidenceAuthority.OFFICIAL_PRIMARY, WebAuthorityClassifier.classify("www.aemet.es"))
        assertEquals(EvidenceAuthority.OFFICIAL_PRIMARY, WebAuthorityClassifier.classify("www.juntadeandalucia.es"))
        assertEquals(EvidenceAuthority.PRIMARY_TECHNICAL, WebAuthorityClassifier.classify("developer.android.com"))
        assertEquals(EvidenceAuthority.UNKNOWN, WebAuthorityClassifier.classify("random-blog.example"))
    }
}
