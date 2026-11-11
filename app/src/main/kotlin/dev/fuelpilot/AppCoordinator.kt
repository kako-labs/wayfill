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
class AppCoordinator(private val dependencies: AppDependencies) {
    private val engine = FuelDecisionEngine()
    private val mapper = FuelDecisionUiMapper()

    fun liveIntents(): Flow<AssistIntent> = merge(
        flow {
            while (currentCoroutineContext().isActive) {
                readIntents().forEach { emit(it) }
                delay(dependencies.refreshIntervalMs)
            }
        },
        dependencies.vehicleEvents.motion.map { AssistIntent.MotionChanged(it) },
        dependencies.vehicleEvents.refuels.map { AssistIntent.RefuelConfirmed(it) },
        dependencies.reduceMotion.map { AssistIntent.ReduceMotionChanged(it) },
    )

    suspend fun readIntents(): List<AssistIntent> = coroutineScope {
        val e = async { readOr<EnergyResult>(EnergyResult.Unavailable) { dependencies.energy.readEnergy() } }
        val l = async { readOr<LocationResult>(LocationResult.Unavailable) { dependencies.location.readLocation() } }
        val r = async { readOr<RouteResult>(RouteResult.Unavailable) { dependencies.navigation.readRoute() } }
        val s = async { readOr<StationResult>(StationResult.Unavailable) { dependencies.stations.findStations() } }
        val energy = e.await(); val location = l.await(); val route = r.await(); val stations = s.await()
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
                val input = FuelDecisionInput(rangeRemainingKm = range, route = activeRoute, stations = candidates)
                mapper.map(AssistPresentationInput(engine.evaluate(input), input, activeRoute?.id))
            }
        }
        intents += AssistIntent.PresentationChanged(presentation)
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
        val request = WaypointRequest(effect.requestId, effect.stationId)
        if (assist.state.value.pending != request) return
        coroutineScope {
            val operation = launch {
                val result = try {
                    withTimeout(dependencies.navigationTimeoutMs) { dependencies.navigation.addWaypoint(request) }
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

    private fun unavailable(value: AssistAvailability) =
        AssistPresentation(FuelRouteUi(), dev.fuelpilot.ui.Screen.Widget, null, value)
    private fun Double.validDistance() = isFinite() && this >= 0
    private suspend fun <T> readOr(fallback: T, block: suspend () -> T): T = try { withTimeout(dependencies.readTimeoutMs) { block() } }
        catch (_: TimeoutCancellationException) { fallback }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fallback }
}
