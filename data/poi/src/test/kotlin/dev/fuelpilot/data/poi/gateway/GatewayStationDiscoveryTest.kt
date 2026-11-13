package dev.fuelpilot.data.poi.gateway

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class GatewayStationDiscoveryTest {
    private val query = StationDiscoveryQuery(StationCoordinates(CoordinateSystem.KATEC, 314681.8, 544837.0), 5000, StationFuel.GASOLINE)
    private fun document(now: Long = 1000) = GatewayDocument(
        1, "available", null, now, now + 300,
        StationProvenance("OPINET", "aroundAll", CoordinateSystem.KATEC, false, false),
        listOf(RawCatalogStation("id", "Station", StationFuel.GASOLINE, 1700.0,
            StationCoordinates(CoordinateSystem.KATEC, 314871.8, 544012.0), 846.6, null)))

    @Test fun missingConfigurationNeverCallsNetwork() = runBlocking {
        val repository = GatewayStationDiscovery(null, GatewayTransport { _, _ -> error("network") })
        assertEquals(StationDiscoveryResult.Unavailable(DiscoveryFailure.UNCONFIGURED), repository.discover(query))
    }

    @Test fun configRejectsHttpCredentialsQueryAndFragment() {
        listOf("http://example.com", "https://key@example.com", "https://example.com?key=x", "https://example.com/#key").forEach {
            assertTrue(it, runCatching { GatewayConfig(it) }.isFailure)
        }
        assertEquals("https://example.com/v1/stations/discover", GatewayConfig("https://example.com/").endpoint)
    }

    @Test fun rawCatalogKeepsProvenanceAndNeverCreatesRoadMetrics() = runBlocking {
        var body = ""
        val repository = GatewayStationDiscovery(GatewayConfig("https://example.com"),
            GatewayTransport { url, request -> assertEquals("https://example.com/v1/stations/discover", url); body = request; GatewayHttpResponse(200, "{}") },
            GatewayDecoder { document() }, { 1001 })
        val result = repository.discover(query) as StationDiscoveryResult.Available
        assertEquals(846.6, result.catalog.stations.single().providerDistanceMeters, 0.0)
        assertFalse(result.catalog.provenance.roadMetricsVerified)
        assertNull(result.catalog.stations.single().priceObservedAtEpochSeconds)
        assertTrue(body.contains("KATEC"))
        assertFalse(body.contains("key", ignoreCase = true))
    }

    @Test fun staleFutureAndUnverifiedRoadClaimsFailClosed() = runBlocking {
        val valid = document()
        val documents = listOf(valid.copy(expiresAtEpochSeconds = 1000), valid.copy(fetchedAtEpochSeconds = 1002),
            valid.copy(provenance = valid.provenance!!.copy(roadMetricsVerified = true)),
            valid.copy(stations = valid.stations.map { it.copy(coordinates = it.coordinates.copy(x = Double.NaN)) }),
            valid.copy(stations = valid.stations.map { it.copy(fuel = StationFuel.DIESEL) }))
        documents.forEach { doc ->
            val repository = GatewayStationDiscovery(GatewayConfig("https://example.com"),
                GatewayTransport { _, _ -> GatewayHttpResponse(200, "{}") }, GatewayDecoder { doc }, { 1001 })
            assertTrue(repository.discover(query) is StationDiscoveryResult.Unavailable)
        }
    }

    @Test fun errorsAndOversizedResponsesCannotLeakProviderMessage() = runBlocking {
        listOf(GatewayHttpResponse(429, "SECRET"), GatewayHttpResponse(200, "x".repeat(262145))).forEach { response ->
            val repository = GatewayStationDiscovery(GatewayConfig("https://example.com"),
                GatewayTransport { _, _ -> response }, GatewayDecoder { error("must not decode") })
            val result = repository.discover(query)
            assertTrue(result is StationDiscoveryResult.Unavailable)
            assertFalse(result.toString().contains("SECRET"))
        }
    }

    @Test fun invalidQueryNeverCallsNetwork() = runBlocking {
        val repository = GatewayStationDiscovery(GatewayConfig("https://example.com"), GatewayTransport { _, _ -> error("network") })
        assertEquals(StationDiscoveryResult.Unavailable(DiscoveryFailure.INVALID_QUERY), repository.discover(query.copy(radiusMeters = 5001)))
    }
}
