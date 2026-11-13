package dev.fuelpilot.data.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RoutePointType
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot

class PleosNaviHelperPort(
    context: Context,
    private val operationTimeoutMs: Long = 4_000L,
    private val maxConnectionAttempts: Int = 2,
    private val retryDelayMs: Long = 150L,
) : NaviHelperPort {
    private val appContext = context.applicationContext
    private val operations = Mutex()

    init { require(operationTimeoutMs > 0 && maxConnectionAttempts > 0 && retryDelayMs >= 0) }

    override fun isSupported(): Boolean {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getApplicationInfo(
                    NAVI_SERVICE_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getApplicationInfo(NAVI_SERVICE_PACKAGE, 0)
            }
        }.isSuccess
    }

    // These callbacks run after a custom permission gate; async revocation is caught by runSession.
    @SuppressLint("MissingPermission")
    override suspend fun readRoute(): NaviRead<NaviRouteSnapshot?> = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION)) return@withLock NaviRead.Unsupported
        runWithRetries<NaviRead<NaviRouteSnapshot?>>(
            NaviRead.Unavailable, NaviRead.Unsupported, NaviRead.Unavailable,
        ) { complete, _, _ ->
            var state: RouteStateInfo? = null
            var destination: DestinationInfo? = null
            var currentLocation: CurrentLocationInfo? = null
            fun publishIfComplete() {
                if (state?.let { routePhase(it.naviMode, it.isRouting) } == RoutePhase.GUIDING) {
                    val destinationInfo = destination ?: return
                    val current = currentLocation ?: return
                    if (current.routeId.isBlank()) complete(NaviRead.Unavailable)
                    else complete(NaviRead.Available(destinationInfo.toSnapshot(current.routeId)))
                }
            }
            val listener = object : NaviHelperEventListener {
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                    state = routeStateInfo
                    when (routePhase(routeStateInfo.naviMode, routeStateInfo.isRouting)) {
                        RoutePhase.GUIDING -> publishIfComplete()
                        RoutePhase.CALCULATING -> complete(NaviRead.Unavailable)
                        RoutePhase.NONE -> complete(NaviRead.Available(null))
                    }
                }

                override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                    destination = destinationInfo
                    publishIfComplete()
                }

                override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                    currentLocation = currentLocationInfo
                    publishIfComplete()
                }

                override fun onError(errorCode: NaviErrorCode) = complete(NaviRead.Unavailable)
            }
            Session(listener, onConnected = { connected ->
                connected.getRouteStateInfo()
                connected.getDestinationInfo()
                connected.getCurrentLocationInfo()
            })
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun readWaypointCount(): NaviRead<Int> = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION)) return@withLock NaviRead.Unsupported
        runWithRetries<NaviRead<Int>>(
            NaviRead.Unavailable, NaviRead.Unsupported, NaviRead.Unavailable,
        ) { complete, _, _ ->
            val listener = object : NaviHelperEventListener {
                override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) =
                    complete(NaviRead.Available(waypointInfo.size))
                override fun onError(errorCode: NaviErrorCode) = complete(NaviRead.Unavailable)
            }
            Session(listener) { it.getWaypointInfo() }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun addWaypoint(target: NavigationTarget, index: Int): NaviCommandStatus = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION) || !hasPermission(NAVI_SEARCH_PERMISSION)) return@withLock NaviCommandStatus.UNSUPPORTED
        val waypointIndex = when (index) {
            0 -> WaypointIndex.FIRST
            1 -> WaypointIndex.SECOND
            else -> return@withLock NaviCommandStatus.UNSUPPORTED
        }
        runWithRetries<NaviCommandStatus>(
            NaviCommandStatus.TIMED_OUT,
            NaviCommandStatus.UNSUPPORTED,
            NaviCommandStatus.FAILED,
        ) { complete, unsupported, failed ->
            val requestSubmitted = AtomicBoolean(false)
            lateinit var helper: NaviHelper
            val listener = object : NaviHelperEventListener {
                override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
                    if (waypointChangedInfo.cmdType == WaypointCmdType.ADD && waypointChangedInfo.index == waypointIndex) {
                        try { helper.getWaypointInfo() }
                        catch (_: SecurityException) { unsupported() }
                        catch (_: Exception) { failed() }
                    }
                }

                override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
                    if (requestSubmitted.get()) complete(
                        if (waypointInfo.getOrNull(index)?.matches(target) == true) NaviCommandStatus.SUCCEEDED
                        else NaviCommandStatus.FAILED
                    )
                }

                override fun onError(errorCode: NaviErrorCode) = complete(NaviCommandStatus.FAILED)
            }
            Session(listener) { connected ->
                helper = connected
                requestSubmitted.set(true)
                helper.addWaypoint(target.toWaypoint(waypointIndex))
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun setDestination(target: NavigationTarget): NaviCommandStatus = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION) || !hasPermission(NAVI_SEARCH_PERMISSION)) return@withLock NaviCommandStatus.UNSUPPORTED
        runWithRetries<NaviCommandStatus>(
            NaviCommandStatus.TIMED_OUT,
            NaviCommandStatus.UNSUPPORTED,
            NaviCommandStatus.FAILED,
        ) { complete, unsupported, failed ->
            val routingCompleted = AtomicBoolean(false)
            lateinit var helper: NaviHelper
            val listener = object : NaviHelperEventListener {
                override fun onRoutingCompleted() {
                    routingCompleted.set(true)
                    try { helper.getDestinationInfo() }
                    catch (_: SecurityException) { unsupported() }
                    catch (_: Exception) { failed() }
                }

                override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                    if (routingCompleted.get()) complete(
                        if (destinationInfo.matches(target)) NaviCommandStatus.SUCCEEDED
                        else NaviCommandStatus.FAILED
                    )
                }

                override fun onError(errorCode: NaviErrorCode) = complete(NaviCommandStatus.FAILED)
            }
            Session(listener) { connected ->
                helper = connected
                helper.requestRoute(target.toRoute())
            }
        }
    }

    /**
     * NaviHelper.requestTrip previews a trip from the navigation's position without changing the route
     * (checked on PLEOS Connect v2.1: route state identical before and after). TripInfo carries no request
     * ID and is broadcast to every NaviHelper listener, so only a result whose destination point matches
     * this request is accepted.
     */
    @SuppressLint("MissingPermission")
    override suspend fun measureTrip(latitude: Double, longitude: Double): NaviRead<NaviTrip> = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION)) return@withLock NaviRead.Unsupported
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0)
            return@withLock NaviRead.Unavailable
        runWithRetries<NaviRead<NaviTrip>>(
            NaviRead.Unavailable, NaviRead.Unsupported, NaviRead.Unavailable,
        ) { complete, _, _ ->
            val listener = object : NaviHelperEventListener {
                override fun onTripInfo(tripInfo: TripInfo) {
                    val destination = tripInfo.summary?.routePoints?.firstOrNull { it.pointType == RoutePointType.DESTINATION }
                        ?: return
                    if (!sameTarget(destination.latitude, destination.longitude, latitude, longitude)) return
                    complete(
                        if (tripInfo.distance >= 0 && tripInfo.eta >= 0) NaviRead.Available(NaviTrip(tripInfo.distance, tripInfo.eta))
                        else NaviRead.Unavailable
                    )
                }

                override fun onError(errorCode: NaviErrorCode) = complete(NaviRead.Unavailable)
            }
            Session(listener) { it.requestTrip(TripRequest(latitude, longitude, "", "", "")) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun readCurrentLocation(): NaviRead<NaviPosition> = operations.withLock {
        if (!hasPermission(NAVI_ROUTE_PERMISSION)) return@withLock NaviRead.Unsupported
        runWithRetries<NaviRead<NaviPosition>>(
            NaviRead.Unavailable, NaviRead.Unsupported, NaviRead.Unavailable,
        ) { complete, _, _ ->
            val listener = object : NaviHelperEventListener {
                override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                    val lat = currentLocationInfo.latitude
                    val lon = currentLocationInfo.longitude
                    // (0, 0) is the uninitialised value, not a position.
                    complete(
                        if (lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0))
                            NaviRead.Available(NaviPosition(lat, lon))
                        else NaviRead.Unavailable
                    )
                }

                override fun onError(errorCode: NaviErrorCode) = complete(NaviRead.Unavailable)
            }
            Session(listener) { it.getCurrentLocationInfo() }
        }
    }

    private fun sameTarget(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Boolean {
        val dLat = (aLat - bLat) * 111_320.0
        val dLon = (aLon - bLon) * 111_320.0 * cos(Math.toRadians(bLat))
        return hypot(dLat, dLon) <= TRIP_TARGET_TOLERANCE_METERS
    }

    private data class Session(
        val listener: NaviHelperEventListener,
        val onConnected: (NaviHelper) -> Unit,
    )

    private sealed interface SessionAttempt<out T> {
        data class Completed<T>(val value: T) : SessionAttempt<T>
        data object Unsupported : SessionAttempt<Nothing>
        data object Failed : SessionAttempt<Nothing>
        data object TimedOut : SessionAttempt<Nothing>
    }

    private suspend fun <T> runWithRetries(
        timeoutResult: T,
        unsupportedResult: T,
        failureResult: T,
        create: ((T) -> Unit, () -> Unit, () -> Unit) -> Session,
    ): T {
        repeat(maxConnectionAttempts) { attempt ->
            when (val result = runSession(create)) {
                is SessionAttempt.Completed -> return result.value
                SessionAttempt.Unsupported -> return unsupportedResult
                SessionAttempt.Failed -> return failureResult
                SessionAttempt.TimedOut -> if (attempt + 1 < maxConnectionAttempts) delay(retryDelayMs)
            }
        }
        return timeoutResult
    }

    private suspend fun <T> runSession(
        create: ((T) -> Unit, () -> Unit, () -> Unit) -> Session,
    ): SessionAttempt<T> = withTimeoutOrNull(operationTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val helper = try { NaviHelper(appContext) } catch (_: Exception) {
                    continuation.resume(SessionAttempt.Unsupported)
                    return@suspendCancellableCoroutine
                }
                lateinit var session: Session
                fun finish(result: SessionAttempt<T>) {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { helper.removeListener(session.listener) }
                        runCatching { helper.release() }
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
                session = create(
                    { finish(SessionAttempt.Completed(it)) },
                    { finish(SessionAttempt.Unsupported) },
                    { finish(SessionAttempt.Failed) },
                )
                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { helper.removeListener(session.listener) }
                        runCatching { helper.release() }
                    }
                }
                try {
                    helper.initialize(
                        onConnected = {
                            try {
                                helper.addListener(session.listener)
                                session.onConnected(helper)
                            } catch (_: SecurityException) { finish(SessionAttempt.Unsupported) }
                            catch (_: Exception) { finish(SessionAttempt.Failed) }
                        },
                        onFailed = { finish(SessionAttempt.Unsupported) },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finish(SessionAttempt.Unsupported)
                }
            }
        } ?: SessionAttempt.TimedOut

    private fun DestinationInfo.toSnapshot(routeId: String) =
        NaviRouteSnapshot(name, latitude, longitude, remainDistance.toDouble(), routeId)

    private fun NavigationTarget.toWaypoint(index: WaypointIndex) = RequestWaypointInfo(
        latitude = latitude,
        longitude = longitude,
        poiName = name,
        waypointIndex = index,
        poiId = poiId.orEmpty(),
        address = address.orEmpty(),
        poiSubId = poiSubId.orEmpty(),
        poiProvider = poiProvider.orEmpty(),
    )

    private fun NavigationTarget.toRoute() = RouteInfo(
        latitude = latitude,
        longitude = longitude,
        poiName = name,
        poiId = poiId.orEmpty(),
        address = address.orEmpty(),
        poiSubId = poiSubId.orEmpty(),
        poiProvider = poiProvider.orEmpty(),
    )

    private fun WaypointInfo.matches(target: NavigationTarget) = NavigationObservedTarget(
        latitude, longitude, name, poiId, poiSubId, poiProvider,
    ).matches(target)

    private fun DestinationInfo.matches(target: NavigationTarget) = NavigationObservedTarget(
        latitude, longitude, name, poiId, poiSubId, poiProvider,
    ).matches(target)

    private fun hasPermission(permission: String) = appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val NAVI_SERVICE_PACKAGE = "ai.pleos.playground.service.navi"
        const val NAVI_ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
        const val NAVI_SEARCH_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
        /** The returned destination point may be snapped to a road; a station request never moves this far. */
        const val TRIP_TARGET_TOLERANCE_METERS = 500.0
    }
}

internal enum class RoutePhase { GUIDING, CALCULATING, NONE }

/**
 * `isRouting` only means a route is being calculated: during live guidance PLEOS reports
 * naviMode=ROUTE_GUIDING with isRouting=false (checked on the PLEOS emulator, 2026-11-13). An active route is
 * therefore the guiding mode; free drive (SAFE_GUIDING) or an idle navigation is confirmed no route, and a
 * calculation in progress is not yet known either way.
 */
internal fun routePhase(mode: NaviMode, isRouting: Boolean): RoutePhase = when {
    mode == NaviMode.ROUTE_GUIDING || mode == NaviMode.SIMULATION_GUIDING -> RoutePhase.GUIDING
    isRouting -> RoutePhase.CALCULATING
    else -> RoutePhase.NONE
}
