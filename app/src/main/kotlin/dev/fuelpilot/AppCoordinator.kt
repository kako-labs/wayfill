package dev.fuelpilot

import dev.fuelpilot.data.energy.*
import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.model.*
import dev.fuelpilot.ui.FuelRouteUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** App-level orchestration. No SDK calls or test fixtures are hidden in this class. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppCoordinator(private val dependencies: AppDependencies) {
    private val engine = FuelDecisionEngine()
    private val mapper = FuelDecisionUiMapper()

    fun liveIntents(): Flow<AssistIntent> = channelFlow {
        val motionRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        launch {
            combine(merge(flow {
                while (currentCoroutineContext().isActive) { emit(Unit); delay(dependencies.refreshIntervalMs) }
            }, dependencies.refreshRequests, motionRefresh), dependencies.store.observeMinimumReserveKm()) { _, reserve -> reserve }
                .transformLatest { reserve -> readIntents(reserve).forEach { emit(it) } }
                .collect { send(it) }
        }
        launch {
            dependencies.vehicleEvents.motion.distinctUntilChanged().collect { motion ->
                // Invalidate the old surface first, then refresh its replacement without waiting for the timer.
                send(AssistIntent.MotionChanged(motion))
                motionRefresh.emit(Unit)
            }
        }
        launch {
            dependencies.vehicleEvents.refuels.distinctUntilChangedBy { it.id }.collect {
                // Clear the pre-refuel recommendation before collecting a fresh energy/route snapshot.
                send(AssistIntent.RefuelConfirmed(it))
                motionRefresh.emit(Unit)
            }
        }
        launch { dependencies.reduceMotion.collect { send(AssistIntent.ReduceMotionChanged(it)) } }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        // A failed vehicle subscription cannot leave a last Parked snapshot actionable.
        emit(AssistIntent.MotionChanged(dev.fuelpilot.ui.Motion.Unknown))
        emit(AssistIntent.PresentationChanged(unavailable(AssistAvailability.UNAVAILABLE)))
    }

    suspend fun readIntents(minimumReserveKm: Int? = null): List<AssistIntent> = coroutineScope {
        val reserve = (minimumReserveKm ?: dependencies.store.read().minimumReserveKm).takeIf(::validReserveKm) ?: 50
        val e = async { readOr<EnergyResult>(EnergyResult.Unavailable) { dependencies.energy.readEnergy() } }
        val l = async { readOr<LocationResult>(LocationResult.Unavailable) { dependencies.location.readLocation() } }
        val r = async { readOr<RouteResult>(RouteResult.Unavailable) { dependencies.navigation.readRoute() } }
        val energy = e.await(); val location = l.await(); val route = r.await()
        val stations = readOr<StationResult>(StationResult.Unavailable) {
            val contextual = dependencies.stations as? ContextualStationRepository
            if (contextual == null) dependencies.stations.findStations()
            else if (location is LocationResult.Available && route is RouteResult.Available)
                contextual.findStationsFor(location.location, route.route)
            else StationResult.Unavailable
        }
        val intents = mutableListOf<AssistIntent>()
        // Available(null) alone means confirmed no active destination. Unsupported is not nearby mode.
        if (route is RouteResult.Available) intents += AssistIntent.RouteChanged(route.route?.id, route.route?.id)
        val signals = listOf(
            when (energy) { is EnergyResult.Available -> SignalStatus.FRESH; is EnergyResult.Stale -> SignalStatus.STALE; EnergyResult.Unsupported -> SignalStatus.UNSUPPORTED; else -> SignalStatus.UNAVAILABLE },
            when (location) { is LocationResult.Available -> SignalStatus.FRESH; is LocationResult.Stale -> SignalStatus.STALE; LocationResult.Unsupported -> SignalStatus.UNSUPPORTED; else -> SignalStatus.UNAVAILABLE },
            when (route) { is RouteResult.Available -> SignalStatus.FRESH; is RouteResult.Stale -> SignalStatus.STALE; RouteResult.Unsupported -> SignalStatus.UNSUPPORTED; else -> SignalStatus.UNAVAILABLE },
            when (stations) { is StationResult.Available -> SignalStatus.FRESH; is StationResult.Stale -> SignalStatus.STALE; StationResult.Unsupported -> SignalStatus.UNSUPPORTED; else -> SignalStatus.UNAVAILABLE },
        )
        val unavailable = when {
            SignalStatus.UNSUPPORTED in signals -> AssistAvailability.UNSUPPORTED
            SignalStatus.UNAVAILABLE in signals -> AssistAvailability.UNAVAILABLE
            SignalStatus.STALE in signals -> AssistAvailability.STALE
            else -> null
        }
        var navigationTargets = emptyMap<String, NavigationTarget>()
        val presentation = if (unavailable != null) unavailable(unavailable) else {
            val range = (energy as EnergyResult.Available).rangeRemainingKm
            val activeRoute = (route as RouteResult.Available).route
            val candidates = (stations as StationResult.Available).stations
            val position = (location as LocationResult.Available).location
            if (!range.validDistance() || activeRoute?.remainingDistanceKm?.validDistance() == false ||
                !position.latitude.isFinite() || position.latitude !in -90.0..90.0 ||
                !position.longitude.isFinite() || position.longitude !in -180.0..180.0 ||
                candidates.any { !it.distanceFromCurrentKm.validDistance() || it.distanceAlongRouteKm?.validDistance() == false }
            ) unavailable(AssistAvailability.UNAVAILABLE)
            else {
                val input = FuelDecisionInput(rangeRemainingKm = range, minimumReserveKm = reserve.toDouble(), route = activeRoute, stations = candidates)
                navigationTargets = candidates.mapNotNull { candidate ->
                    candidate.toNavigationTarget()?.let { candidate.id to it }
                }.toMap()
                mapper.map(AssistPresentationInput(engine.evaluate(input), input, activeRoute?.id))
            }
        }
        intents += AssistIntent.PresentationChanged(presentation, minimumReserveKm = reserve, navigationTargets = navigationTargets)
        intents
    }

    /** Bound to the host ViewModel scope, so rotation does not restart or lose a request. */
    fun connectNavigation(assist: AssistViewModel, scope: CoroutineScope): Job = scope.launch {
        assist.effects.collect { effect ->
            when (effect) {
                is AssistEffect.AddWaypoint -> launch { deliver(assist, effect) }
            }
        }
    }

    private suspend fun deliver(assist: AssistViewModel, effect: AssistEffect.AddWaypoint) {
        val request = WaypointRequest(effect.requestId, effect.stationId, effect.mode, effect.expectedRouteId, effect.target)
        if (assist.state.value.pending != request) return
        coroutineScope {
            val operation = launch {
                val result = try {
                    withTimeout(dependencies.navigationTimeoutMs) { navigate(request) }
                } catch (_: TimeoutCancellationException) {
                    WaypointResult.TimedOut(request.requestId, request.stationId)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { WaypointResult.Failed(request.requestId, request.stationId) }
                assist.dispatch(AssistIntent.NavigationCompleted(result))
            }
            try { assist.state.first { it.pending != request } }
            finally { operation.cancelAndJoin() }
        }
    }

    private suspend fun navigate(request: WaypointRequest): WaypointResult {
        val target = request.target
        if (target == null || target.stationId != request.stationId || !target.isVerifiedWgs84())
            return WaypointResult.Unsupported(request.requestId, request.stationId)
        val capabilities = dependencies.navigation.capabilities()
        if (request.mode == NavigationMode.SET_DESTINATION) {
            if (capabilities.activeRouteId != null) return WaypointResult.Cancelled(request.requestId, request.stationId)
            return if (capabilities.supportsDestination) dependencies.navigation.setDestination(request)
                else WaypointResult.Unsupported(request.requestId, request.stationId)
        }
        val max = capabilities.maxWaypoints
        val current = capabilities.currentWaypoints
        if (!capabilities.supportsWaypoint || max == null || current == null || max < 0 || current < 0)
            return WaypointResult.Unsupported(request.requestId, request.stationId)
        if (current >= max) return WaypointResult.CapacityReached(request.requestId, request.stationId)
        if (request.expectedRouteId == null || capabilities.activeRouteId != request.expectedRouteId)
            return WaypointResult.Cancelled(request.requestId, request.stationId)
        return dependencies.navigation.addWaypoint(request)
    }

    private fun unavailable(value: AssistAvailability) =
        AssistPresentation(FuelRouteUi(), dev.fuelpilot.ui.Screen.Widget, null, value)
    private fun FuelStationCandidate.toNavigationTarget(): NavigationTarget? {
        val target = navigationTarget ?: return null
        if (target.stationId != id || !target.isValid()) return null
        return NavigationTarget(
            stationId = id,
            latitude = target.latitude,
            longitude = target.longitude,
            name = name,
            provenance = target.provenance,
            poiId = target.poiId,
            address = target.address,
            poiSubId = target.poiSubId,
            poiProvider = target.poiProvider,
        )
    }
    private fun Double.validDistance() = isFinite() && this >= 0
    private suspend fun <T> readOr(fallback: T, block: suspend () -> T): T = try { withTimeout(dependencies.readTimeoutMs) { block() } }
        catch (_: TimeoutCancellationException) { fallback }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fallback }
}
