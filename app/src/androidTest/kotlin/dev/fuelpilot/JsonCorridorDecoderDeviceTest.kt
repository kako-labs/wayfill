package dev.fuelpilot

import dev.fuelpilot.data.poi.gateway.JsonCorridorDecoder
import dev.fuelpilot.data.poi.gateway.StationFuel
import org.junit.Assert.*
import org.junit.Test

/** Exercises platform org.json, which is a stub in ordinary Android JVM tests. */
class JsonCorridorDecoderDeviceTest {
    // Shape copied from a live gateway response (2026-11-13), trimmed to one station.
    private val available = """{"schemaVersion": 1, "status": "available", "corridorToken": "AbCdEfGhIjKlMnOpQrSt_-", "fetchedAtEpochSeconds": 1794575498,
        "expiresAtEpochSeconds": 1794575528, "stationsObservedAtEpochSeconds": 1794575498,
        "route": {"remainingDistanceMeters": 16839.0, "remainingDurationSeconds": 1500},
        "coverage": {"verified": true, "sampleSpacingMeters": 6000, "searchRadiusMeters": 5000},
        "provenance": {"stationProvider": "OPINET", "stationEndpoint": "aroundAll", "routingProvider": "KAKAO_MOBILITY", "coordinateSystem": "WGS84"},
        "stations": [{"id": "A0000001", "name": "Station", "fuel": "B027", "priceKrwPerLiter": 1884.0,
            "latitude": 37.3876931, "longitude": 127.1135118, "alongRouteMeters": 382.6, "roadDistanceMeters": 1123.1,
            "etaSeconds": 188, "detourSeconds": 234}]}"""

    @Test fun platformJsonDecodesAvailableAndUnavailableDocuments() {
        val doc = JsonCorridorDecoder.decode(available)
        val snapshot = doc.snapshot!!
        assertEquals("KAKAO_MOBILITY", doc.routingProvider)
        assertEquals("AbCdEfGhIjKlMnOpQrSt_-", doc.corridorToken)
        assertTrue(snapshot.coverageVerified)
        assertEquals(16839.0, snapshot.remainingDistanceMeters, 0.0)
        val station = snapshot.stations.single()
        assertEquals(StationFuel.GASOLINE, station.fuel)
        assertEquals(382.6, station.alongRouteMeters, 0.0)
        assertEquals(234, station.detourSeconds)
        val unavailable = JsonCorridorDecoder.decode("""{"schemaVersion":1,"status":"unavailable","reason":"ROUTE_TOO_LONG"}""")
        assertEquals("ROUTE_TOO_LONG", unavailable.reason)
    }

    @Test fun platformJsonRejectsLooseTypesAndTrailingPayload() {
        listOf(available.replace("\"verified\": true", "\"verified\": \"true\""),
            available.replace("\"etaSeconds\": 188", "\"etaSeconds\": 188.5"),
            available.replace("\"alongRouteMeters\": 382.6", "\"alongRouteMeters\": \"382.6\""),
            available + "{}").forEach { json ->
            assertThrows(Exception::class.java) { JsonCorridorDecoder.decode(json) }
        }
    }
}
