package dev.fuelpilot.data.navigation

import dev.fuelpilot.model.ActiveRoute

sealed interface RouteResult {
    data class Available(val route: ActiveRoute?) : RouteResult

    data class Stale(val route: ActiveRoute) : RouteResult

    data object Unavailable : RouteResult

    data object Unsupported : RouteResult
}

data class WaypointRequest(
    val requestId: String,
    val stationId: String,
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
}

interface NavigationRepository {
    suspend fun readRoute(): RouteResult

    suspend fun addWaypoint(request: WaypointRequest): WaypointResult
}

object UnsupportedNavigationRepository : NavigationRepository {
    override suspend fun readRoute(): RouteResult = RouteResult.Unsupported

    override suspend fun addWaypoint(request: WaypointRequest): WaypointResult =
        WaypointResult.Unsupported(
            requestId = request.requestId,
            stationId = request.stationId,
        )
}
