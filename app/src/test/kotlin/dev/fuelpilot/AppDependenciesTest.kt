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
    @Test fun departureReadsAFreshRecommendationWithoutWaitingForThePeriodicTimer() = runTest {
        val motionUpdates = MutableStateFlow(Motion.Parked)
        var range = 75.0
        val vm = start(fixture(
            energy = object : EnergyRepository { override suspend fun readEnergy() = EnergyResult.Available(range) },
            vehicle = object : VehicleEvents {
                override val motion = motionUpdates
                override val refuels = emptyFlow<RefuelEvent>()
            },
        ))
        runCurrent()
        assertEquals(75.0, vm.state.value.presentation.route.rangeKm!!, 0.0)
        range = 70.0
        motionUpdates.value = Motion.Moving
        runCurrent()
        assertEquals(Motion.Moving, vm.state.value.ui.motion)
        assertNotNull(vm.state.value.toString(), vm.state.value.presentation.route.rangeKm)
        assertEquals(70.0, vm.state.value.presentation.route.rangeKm!!, 0.0)
        assertNotNull(vm.state.value.activeRecommendationKey)
        assertNull(vm.state.value.pending)
    }

    @Test fun productionStationQueryUsesTheSameOriginAndRouteAsDecisionInput() = runTest {
        var queries = 0
        val base = fixture()
        val stations = object : ContextualStationRepository {
            override suspend fun findStations(): StationResult = error("Context-free query must not be used")
            override suspend fun findStationsFor(origin: VehicleLocation, route: ActiveRoute?): StationResult {
                queries++
                assertEquals(VehicleLocation(37.0,127.0), origin)
                assertEquals(ActiveRoute("route", "destination", 95.0), route)
                return StationResult.Available(listOf(candidate))
            }
        }
        val deps = AppDependencies(base.store,base.energy,base.location,stations,base.navigation,base.vehicleEvents)
        val result = deps.coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(1,queries)
        assertEquals(AssistAvailability.READY,result.availability)
    }

    @Test fun changedRouteOrUnknownWaypointCapacityNeverCallsNavigator() = runTest {
        for (capabilities in listOf(
            NavigationCapabilities(true, true, 2, 0, "different-route"),
            NavigationCapabilities(true, true, null, null, "route"),
        )) {
            var calls = 0
            val vm = start(fixture(navigation = object : NavigationRepository {
                override suspend fun readRoute() = RouteResult.Available(ActiveRoute("route", "destination", 95.0))
                override suspend fun capabilities() = capabilities
                override suspend fun addWaypoint(request: WaypointRequest): WaypointResult {
                    calls++; return WaypointResult.Succeeded(request.requestId, request.stationId)
                }
            }))
            runCurrent(); vm.dispatch(AssistIntent.SelectStation("a")); runCurrent()
            assertEquals(0, calls)
            assertFalse(vm.state.value.navigationResult is WaypointResult.Succeeded)
        }
    }

    @Test fun recommendationWithoutVerifiedNavigationTargetFailsClosed() = runTest {
        var calls = 0
        val stations = object : StationRepository {
            override suspend fun findStations() = StationResult.Available(listOf(candidate.copy(navigationTarget = null)))
        }
        val navigation = routePort(result = {
            calls++
            WaypointResult.Succeeded(it.requestId, it.stationId)
        })
        val vm = start(fixture(stations = stations, navigation = navigation))
        runCurrent()
        vm.dispatch(AssistIntent.SelectStation("a"))
        runCurrent()

        assertEquals(0, calls)
        assertTrue(vm.state.value.navigationResult is WaypointResult.Unsupported)
    }

    @Test fun nearbySelectionSetsDestinationAndDoesNotAppendAnExistingRouteWaypoint() = runTest {
        var destinations = 0; var waypoints = 0
        val vm = start(fixture(navigation = object : NavigationRepository {
            override suspend fun readRoute() = RouteResult.Available(null)
            override suspend fun capabilities() = NavigationCapabilities(true,true,2,0)
            override suspend fun setDestination(request: WaypointRequest): WaypointResult {
                destinations++; assertEquals(NavigationMode.SET_DESTINATION,request.mode)
                assertEquals(verifiedNavigationTarget, request.target)
                return WaypointResult.Succeeded(request.requestId,request.stationId)
            }
            override suspend fun addWaypoint(request: WaypointRequest): WaypointResult {
                waypoints++; return WaypointResult.Failed(request.requestId,request.stationId)
            }
        })); runCurrent()
        vm.dispatch(AssistIntent.SelectStation("a")); runCurrent()
        assertEquals(1,destinations); assertEquals(0,waypoints)
        assertEquals(Screen.Added,vm.state.value.ui.screen)
    }

    @Test fun fullWaypointCapacityReturnsAnExplicitFailureWithoutCallingNavigator() = runTest {
        var calls=0
        val vm=start(fixture(navigation=object: NavigationRepository {
            override suspend fun readRoute()=RouteResult.Available(ActiveRoute("route","destination",95.0))
            override suspend fun capabilities()=NavigationCapabilities(true,true,2,2,"route")
            override suspend fun addWaypoint(request: WaypointRequest): WaypointResult { calls++; return WaypointResult.Succeeded(request.requestId,request.stationId) }
        }));runCurrent();vm.dispatch(AssistIntent.SelectStation("a"));runCurrent()
        assertEquals(0,calls);assertTrue(vm.state.value.navigationResult is WaypointResult.CapacityReached)
    }

    @Test fun coordinatorUsesTheStoredReserveInsteadOfHardCodedFifty() = runTest {
        val store = object : AssistStateStore {
            override suspend fun read() = AssistStoredState(minimumReserveKm = 70)
            override suspend fun write(state: AssistStoredState) = Unit
        }
        val base = fixture()
        val deps = AppDependencies(store, base.energy, base.location, base.stations, base.navigation, base.vehicleEvents)
        val result = deps.coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertNull(result.route.recommended)
        assertEquals(AssistAvailability.UNAVAILABLE, result.availability)
    }

    @Test fun failedVehicleStreamInvalidatesParkedStateWithoutCrashingTheOwner() = runTest {
        val failure = CompletableDeferred<Unit>()
        val vm = start(fixture(vehicle = object : VehicleEvents {
            override val motion = flow {
                emit(Motion.Parked); failure.await(); throw SecurityException("revoked")
            }
            override val refuels = emptyFlow<RefuelEvent>()
        })); runCurrent()
        assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
        failure.complete(Unit); runCurrent()
        assertEquals(Motion.Unknown, vm.state.value.ui.motion)
        assertEquals(AssistAvailability.UNAVAILABLE, vm.state.value.presentation.availability)
        assertNull(vm.state.value.pending)
        assertNull(vm.state.value.presentation.route.recommended)
    }

    @Test fun defaultsUseUnsupportedPortsAndNeverInventRouteOrNumbers() = runTest {
        val d = AppDependencies(MemoryStore())
        assertSame(UnsupportedEnergyRepository, d.energy)
        assertSame(UnsupportedLocationRepository, d.location)
        assertSame(UnsupportedStationRepository, d.stations)
        assertSame(UnsupportedNavigationRepository, d.navigation)
        assertSame(UnsupportedRecommendationSpeechPort, d.recommendationSpeech)
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

    @Test fun missingStationSourceIsNamedWithoutBlamingTheVehicle() = runTest {
        val p = fixture(stations = object : StationRepository {
            override suspend fun findStations() = StationResult.Unavailable
        }).coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.UNAVAILABLE, p.availability)
        assertEquals(setOf(AssistSignal.STATIONS), p.blockedBy)
        assertEquals("차량과 경로는 확인했어요. 주유소 정보가 연결되면 안내할게요.", p.statusMessage())
    }

    @Test fun unqueriedContextualStationsAreNotBlamedForAMissingLocation() = runTest {
        val base = fixture()
        val stations = object : ContextualStationRepository {
            override suspend fun findStations(): StationResult = error("Context-free query must not be used")
            override suspend fun findStationsFor(origin: VehicleLocation, route: ActiveRoute?): StationResult = error("Not queried without location")
        }
        val location = object : LocationRepository { override suspend fun readLocation() = LocationResult.Unavailable }
        val p = AppDependencies(base.store, base.energy, location, stations, base.navigation, base.vehicleEvents)
            .coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(setOf(AssistSignal.LOCATION), p.blockedBy)
        assertEquals("현재 위치 정보를 확인할 수 없어요.", p.statusMessage())
    }

    @Test fun readyPresentationHasNoBlockedSignals() = runTest {
        val p = fixture().coordinator().readIntents().filterIsInstance<AssistIntent.PresentationChanged>().single().value
        assertEquals(AssistAvailability.READY, p.availability)
        assertTrue(p.blockedBy.isEmpty())
        assertNull(p.statusMessage())
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
        vehicle: VehicleEvents = object : VehicleEvents {
            override val motion = flowOf(Motion.Parked)
            override val refuels = emptyFlow<RefuelEvent>()
        },
    ) = AppDependencies(MemoryStore(), energy, object : LocationRepository {
        override suspend fun readLocation() = LocationResult.Available(VehicleLocation(37.0,127.0))
    }, stations, navigation, vehicle, refreshIntervalMs = 5000L, navigationTimeoutMs = 1000L)

    private fun routePort(route: ActiveRoute? = ActiveRoute("route", "destination", 95.0),
        result: suspend (WaypointRequest) -> WaypointResult = { WaypointResult.Unsupported(it.requestId,it.stationId) },
    ) = object : NavigationRepository {
        override suspend fun readRoute() = RouteResult.Available(route)
        override suspend fun addWaypoint(request: WaypointRequest) = result(request)
        override suspend fun setDestination(request: WaypointRequest) = result(request)
        override suspend fun capabilities() = NavigationCapabilities(true,true,2,0,route?.id)
    }

    private val verifiedNavigationTarget = NavigationTarget(
        stationId = "a",
        latitude = 37.5,
        longitude = 127.0,
        name = "Station A",
        provenance = NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84,
        poiId = "verified-poi-a",
        poiProvider = "PLEOS_NAVI",
    )
    private val candidate = FuelStationCandidate("a","Station A",20.0,20.0,
        openStatus = OpenStatus.OPEN, fuelCompatible = true, businessInfoFresh = true,
        navigationTarget = VerifiedStationNavigationTarget(
            stationId = verifiedNavigationTarget.stationId,
            latitude = verifiedNavigationTarget.latitude,
            longitude = verifiedNavigationTarget.longitude,
            provenance = verifiedNavigationTarget.provenance,
            poiId = verifiedNavigationTarget.poiId,
            poiProvider = verifiedNavigationTarget.poiProvider,
        ))
    private class MemoryStore : AssistStateStore {
        private var state = AssistStoredState()
        override suspend fun read() = state
        override suspend fun write(state: AssistStoredState) { this.state = state }
    }
}
