package dev.fuelpilot.data.navigation

import dev.fuelpilot.model.ActiveRoute
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

    private companion object {
        const val REQUEST_ID = "request-123"
        const val STATION_ID = "station-456"
    }
}
