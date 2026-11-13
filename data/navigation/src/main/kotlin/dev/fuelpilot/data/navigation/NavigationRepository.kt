package dev.fuelpilot.data.navigation

import dev.fuelpilot.model.ActiveRoute
import dev.fuelpilot.model.NavigationTargetProvenance
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

data class NavigationTarget(
    val stationId: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val provenance: NavigationTargetProvenance,
    val poiId: String? = null,
    val address: String? = null,
    val poiSubId: String? = null,
    val poiProvider: String? = null,
) {
    fun isVerifiedWgs84(): Boolean = stationId.isNotBlank() && latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 && name.isNotBlank() &&
        listOf(poiId, address, poiSubId, poiProvider).all { it == null || it.isNotBlank() }
}

data class NavigationObservedTarget(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val poiId: String?,
    val poiSubId: String?,
    val poiProvider: String?,
)

internal fun NavigationObservedTarget.matches(target: NavigationTarget): Boolean {
    val expectedPoiId = target.poiId?.takeIf(String::isNotBlank)
    val expectedProvider = target.poiProvider?.takeIf(String::isNotBlank)
    val expectedSubId = target.poiSubId?.takeIf(String::isNotBlank)
    if (expectedPoiId != null) {
        if (poiId != expectedPoiId) return false
        if (expectedProvider != null && poiProvider != expectedProvider) return false
        if (expectedSubId != null && poiSubId != expectedSubId) return false
        return true
    }
    if (expectedProvider != null && !poiProvider.isNullOrBlank() && poiProvider != expectedProvider) return false
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    return distanceMeters(latitude, longitude, target.latitude, target.longitude) <= COORDINATE_FALLBACK_METERS &&
        compatibleName(name, target.name)
}

private fun compatibleName(observed: String, expected: String): Boolean {
    fun String.normalized() = lowercase().filter(Char::isLetterOrDigit)
    val left = observed.normalized()
    val right = expected.normalized()
    return left.isNotBlank() && right.isNotBlank() &&
        (left == right || (minOf(left.length, right.length) >= 4 && (left.contains(right) || right.contains(left))))
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val a = sin(latitudeDelta / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(longitudeDelta / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val COORDINATE_FALLBACK_METERS = 35.0

data class WaypointRequest(
    val requestId: String,
    val stationId: String,
    val mode: NavigationMode = NavigationMode.ADD_WAYPOINT,
    val expectedRouteId: String? = null,
    val target: NavigationTarget? = null,
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

data class NaviRouteSnapshot(
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val remainingDistanceMeters: Double,
    /** Exact NaviHelper CurrentLocationInfo.routeId; no destination-derived fallback is allowed. */
    val routeToken: String,
)

sealed interface NaviRead<out T> {
    data class Available<T>(val value: T) : NaviRead<T>
    data object Unavailable : NaviRead<Nothing>
    data object Unsupported : NaviRead<Nothing>
}

enum class NaviCommandStatus { SUCCEEDED, FAILED, TIMED_OUT, UNSUPPORTED }

/** Road distance and time the vehicle navigation computed from its own current position. */
data class NaviTrip(val distanceMeters: Int, val etaSeconds: Int)

/** The vehicle navigation's own current position: the origin its routing and trip previews use. */
data class NaviPosition(val latitude: Double, val longitude: Double)

interface NaviHelperPort {
    fun isSupported(): Boolean
    suspend fun readRoute(): NaviRead<NaviRouteSnapshot?>
    suspend fun readWaypointCount(): NaviRead<Int>
    suspend fun addWaypoint(target: NavigationTarget, index: Int): NaviCommandStatus
    suspend fun setDestination(target: NavigationTarget): NaviCommandStatus
    /** Side-effect-free trip preview to one point; the active route is not changed. */
    suspend fun measureTrip(latitude: Double, longitude: Double): NaviRead<NaviTrip> = NaviRead.Unsupported
    suspend fun readCurrentLocation(): NaviRead<NaviPosition> = NaviRead.Unsupported
}

class PleosNavigationRepository(
    private val port: NaviHelperPort,
    private val hasRoutePermission: () -> Boolean,
    private val hasRouteSearchPermission: () -> Boolean,
) : NavigationRepository {
    override suspend fun readRoute(): RouteResult {
        if (!port.isSupported() || !hasRoutePermission()) return RouteResult.Unsupported
        return when (val read = port.readRoute()) {
            is NaviRead.Available -> read.value?.let(::mapRoute) ?: RouteResult.Available(null)
            NaviRead.Unavailable -> RouteResult.Unavailable
            NaviRead.Unsupported -> RouteResult.Unsupported
        }
    }

    override suspend fun capabilities(): NavigationCapabilities {
        if (!port.isSupported()) return NavigationCapabilities()
        val canRead = hasRoutePermission()
        val canSearch = hasRouteSearchPermission()
        if (!canRead || !canSearch) return NavigationCapabilities()
        val route = when (val read = port.readRoute()) {
            is NaviRead.Available -> read.value
            NaviRead.Unavailable, NaviRead.Unsupported -> return NavigationCapabilities()
        }
        val count = when (val read = port.readWaypointCount()) {
            is NaviRead.Available -> read.value.takeIf { it in 0..MAX_WAYPOINTS }
                ?: return NavigationCapabilities()
            NaviRead.Unavailable, NaviRead.Unsupported -> return NavigationCapabilities()
        }
        return NavigationCapabilities(
            supportsDestination = true,
            supportsWaypoint = route != null,
            maxWaypoints = MAX_WAYPOINTS,
            currentWaypoints = count,
            activeRouteId = route?.let(::routeId),
        )
    }

    override suspend fun addWaypoint(request: WaypointRequest): WaypointResult {
        val target = request.target
        if (!port.isSupported() || !hasRoutePermission() || !hasRouteSearchPermission() ||
            target == null || target.stationId != request.stationId || !target.isVerifiedWgs84()
        ) return unsupported(request)
        val route = when (val read = port.readRoute()) {
            is NaviRead.Available -> read.value
            NaviRead.Unsupported -> return unsupported(request)
            NaviRead.Unavailable -> return failed(request)
        }
        if (route == null || request.expectedRouteId == null || routeId(route) != request.expectedRouteId)
            return WaypointResult.Cancelled(request.requestId, request.stationId)
        val count = when (val read = port.readWaypointCount()) {
            is NaviRead.Available -> read.value
            NaviRead.Unsupported -> return unsupported(request)
            NaviRead.Unavailable -> return failed(request)
        }
        if (count !in 0..MAX_WAYPOINTS) return failed(request)
        if (count >= MAX_WAYPOINTS) return WaypointResult.CapacityReached(request.requestId, request.stationId)
        return mapCommand(request, port.addWaypoint(target, count))
    }

    override suspend fun setDestination(request: WaypointRequest): WaypointResult {
        val target = request.target
        if (!port.isSupported() || !hasRoutePermission() || !hasRouteSearchPermission() ||
            target == null || target.stationId != request.stationId || !target.isVerifiedWgs84()
        )
            return unsupported(request)
        when (val read = port.readRoute()) {
            is NaviRead.Available -> if (read.value != null) return WaypointResult.Cancelled(request.requestId, request.stationId)
            NaviRead.Unsupported -> return unsupported(request)
            NaviRead.Unavailable -> return failed(request)
        }
        return mapCommand(request, port.setDestination(target))
    }

    private fun mapRoute(snapshot: NaviRouteSnapshot): RouteResult {
        if (snapshot.destinationName.isBlank() || !snapshot.destinationLatitude.isFinite() ||
            snapshot.destinationLatitude !in -90.0..90.0 || !snapshot.destinationLongitude.isFinite() ||
            snapshot.destinationLongitude !in -180.0..180.0 || !snapshot.remainingDistanceMeters.isFinite() ||
            snapshot.remainingDistanceMeters < 0.0 || snapshot.routeToken.isBlank()
        ) return RouteResult.Unavailable
        return RouteResult.Available(
            ActiveRoute(routeId(snapshot), snapshot.destinationName, snapshot.remainingDistanceMeters / 1000.0)
        )
    }

    private fun routeId(snapshot: NaviRouteSnapshot) = "navi:${snapshot.routeToken}"
    private fun unsupported(request: WaypointRequest) = WaypointResult.Unsupported(request.requestId, request.stationId)
    private fun failed(request: WaypointRequest) = WaypointResult.Failed(request.requestId, request.stationId)
    private fun mapCommand(request: WaypointRequest, status: NaviCommandStatus): WaypointResult = when (status) {
        NaviCommandStatus.SUCCEEDED -> WaypointResult.Succeeded(request.requestId, request.stationId)
        NaviCommandStatus.FAILED -> failed(request)
        NaviCommandStatus.TIMED_OUT -> WaypointResult.TimedOut(request.requestId, request.stationId)
        NaviCommandStatus.UNSUPPORTED -> unsupported(request)
    }

    private companion object { const val MAX_WAYPOINTS = 2 }
}
