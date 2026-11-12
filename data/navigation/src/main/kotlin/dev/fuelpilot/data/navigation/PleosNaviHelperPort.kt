package dev.fuelpilot.data.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
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
                if (state?.isRouting == true) {
                    val destinationInfo = destination ?: return
                    val current = currentLocation ?: return
                    if (current.routeId.isBlank()) complete(NaviRead.Unavailable)
                    else complete(NaviRead.Available(destinationInfo.toSnapshot(current.routeId)))
                }
            }
            val listener = object : NaviHelperEventListener {
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                    state = routeStateInfo
                    if (!routeStateInfo.isRouting) complete(NaviRead.Available(null))
                    else publishIfComplete()
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
    }
}
