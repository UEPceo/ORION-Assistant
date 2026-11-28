package com.orion.voicelab.connectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PescaIAConnectorSecurityTest {
    @Test
    fun onlyHttpsPescaIaHostsAreAllowedForRedirects() {
        assertTrue(isAllowedPescaIaUrl("https://pescaisurfcasting.company/playas.html"))
        assertTrue(isAllowedPescaIaUrl("https://www.pescaisurfcasting.company/metodologia.html"))
        assertFalse(isAllowedPescaIaUrl("http://pescaisurfcasting.company/"))
        assertFalse(isAllowedPescaIaUrl("https://pescaisurfcasting.company.attacker.test/"))
        assertFalse(isAllowedPescaIaUrl("https://attacker.test/"))
        assertFalse(isAllowedPescaIaUrl("https://pescaisurfcasting.company:8443/"))
    }
}
