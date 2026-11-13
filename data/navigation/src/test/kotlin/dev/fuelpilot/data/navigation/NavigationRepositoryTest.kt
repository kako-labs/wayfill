package dev.fuelpilot.data.navigation

import dev.fuelpilot.model.ActiveRoute
import dev.fuelpilot.model.NavigationTargetProvenance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRepositoryTest {
    @Test
    fun routeOutcomeTypesRemainDistinct() {
        val route = ActiveRoute(
            id = "route-123",
            destinationName = "destination",
            remainingDistanceKm = 1.0,
        )
        val outcomes = listOf<RouteResult>(
            RouteResult.Available(route),
            RouteResult.Stale(route),
            RouteResult.Unavailable,
            RouteResult.Unsupported,
        )

        assertEquals(4, outcomes.map { it::class }.toSet().size)
    }

    @Test
    fun everyTerminalResultCarriesRequestAndStationIds() {
        val results = listOf<WaypointResult>(
            WaypointResult.Succeeded(REQUEST_ID, STATION_ID),
            WaypointResult.Failed(REQUEST_ID, STATION_ID),
            WaypointResult.TimedOut(REQUEST_ID, STATION_ID),
            WaypointResult.Cancelled(REQUEST_ID, STATION_ID),
            WaypointResult.Unsupported(REQUEST_ID, STATION_ID),
        )

        results.forEach { result ->
            assertEquals(REQUEST_ID, result.requestId)
            assertEquals(STATION_ID, result.stationId)
        }
    }

    @Test
    fun defaultRepositoryReturnsOnlyUnsupportedWithoutRouteOrSuccess() = runBlocking {
        val request = WaypointRequest(REQUEST_ID, STATION_ID)

        val routeResult = UnsupportedNavigationRepository.readRoute()
        val result = UnsupportedNavigationRepository.addWaypoint(request)

        assertSame(RouteResult.Unsupported, routeResult)
        assertTrue(result is WaypointResult.Unsupported)
        assertEquals(REQUEST_ID, result.requestId)
        assertEquals(STATION_ID, result.stationId)
    }

    @Test fun mapsVerifiedRouteMetersToKmWithDestination() = runBlocking {
        val repository = PleosNavigationRepository(port(route = NaviRead.Available(
            NaviRouteSnapshot("Destination", 37.5, 127.0, 12_300.0, "route-token")
        )), { true }, { true })

        assertEquals(RouteResult.Available(ActiveRoute("navi:route-token", "Destination", 12.3, 37.5, 127.0)),
            repository.readRoute())
    }

    @Test fun navigationMutationRequiresVerifiedTargetAndSdkCallback() = runBlocking {
        val fake = port(
            route = NaviRead.Available(NaviRouteSnapshot("Destination", 37.5, 127.0, 1000.0, "route-token")),
            command = NaviCommandStatus.SUCCEEDED,
        )
        val repository = PleosNavigationRepository(fake, { true }, { true })
        val missing = WaypointRequest(REQUEST_ID, STATION_ID)
        val target = target()

        assertTrue(repository.addWaypoint(missing) is WaypointResult.Unsupported)
        assertTrue(repository.addWaypoint(missing.copy(expectedRouteId = "navi:route-token", target = target)) is WaypointResult.Succeeded)
        assertEquals(target, fake.lastTarget)
    }

    @Test fun targetForAnotherStationFailsClosedWithoutCallingSdk() = runBlocking {
        val fake = port(
            route = NaviRead.Available(NaviRouteSnapshot("Destination", 37.5, 127.0, 1000.0, "route-token")),
            command = NaviCommandStatus.SUCCEEDED,
        )
        val repository = PleosNavigationRepository(fake, { true }, { true })
        val request = WaypointRequest(
            REQUEST_ID,
            STATION_ID,
            expectedRouteId = "navi:route-token",
            target = target(stationId = "another-station"),
        )

        assertTrue(repository.addWaypoint(request) is WaypointResult.Unsupported)
        assertEquals(0, fake.routeReads)
        assertEquals(null, fake.lastTarget)
    }

    @Test fun preservesCorrelationAcrossFailureAndTimeout() = runBlocking {
        val target = target()
        for (status in listOf(NaviCommandStatus.FAILED, NaviCommandStatus.TIMED_OUT, NaviCommandStatus.UNSUPPORTED)) {
            val result = PleosNavigationRepository(port(route = NaviRead.Available(null), command = status), { true }, { true })
                .setDestination(WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target))
            assertEquals(REQUEST_ID, result.requestId)
            assertEquals(STATION_ID, result.stationId)
        }
    }

    @Test fun destinationAndCapabilitiesRequireBothNaviPermissions() = runBlocking {
        val target = target()
        val fake = port(route = NaviRead.Available(null), command = NaviCommandStatus.SUCCEEDED)
        val missingRead = PleosNavigationRepository(fake, { false }, { true })
        val missingSearch = PleosNavigationRepository(fake, { true }, { false })

        assertEquals(NavigationCapabilities(), missingRead.capabilities())
        assertEquals(NavigationCapabilities(), missingSearch.capabilities())
        assertTrue(missingRead.setDestination(
            WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target)
        ) is WaypointResult.Unsupported)
        assertTrue(missingSearch.setDestination(
            WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target)
        ) is WaypointResult.Unsupported)
        assertEquals(0, fake.routeReads)
    }

    @Test fun serviceUnsupportedMapsSeparatelyFromCommandTimeout() = runBlocking {
        val target = target()
        val unsupported = PleosNavigationRepository(port(supported = false), { true }, { true })
        val initializedButUnavailable = PleosNavigationRepository(
            port(route = NaviRead.Available(null), command = NaviCommandStatus.UNSUPPORTED), { true }, { true }
        )
        val timedOut = PleosNavigationRepository(
            port(route = NaviRead.Available(null), command = NaviCommandStatus.TIMED_OUT), { true }, { true }
        )

        assertSame(RouteResult.Unsupported, unsupported.readRoute())
        assertTrue(unsupported.setDestination(
            WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target)
        ) is WaypointResult.Unsupported)
        assertTrue(initializedButUnavailable.setDestination(
            WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target)
        ) is WaypointResult.Unsupported)
        assertTrue(timedOut.setDestination(
            WaypointRequest(REQUEST_ID, STATION_ID, NavigationMode.SET_DESTINATION, target = target)
        ) is WaypointResult.TimedOut)
    }

    @Test fun poiIdentityPrecedesNormalizedCoordinateFallback() {
        val identified = target(poiId = "poi-1", poiProvider = "provider")
        assertTrue(NavigationObservedTarget(0.0, 0.0, "Other", "poi-1", null, "provider").matches(identified))
        assertTrue(!NavigationObservedTarget(37.5, 127.0, "Station", "poi-1", null, "other").matches(identified))

        val coordinateOnly = target(name = "Fuel Station")
        assertTrue(NavigationObservedTarget(37.50015, 127.0, "Fuel Station Gangnam", null, null, null)
            .matches(coordinateOnly))
        assertTrue(!NavigationObservedTarget(37.50015, 127.0, "Different POI", null, null, null)
            .matches(coordinateOnly))
        assertTrue(!NavigationObservedTarget(37.5005, 127.0, "Fuel Station", null, null, null)
            .matches(coordinateOnly))
    }

    @Test fun unreportedWaypointCoordinatesFallBackToTheNameAlone() {
        // PLEOS returned WaypointInfo(name=주유소티파니블루, latitude=0.0, longitude=0.0, ...) for a real waypoint.
        val station = target(name = "주유소티파니블루")
        assertTrue(NavigationObservedTarget(0.0, 0.0, "주유소티파니블루", "", null, "").matches(station))
        assertTrue(!NavigationObservedTarget(0.0, 0.0, "다른주유소", "", null, "").matches(station))
        assertTrue(!NavigationObservedTarget(0.0, 0.0, "", "", null, "").matches(station))
        // Reported coordinates still have to agree; a matching name cannot rescue a different place.
        assertTrue(!NavigationObservedTarget(37.6, 127.0, "주유소티파니블루", "", null, "").matches(station))
    }

    private class FakePort(
        private val route: NaviRead<NaviRouteSnapshot?>,
        private val command: NaviCommandStatus,
        private val supported: Boolean,
    ) : NaviHelperPort {
        var lastTarget: NavigationTarget? = null
        var routeReads = 0
        override fun isSupported() = supported
        override suspend fun readRoute(): NaviRead<NaviRouteSnapshot?> {
            routeReads++
            return route
        }
        override suspend fun readWaypointCount(): NaviRead<Int> = NaviRead.Available(0)
        override suspend fun addWaypoint(target: NavigationTarget, index: Int): NaviCommandStatus {
            lastTarget = target
            return command
        }
        override suspend fun setDestination(target: NavigationTarget): NaviCommandStatus {
            lastTarget = target
            return command
        }
    }

    private fun port(
        route: NaviRead<NaviRouteSnapshot?> = NaviRead.Available(null),
        command: NaviCommandStatus = NaviCommandStatus.FAILED,
        supported: Boolean = true,
    ) = FakePort(route, command, supported)

    private fun target(
        stationId: String = STATION_ID,
        name: String = "Station",
        poiId: String? = null,
        poiProvider: String? = null,
    ) = NavigationTarget(
        stationId = stationId,
        latitude = 37.5,
        longitude = 127.0,
        name = name,
        provenance = NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84,
        poiId = poiId,
        poiProvider = poiProvider,
    )

    private companion object {
        const val REQUEST_ID = "request-123"
        const val STATION_ID = "station-456"
    }
}
