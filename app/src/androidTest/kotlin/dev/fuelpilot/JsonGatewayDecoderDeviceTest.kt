package dev.fuelpilot

import dev.fuelpilot.data.poi.gateway.JsonGatewayDecoder
import org.junit.Assert.*
import org.junit.Test

/** Exercises platform org.json, which is a stub in ordinary Android JVM tests. */
class JsonGatewayDecoderDeviceTest {
    private val available = """{
        "schemaVersion":1,"status":"available","fetchedAtEpochSeconds":100,"expiresAtEpochSeconds":200,
        "provenance":{"provider":"OPINET","endpoint":"aroundAll","coordinateSystem":"KATEC","roadMetricsVerified":false,"cacheHit":true},
        "stations":[]
    }"""

    @Test fun platformJsonDecodesAvailableAndUnavailableDocuments() {
        val doc = JsonGatewayDecoder.decode(available)
        assertEquals("available", doc.status)
        assertEquals(100L, doc.fetchedAtEpochSeconds)
        assertFalse(doc.provenance!!.roadMetricsVerified)
        assertTrue(doc.provenance!!.cacheHit)
        assertTrue(doc.stations.isEmpty())
        val unavailable = JsonGatewayDecoder.decode("""{"schemaVersion":1,"status":"unavailable","reason":"PROVIDER_UNCONFIGURED"}""")
        assertEquals("PROVIDER_UNCONFIGURED", unavailable.reason)
    }

    @Test fun platformJsonRejectsStringBooleanAndTrailingPayload() {
        for (json in listOf(available.replace("\"roadMetricsVerified\":false", "\"roadMetricsVerified\":\"false\""), available + "{}")) {
            assertThrows(Exception::class.java) { JsonGatewayDecoder.decode(json) }
        }
    }
}
