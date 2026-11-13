package dev.fuelpilot.demo

import dev.fuelpilot.*
import dev.fuelpilot.data.energy.*
import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.feature.assist.AssistStateStore
import dev.fuelpilot.model.*
import dev.fuelpilot.ui.*
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Debug source set only. These fixtures must never be available to production dependencies. */
class DemoSources : EnergyRepository, LocationRepository, NavigationRepository, StationRepository, VehicleEvents {
    private var sequence = 0
    private val sessionId = UUID.randomUUID().toString()
    var scenario: String = "recommendation"
        private set
    var navigationCalls = 0
        private set
    var navigationSucceeds = true
    var navigationDelayMs = 700L
    val currentRouteId: String?
        get() = routeId.takeUnless { scenario == "nearby" }
    override val motion = MutableStateFlow(Motion.Parked)
    override val refuels = MutableSharedFlow<RefuelEvent>(extraBufferCapacity = 16)
    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private var lastRefuel: RefuelEvent? = null
    private var rangeAfterRefuel: Double? = null
    private var routeId = "demo-$sessionId-0"

    fun dependencies(store: AssistStateStore, reducedMotion: Boolean) = AppDependencies(
        store, this, this, this, this, this,
        refreshIntervalMs = 60_000, refreshRequests = refresh, reduceMotion = flowOf(reducedMotion),
    )

    fun select(value: String) {
        rangeAfterRefuel = null
        scenario = value
        routeId = "demo-$sessionId-${++sequence}"
        refresh.tryEmit(Unit)
    }

    fun refuel(variant: String) {
        val event = RefuelEvent("demo-$sessionId-refuel-${++sequence}",
            liters = if (variant == "small") 5.0 else 40.0, rangeKm = 550.0,
            isFull = variant != "small", wasLow = variant == "recovery")
        lastRefuel = event
        rangeAfterRefuel = event.rangeKm
        refuels.tryEmit(event)
    }
    fun duplicate() { lastRefuel?.let { refuels.tryEmit(it) } }
    override suspend fun readEnergy(): EnergyResult = when (scenario) {
        "stale" -> EnergyResult.Stale(150.0)
        "unsupported" -> EnergyResult.Unsupported
        "unavailable" -> EnergyResult.Unavailable
        "noaction" -> EnergyResult.Available(400.0)
        else -> EnergyResult.Available(rangeAfterRefuel ?: 150.0)
    }
    override suspend fun readLocation() = LocationResult.Available(VehicleLocation(37.5, 127.0, 5.0))
    override suspend fun readRoute() = RouteResult.Available(
        if (scenario == "nearby") null else ActiveRoute(routeId, "시연 목적지", 250.0))
    override suspend fun findStations(): StationResult {
        val distance = if (scenario == "passing") 3.0 else 15.0
        return StationResult.Available(listOf(
            FuelStationCandidate("demo-a", "시연 주유소 A", distance, distance, 10, 2, OpenStatus.OPEN, true, true,
                VerifiedStationNavigationTarget("demo-a", 37.51, 127.01,
                    NavigationTargetProvenance.VERIFIED_PROVIDER_WGS84,
                    poiId = "synthetic-demo-a", poiProvider = "WAYFILL_SYNTHETIC_ONLY")),
            FuelStationCandidate("demo-b", "시연 주유소 B", 210.0, 210.0, 80, 3, OpenStatus.OPEN, true, true,
                VerifiedStationNavigationTarget("demo-b", 37.62, 127.12,
                    NavigationTargetProvenance.VERIFIED_PROVIDER_WGS84,
                    poiId = "synthetic-demo-b", poiProvider = "WAYFILL_SYNTHETIC_ONLY")),
        ))
    }
    override suspend fun capabilities() = NavigationCapabilities(
        supportsDestination = true, supportsWaypoint = true,
        maxWaypoints = 5, currentWaypoints = 0, activeRouteId = if (scenario == "nearby") null else routeId,
    )
    override suspend fun addWaypoint(request: WaypointRequest): WaypointResult {
        navigationCalls++
        delay(navigationDelayMs.coerceIn(0,5000))
        return if (navigationSucceeds) WaypointResult.Succeeded(request.requestId, request.stationId)
        else WaypointResult.Failed(request.requestId, request.stationId)
    }
    override suspend fun setDestination(request: WaypointRequest) = addWaypoint(request)
}
