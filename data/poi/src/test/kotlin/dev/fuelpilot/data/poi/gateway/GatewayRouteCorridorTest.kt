package dev.fuelpilot.data.poi.gateway

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GatewayRouteCorridorTest {
    private val query = RouteCorridorQuery(37.3947, 127.1112, 37.4979, 127.0276, StationFuel.GASOLINE)
    private val station = CorridorStation("A0001", "Station", StationFuel.GASOLINE, 1884.0, 37.3877, 127.1135,
        382.6, 1123.1, 188, 234)
    private val snapshot = RouteCorridorSnapshot(1000, 1030, 990, 16839.0, 1500, true, listOf(station))
    private val token = "AbCdEfGhIjKlMnOpQrSt_-"
    private fun document(snapshot: RouteCorridorSnapshot = this.snapshot) =
        CorridorDocument(1, "available", null, snapshot, "OPINET", "KAKAO_MOBILITY", "WGS84", token)
    private fun corridor(doc: CorridorDocument, status: Int = 200, now: Long = 1001, onPost: (String, String) -> Unit = { _, _ -> }) =
        GatewayRouteCorridor(GatewayConfig("https://example.com"),
            GatewayTransport { url, body -> onPost(url, body); GatewayHttpResponse(status, "{}") }, CorridorDecoder { doc }, { now })

    @Test fun missingConfigurationAndInvalidQueriesNeverCallNetwork() = runBlocking {
        val offline = GatewayRouteCorridor(null, GatewayTransport { _, _ -> error("network") })
        assertEquals(RouteCorridorResult.Unavailable(CorridorFailure.UNCONFIGURED), offline.measure(query))
        val configured = GatewayRouteCorridor(GatewayConfig("https://example.com"), GatewayTransport { _, _ -> error("network") })
        listOf(query.copy(originLatitude = Double.NaN), query.copy(destinationLongitude = 140.0)).forEach {
            assertEquals(RouteCorridorResult.Unavailable(CorridorFailure.INVALID_QUERY), configured.measure(it))
        }
    }

    @Test fun postsOnlyCoordinatesAndFuelToTheRouteEndpoint() = runBlocking {
        var url = ""; var body = ""
        val result = corridor(document()) { u, b -> url = u; body = b }.measure(query) as RouteCorridorResult.Available
        assertEquals("https://example.com/v1/stations/route", url)
        assertEquals("""{"origin":{"latitude":37.3947,"longitude":127.1112},"destination":{"latitude":37.4979,"longitude":127.0276},"fuel":"B027"}""", body)
        assertEquals(listOf(station), result.snapshot.stations)
        assertTrue(result.snapshot.coverageVerified)
    }

    @Test fun staleFutureMismatchedOrImpossibleSnapshotsFailClosed() = runBlocking {
        assertEquals(RouteCorridorResult.Unavailable(CorridorFailure.STALE), corridor(document(), now = 1030).measure(query))
        val invalid = listOf(
            document(snapshot.copy(fetchedAtEpochSeconds = 1002)),
            document(snapshot.copy(expiresAtEpochSeconds = 1100)),
            document(snapshot.copy(stationsObservedAtEpochSeconds = 1)),
            document().copy(routingProvider = "STRAIGHT_LINE"),
            document().copy(coordinateSystem = "KATEC"),
            document(snapshot.copy(stations = listOf(station.copy(fuel = StationFuel.DIESEL)))),
            document(snapshot.copy(stations = listOf(station, station))),
            document(snapshot.copy(stations = listOf(station.copy(alongRouteMeters = 20000.0)))),
            document(snapshot.copy(stations = listOf(station.copy(roadDistanceMeters = 100.0)))),
            document(snapshot.copy(stations = listOf(station.copy(latitude = 0.0)))),
            document(snapshot.copy(stations = listOf(station.copy(detourSeconds = -1)))),
            document().copy(corridorToken = null),
            document().copy(corridorToken = "has\"quote-and-wrong-len"),
        )
        invalid.forEach { doc ->
            assertEquals(doc.toString(), RouteCorridorResult.Unavailable(CorridorFailure.INVALID_RESPONSE), corridor(doc).measure(query))
        }
    }

    @Test fun serverReasonsMapWithoutLeakingBodies() = runBlocking {
        val reasons = mapOf("ROUTING_UNCONFIGURED" to CorridorFailure.UNCONFIGURED, "PROVIDER_UNCONFIGURED" to CorridorFailure.UNCONFIGURED,
            "ROUTE_NOT_FOUND" to CorridorFailure.ROUTE_UNAVAILABLE, "ROUTE_TOO_LONG" to CorridorFailure.ROUTE_UNAVAILABLE,
            "ROUTING_UNAVAILABLE" to CorridorFailure.NETWORK)
        reasons.forEach { (reason, failure) ->
            val doc = CorridorDocument(1, "unavailable", reason, null, null, null, null)
            assertEquals(RouteCorridorResult.Unavailable(failure), corridor(doc, status = 503).measure(query))
        }
        val quota = GatewayRouteCorridor(GatewayConfig("https://example.com"),
            GatewayTransport { _, _ -> GatewayHttpResponse(429, "SECRET") }, CorridorDecoder { error("must not decode") })
        assertEquals(RouteCorridorResult.Unavailable(CorridorFailure.QUOTA_EXHAUSTED), quota.measure(query))
        val oversized = GatewayRouteCorridor(GatewayConfig("https://example.com"),
            GatewayTransport { _, _ -> GatewayHttpResponse(200, "x".repeat(262145)) }, CorridorDecoder { error("must not decode") })
        assertEquals(RouteCorridorResult.Unavailable(CorridorFailure.INVALID_RESPONSE), oversized.measure(query))
    }

    @Test fun issuedTokenIsSentBackOnlyForTheSameTrip() = runBlocking {
        val bodies = mutableListOf<String>()
        val client = corridor(document()) { _, body -> bodies += body }
        client.measure(query)
        client.measure(query.copy(originLatitude = 37.40))
        client.measure(query.copy(fuel = StationFuel.DIESEL))
        assertFalse(bodies[0].contains("corridorToken"))
        assertTrue(bodies[1].endsWith(""""fuel":"B027","corridorToken":"$token"}"""))
        assertFalse(bodies[2].contains("corridorToken"))
    }

    @Test fun configDerivesBothEndpointsFromOneOrigin() {
        val config = GatewayConfig("https://example.com/")
        assertEquals("https://example.com/v1/stations/discover", config.endpoint)
        assertEquals("https://example.com/v1/stations/route", config.routeEndpoint)
    }
}
