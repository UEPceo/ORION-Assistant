package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.net.InetAddress
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
    fun dnsPinResolverRejectsPrivateAndAcceptsPublicLiteralAddresses() {
        assertNull(SafeWebPageReader.resolvePublicAddress("127.0.0.1"))
        assertNull(SafeWebPageReader.resolvePublicAddress("10.0.0.5"))
        assertNotNull(SafeWebPageReader.resolvePublicAddress("8.8.8.8"))
    }

    @Test
    fun rejectsIpv6TransitionAndSpecialPurposeAddresses() {
        listOf(
            "::1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "2001:0:1::1",
            "2002:7f00:1::1",
            "64:ff9b::a00:1",
            "64:ff9b:1::a00:1",
            "3fff::1"
        ).forEach { value ->
            assertTrue("$value must not be accepted as a public destination",
                SafeWebPageReader.isNonPublicAddress(InetAddress.getByName(value)))
        }
        assertFalse(SafeWebPageReader.isNonPublicAddress(InetAddress.getByName("2001:4860:4860::8888")))
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
