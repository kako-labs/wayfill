package dev.fuelpilot

import dev.fuelpilot.data.energy.*
import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.model.*
import dev.fuelpilot.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDependenciesTest {
    @Test fun defaultsUseUnsupportedPortsAndNeverInventRouteOrNumbers() = runTest {
        val d = AppDependencies(MemoryStore())
        assertSame(UnsupportedEnergyRepository, d.energy)
        assertSame(UnsupportedLocationRepository, d.location)
        assertSame(UnsupportedStationRepository, d.stations)
        assertSame(UnsupportedNavigationRepository, d.navigation)
        assertEquals(Motion.Unknown, d.vehicleEvents.motion.first())
        val p = d.coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.UNSUPPORTED, p.availability)
        assertNull(p.route.rangeKm); assertNull(p.route.recommended)
    }

    @Test fun confirmedNoRouteUsesNearbyButUnknownRouteNeverDoes() = runTest {
        val absent = fixture(navigation = routePort(null)).coordinator().readIntents()
            .filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.READY, absent.availability)
        assertNotNull(absent.route.recommended)
        val unsupported = fixture(navigation = UnsupportedNavigationRepository).coordinator().readIntents()
            .filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.UNSUPPORTED, unsupported.availability)
        assertNull(unsupported.route.recommended)
    }

    @Test fun stalePoiAndPermissionFailureExposeNoStaleRecommendation() = runTest {
        val stale = fixture(stations = object : StationRepository {
            override suspend fun findStations() = StationResult.Stale(listOf(candidate))
        }).coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.STALE, stale.availability); assertNull(stale.route.rangeKm)
        val denied = fixture(energy = object : EnergyRepository {
            override suspend fun readEnergy(): EnergyResult = throw SecurityException("denied")
        }).coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.UNAVAILABLE, denied.availability); assertNull(denied.route.recommended)
    }

    @Test fun stalledDataReadBecomesUnavailableInsteadOfFreezingMonitoring() = runTest {
        val coordinator = fixture(energy = object : EnergyRepository {
            override suspend fun readEnergy(): EnergyResult = awaitCancellation()
        }).coordinator()
        val read = async { coordinator.readIntents() }
        runCurrent(); advanceTimeBy(5001); runCurrent()
        assertTrue("A stalled adapter must have a deadline", read.isCompleted)
        val result = read.await().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.UNAVAILABLE, result.availability)
    }

    @Test fun realInputFlowsThroughEngineAndUiAndOneNavigationCallback() = runTest {
        var calls = 0
        val d = fixture(navigation = routePort { request ->
            calls++; WaypointResult.Succeeded(request.requestId, request.stationId)
        })
        val vm = start(d); runCurrent()
        assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
        assertEquals("a", vm.state.value.presentation.route.recommended?.id)
        vm.dispatch(AssistIntent.SelectStation("a")); vm.dispatch(AssistIntent.SelectStation("a")); runCurrent()
        assertEquals(1, calls); assertEquals(Screen.Added, vm.state.value.ui.screen)
    }

    @Test fun departureCancelsAnInflightNavigationCall() = runTest {
        var cancelled = false
        val vm = start(fixture(navigation = routePort { request ->
            try { delay(100_000); WaypointResult.Succeeded(request.requestId, request.stationId) }
            finally { cancelled = true }
        })); runCurrent()
        vm.dispatch(AssistIntent.SelectStation("a")); runCurrent()
        vm.dispatch(AssistIntent.MotionChanged(Motion.Moving)); runCurrent()
        assertTrue(cancelled); assertNull(vm.state.value.pending); assertNull(vm.state.value.ui.routeStation)
    }

    @Test fun timedOutNavigationReturnsRetryableFailure() = runTest {
        val vm = start(fixture(navigation = routePort { request ->
            delay(100_000); WaypointResult.Succeeded(request.requestId, request.stationId)
        })); runCurrent()
        vm.dispatch(AssistIntent.SelectStation("a")); runCurrent(); advanceTimeBy(1001); runCurrent()
        assertTrue(vm.state.value.navigationResult is WaypointResult.TimedOut)
        assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
    }

    private fun TestScope.start(d: AppDependencies): AssistViewModel {
        val c = d.coordinator()
        return AssistViewModel(d.store, liveIntents = c.liveIntents(), scope = backgroundScope).also {
            c.connectNavigation(it, backgroundScope)
        }
    }

    private fun fixture(
        energy: EnergyRepository = object : EnergyRepository { override suspend fun readEnergy() = EnergyResult.Available(75.0) },
        stations: StationRepository = object : StationRepository { override suspend fun findStations() = StationResult.Available(listOf(candidate)) },
        navigation: NavigationRepository = routePort(),
    ) = AppDependencies(MemoryStore(), energy, object : LocationRepository {
        override suspend fun readLocation() = LocationResult.Available(VehicleLocation(37.0,127.0))
    }, stations, navigation, object : VehicleEvents {
        override val motion = flowOf(Motion.Parked)
        override val refuels = emptyFlow<RefuelEvent>()
    }, refreshIntervalMs = 5000L, navigationTimeoutMs = 1000L)

    private fun routePort(route: ActiveRoute? = ActiveRoute("route", "destination", 95.0),
        result: suspend (WaypointRequest) -> WaypointResult = { WaypointResult.Unsupported(it.requestId,it.stationId) },
    ) = object : NavigationRepository {
        override suspend fun readRoute() = RouteResult.Available(route)
        override suspend fun addWaypoint(request: WaypointRequest) = result(request)
    }

    private val candidate = FuelStationCandidate("a","Station A",20.0,20.0,
        openStatus = OpenStatus.OPEN, fuelCompatible = true, businessInfoFresh = true)
    private class MemoryStore : AssistStateStore {
        private var state = AssistStoredState()
        override suspend fun read() = state
        override suspend fun write(state: AssistStoredState) { this.state = state }
    }
}
