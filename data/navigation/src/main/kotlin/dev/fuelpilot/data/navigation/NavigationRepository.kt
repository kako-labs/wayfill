package dev.fuelpilot.data.navigation

import dev.fuelpilot.model.ActiveRoute

sealed interface RouteResult {
    data class Available(val route: ActiveRoute?) : RouteResult

    data class Stale(val route: ActiveRoute) : RouteResult

    data object Unavailable : RouteResult

    data object Unsupported : RouteResult
}

enum class NavigationMode { ADD_WAYPOINT, SET_DESTINATION }
data class NavigationCapabilities(
    val supportsDestination: Boolean = false, val supportsWaypoint: Boolean = false,
    val maxWaypoints: Int? = null, val currentWaypoints: Int? = null, val activeRouteId: String? = null,
)

data class WaypointRequest(
    val requestId: String,
    val stationId: String,
    val mode: NavigationMode = NavigationMode.ADD_WAYPOINT,
    val expectedRouteId: String? = null,
)

sealed interface WaypointResult {
    val requestId: String
    val stationId: String

    data class Succeeded(
        override val requestId: String,
        override val stationId: String,
    ) : WaypointResult

    data class Failed(
        override val requestId: String,
        override val stationId: String,
    ) : WaypointResult

    data class TimedOut(
        override val requestId: String,
        override val stationId: String,
    ) : WaypointResult

    data class Cancelled(
        override val requestId: String,
        override val stationId: String,
    ) : WaypointResult

    data class Unsupported(
        override val requestId: String,
        override val stationId: String,
    ) : WaypointResult
    data class CapacityReached(override val requestId: String, override val stationId: String) : WaypointResult
}

interface NavigationRepository {
    suspend fun readRoute(): RouteResult

    suspend fun addWaypoint(request: WaypointRequest): WaypointResult
    suspend fun setDestination(request: WaypointRequest): WaypointResult = WaypointResult.Unsupported(request.requestId, request.stationId)
    suspend fun capabilities(): NavigationCapabilities = NavigationCapabilities()
}

object UnsupportedNavigationRepository : NavigationRepository {
    override suspend fun readRoute(): RouteResult = RouteResult.Unsupported

    override suspend fun addWaypoint(request: WaypointRequest): WaypointResult =
        WaypointResult.Unsupported(
            requestId = request.requestId,
            stationId = request.stationId,
        )
}
