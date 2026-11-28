package com.orion.voicelab.connectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsConnectorTest {
    @Test
    fun transitUsesDocumentedMapsDirectionsUrl() {
        val uri = buildGoogleMapsUri("navigate", "Cádiz", "transit")!!
        assertTrue(uri.toString().startsWith("https://www.google.com/maps/dir/?api=1"))
        assertTrue(uri.toString().contains("destination=C%C3%A1diz"))
        assertTrue(uri.toString().contains("travelmode=transit"))
        assertFalse(uri.toString().contains("mode=r"))
    }

    @Test
    fun walkingAndDrivingUseDocumentedTravelModes() {
        assertTrue(buildGoogleMapsUri("navigate", "Tarifa", "walking")!!.toString().contains("travelmode=walking"))
        assertTrue(buildGoogleMapsUri("navigate", "Tarifa", "driving")!!.toString().contains("travelmode=driving"))
    }

    @Test
    fun blankDestinationAndUnknownOperationAreRejected() {
        assertNull(buildGoogleMapsUri("navigate", "", "driving"))
        assertNull(buildGoogleMapsUri("delete", "Tarifa", "driving"))
    }
}
