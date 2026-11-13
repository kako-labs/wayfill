package dev.fuelpilot.data.navigation

import ai.pleos.playground.navi.constants.NaviMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePhaseTest {
    @Test fun guidanceIsAnActiveRouteEvenThoughNothingIsBeingCalculated() {
        // Observed on PLEOS during live guidance: naviMode=ROUTE_GUIDING, isRouting=false.
        assertEquals(RoutePhase.GUIDING, routePhase(NaviMode.ROUTE_GUIDING, isRouting = false))
        assertEquals(RoutePhase.GUIDING, routePhase(NaviMode.ROUTE_GUIDING, isRouting = true))
        assertEquals(RoutePhase.GUIDING, routePhase(NaviMode.SIMULATION_GUIDING, isRouting = false))
    }

    @Test fun freeDriveIsConfirmedNoRouteAndCalculationIsNotYetKnown() {
        assertEquals(RoutePhase.NONE, routePhase(NaviMode.SAFE_GUIDING, isRouting = false))
        assertEquals(RoutePhase.NONE, routePhase(NaviMode.NOT_WORKING, isRouting = false))
        assertEquals(RoutePhase.CALCULATING, routePhase(NaviMode.NOT_WORKING, isRouting = true))
    }
}
