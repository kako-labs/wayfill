package dev.fuelpilot.feature.assist

import dev.fuelpilot.data.navigation.NavigationMode
import dev.fuelpilot.data.navigation.NavigationTarget
import dev.fuelpilot.data.navigation.WaypointRequest
import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.data.poi.gateway.StationFuel
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.FuelUiEvent
import dev.fuelpilot.ui.FuelUiState
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import dev.fuelpilot.ui.Screen
import dev.fuelpilot.ui.reduceFuelUi

sealed interface AssistIntent {
    data class PresentationChanged(
        val value: AssistPresentation,
        val minimumReserveKm: Int? = null,
        val navigationTargets: Map<String, NavigationTarget> = emptyMap(),
    ) : AssistIntent
    data class RouteChanged(val routeId: String?, val destinationId: String?) : AssistIntent
    data class MotionChanged(val value: Motion) : AssistIntent
    data class RefuelConfirmed(val value: RefuelEvent) : AssistIntent
    /**
     * [recommendationKey] lets compact/HUN surfaces prove that their action still belongs to the
     * recommendation they rendered. It remains optional only for the existing parked in-app
     * caller, which is serialized with presentation updates by [AssistViewModel].
     */
    data class SelectStation(val stationId: String, val recommendationKey: String? = null) : AssistIntent
    data class NavigationCompleted(val value: WaypointResult) : AssistIntent
    data class Show(val screen: Screen) : AssistIntent
    data class CelebrationFinished(val id: String) : AssistIntent
    data class CharacterChanged(val enabled: Boolean) : AssistIntent
    data class ReserveChanged(val km: Int) : AssistIntent
    /** Parked only, like the reserve: it changes which stations can be recommended. */
    data class FuelChanged(val fuel: StationFuel) : AssistIntent
    data object PauseMonitoring : AssistIntent
    data class ReduceMotionChanged(val enabled: Boolean) : AssistIntent
    data object Continue : AssistIntent
    data object ClearRefuelHistory : AssistIntent
}

sealed interface AssistEffect {
    data class AddWaypoint(
        val requestId: String,
        val stationId: String,
        val mode: NavigationMode = NavigationMode.ADD_WAYPOINT,
        val expectedRouteId: String? = null,
        val target: NavigationTarget? = null,
    ) : AssistEffect
}

data class AssistViewState(
    val ready: Boolean = false,
    val ui: FuelUiState = FuelUiState(),
    val presentation: AssistPresentation = unavailablePresentation(),
    val navigationTargets: Map<String, NavigationTarget> = emptyMap(),
    val persisted: AssistStoredState = AssistStoredState(),
    val pending: WaypointRequest? = null,
    val navigationResult: WaypointResult? = null,
    val requestedScreen: Screen? = null,
    val activeRecommendationKey: String? = null,
    val assistVisible: Boolean = true,
    /** Session-only monotonic entrance time; never stored or reconstructed on process restart. */
    val recommendationStartedAtMs: Long? = null,
) {
    /** Compatibility for hosts that still call the assist surface an overlay. */
    val overlayVisible: Boolean get() = assistVisible
}

data class AssistTransition(val state: AssistViewState, val effects: List<AssistEffect> = emptyList())

/** Pure transition path. Time and request identity are supplied by the owner. */
fun reduceAssist(
    state: AssistViewState,
    intent: AssistIntent,
    wallClockMs: Long,
    monotonicClockMs: Long,
    requestId: String,
): AssistTransition {
    if (!state.ready) return AssistTransition(state)
    var next = state
    var effects = emptyList<AssistEffect>()
    when (intent) {
        AssistIntent.PauseMonitoring -> next = state.clearTransient().copy(
            presentation = unavailablePresentation(),
            ui = state.clearTransient().ui.copy(motion = Motion.Unknown),
        )
        is AssistIntent.RouteChanged -> {
            if (intent.routeId != state.persisted.routeId || intent.destinationId != state.persisted.destinationId) {
                next = state.clearTransient().copy(
                    presentation = unavailablePresentation(),
                    persisted = state.persisted.clearExposure().copy(routeId = intent.routeId, destinationId = intent.destinationId),
                )
            }
        }
        is AssistIntent.MotionChanged -> {
            next = when {
                intent.value == Motion.Parked -> state.copy(
                    ui = state.ui.copy(motion = intent.value),
                    // A recommendation already delivered in the moving surface must not trigger
                    // broad parked acting merely because the vehicle stopped.
                    activeRecommendationKey = state.activeRecommendationKey.takeUnless {
                        state.ui.motion == Motion.Moving
                    },
                )
                state.ui.motion == intent.value -> state.copy(ui = state.ui.copy(motion = intent.value))
                else -> state.clearTransient().copy(
                    ui = state.clearTransient().ui.copy(motion = intent.value),
                    // Do not immediately re-expose the parked advice after clearing its exposure.
                    // Only a fresh evaluation may supply an actionable moving recommendation.
                    presentation = unavailablePresentation(),
                    persisted = state.persisted.clearExposure(),
                )
            }
        }
        is AssistIntent.PresentationChanged -> {
            // An in-flight evaluation may finish after a parked policy edit.
            if (intent.minimumReserveKm != null && intent.minimumReserveKm != state.persisted.minimumReserveKm)
                return AssistTransition(state)
            val incoming = intent.value
            val newWarning = incoming.recommendationKey != null &&
                (incoming.recommendationKey !in state.persisted.exposedRecommendationKeys ||
                    (incoming.targetScreen == Screen.Passing && state.presentation.targetScreen != Screen.Passing))
            next = state.copy(
                presentation = incoming,
                navigationTargets = intent.navigationTargets.filter { (stationId, target) ->
                    stationId == target.stationId && target.isVerifiedWgs84()
                },
                requestedScreen = state.requestedScreen.takeUnless { newWarning },
                activeRecommendationKey = state.activeRecommendationKey.takeIf { it == incoming.recommendationKey },
            )
        }
        is AssistIntent.RefuelConfirmed -> {
            val retained = state.persisted.pruned(wallClockMs)
            val event = intent.value
            if (event.id.isNotBlank() && event.id !in retained.processedRefuels) {
                val stored = retained.copy(
                    processedRefuels = retained.processedRefuels + (event.id to wallClockMs),
                    refuelHistory = retained.refuelHistory + event.asRecord(wallClockMs),
                ).pruned(wallClockMs)
                val ui = reduceFuelUi(state.ui.copy(seenIds = retained.processedRefuels.keys),
                    FuelUiEvent.RefuelConfirmed(event), monotonicClockMs)
                next = state.copy(
                    ui = ui.copy(refuel = ui.refuel.takeIf { ui.motion == Motion.Parked }),
                    persisted = stored,
                    presentation = unavailablePresentation(),
                    navigationTargets = emptyMap(),
                    pending = null,
                    navigationResult = null,
                    requestedScreen = null,
                    activeRecommendationKey = null,
                )
            }
        }
        is AssistIntent.SelectStation -> {
            val station = state.presentation.route.recommended
            val recommendationKey = state.presentation.recommendationKey
            val matchesRecommendation = intent.recommendationKey == recommendationKey ||
                (state.ui.motion == Motion.Parked && intent.recommendationKey == null)
            if (state.ui.motion in actionableMotion && state.pending == null &&
                state.navigationResult !is WaypointResult.Succeeded &&
                state.ui.refuel == null && state.ui.celebration == null &&
                state.presentation.availability == AssistAvailability.READY &&
                state.presentation.targetScreen in recommendationScreens &&
                !recommendationKey.isNullOrBlank() && matchesRecommendation &&
                station?.id == intent.stationId && intent.stationId.isNotBlank() && requestId.isNotBlank()
            ) {
                val mode = if (state.persisted.routeId == null) NavigationMode.SET_DESTINATION else NavigationMode.ADD_WAYPOINT
                val target = state.navigationTargets[intent.stationId]
                val request = WaypointRequest(requestId, intent.stationId, mode, state.persisted.routeId, target)
                next = state.copy(pending = request, navigationResult = null, requestedScreen = null)
                effects = listOf(AssistEffect.AddWaypoint(requestId, intent.stationId, mode, request.expectedRouteId, target))
            }
        }
        is AssistIntent.NavigationCompleted -> {
            val result = intent.value
            if (state.pending?.requestId == result.requestId && state.pending.stationId == result.stationId) {
                next = state.copy(pending = null, navigationResult = result, requestedScreen = null)
            }
        }
        is AssistIntent.Show -> {
            if (state.ui.motion == Motion.Parked && intent.screen in userScreens) {
                next = state.copy(requestedScreen = intent.screen)
            }
        }
        is AssistIntent.CelebrationFinished -> next = state.copy(
            ui = reduceFuelUi(state.ui, FuelUiEvent.CelebrationFinished(intent.id), monotonicClockMs),
        )
        is AssistIntent.ReserveChanged -> if (state.ui.motion == Motion.Parked && validReserveKm(intent.km) && intent.km != state.persisted.minimumReserveKm) {
            next = state.clearTransient().copy(
                persisted = state.persisted.clearExposure().copy(minimumReserveKm = intent.km),
                presentation = unavailablePresentation(), requestedScreen = state.requestedScreen,
            )
        }
        is AssistIntent.FuelChanged -> if (state.ui.motion == Motion.Parked && intent.fuel in selectableFuels &&
            intent.fuel != state.persisted.fuelPreference) {
            next = state.clearTransient().copy(
                persisted = state.persisted.clearExposure().copy(fuelPreference = intent.fuel),
                presentation = unavailablePresentation(), requestedScreen = state.requestedScreen,
            )
        }
        is AssistIntent.CharacterChanged -> if (state.ui.motion == Motion.Parked) next = state.copy(
            persisted = state.persisted.copy(characterEnabled = intent.enabled),
            ui = reduceFuelUi(state.ui, FuelUiEvent.CharacterChanged(intent.enabled), monotonicClockMs),
        )
        is AssistIntent.ReduceMotionChanged -> next = state.copy(
            persisted = state.persisted.copy(reduceMotion = intent.enabled),
            ui = state.ui.copy(reduceMotion = intent.enabled, celebration = state.ui.celebration.takeUnless { intent.enabled }),
        )
        AssistIntent.ClearRefuelHistory -> if(state.ui.motion==Motion.Parked) next=state.copy(
            persisted=state.persisted.copy(refuelHistory=emptyList()),
        )
        AssistIntent.Continue -> next = state.copy(
            ui = state.ui.copy(refuel = null, celebration = null),
            navigationResult = null,
            requestedScreen = null,
            activeRecommendationKey = null,
        )
    }
    val resolved=resolveScreen(next)
    val firstParkedEntrance=resolved.ui.motion==Motion.Parked && resolved.activeRecommendationKey!=null &&
        resolved.activeRecommendationKey!=state.activeRecommendationKey &&
        resolved.activeRecommendationKey !in state.persisted.exposedRecommendationKeys
    return AssistTransition(resolved.copy(recommendationStartedAtMs=when {
        firstParkedEntrance->monotonicClockMs
        resolved.ui.motion!=Motion.Parked || resolved.activeRecommendationKey!=state.activeRecommendationKey->null
        else->state.recommendationStartedAtMs
    }), effects)
}

private val recommendationScreens = setOf(Screen.Recommendation, Screen.Passing)
private val userScreens = setOf(Screen.Analysis, Screen.Settings, Screen.LongDrive)
private val actionableMotion = setOf(Motion.Parked, Motion.Moving)

/** Motion > refuel > pending navigation > result > recommendation > monitoring/no-action > unavailable. */
private fun resolveScreen(state: AssistViewState): AssistViewState {
    val presentation = state.presentation
    val key = presentation.recommendationKey
    val recommendation = presentation.availability == AssistAvailability.READY && presentation.targetScreen in recommendationScreens
    val exposed = key != null && (state.activeRecommendationKey == key || key !in state.persisted.exposedRecommendationKeys)
    val result = state.navigationResult
    val screen = when {
        state.ui.motion != Motion.Parked -> Screen.Widget
        state.ui.refuel != null -> Screen.Refuel
        state.pending != null -> Screen.Adding
        result is WaypointResult.Succeeded -> Screen.Added
        result != null -> Screen.Recommendation
        state.requestedScreen != null -> state.requestedScreen
        recommendation && exposed -> presentation.targetScreen!!
        else -> Screen.Widget
    }
    val visible = state.ui.motion != Motion.Parked || state.ui.refuel != null || state.pending != null ||
        result != null || state.requestedScreen != null || presentation.targetScreen != null ||
        presentation.availability != AssistAvailability.READY
    val activelyExposed = recommendation && exposed && key != null && when (state.ui.motion) {
        Motion.Parked -> screen in recommendationScreens
        Motion.Moving -> screen == Screen.Widget
        Motion.Unknown -> false
    }
    val stored = if (activelyExposed) state.persisted.copy(
        lastRecommendationKey = key,
        exposedRecommendationKeys = state.persisted.exposedRecommendationKeys + key!!,
    ) else state.persisted
    return state.copy(
        persisted = stored,
        activeRecommendationKey = if (activelyExposed) key else state.activeRecommendationKey,
        assistVisible = visible,
        ui = state.ui.copy(
            screen = screen,
            celebration = state.ui.celebration.takeIf { state.ui.motion == Motion.Parked },
            seenIds = stored.processedRefuels.keys,
            pendingStation = state.pending?.stationId,
            routeStation = (result as? WaypointResult.Succeeded)?.stationId,
            error = result?.errorMessage(),
            detailsAvailable = state.ui.motion == Motion.Parked && recommendation,
            statusMessage = presentation.statusMessage(),
        ),
    )
}

private fun WaypointResult.errorMessage(): String? = when (this) {
    is WaypointResult.Succeeded -> null
    is WaypointResult.Failed -> "경유지를 추가하지 못했어요. 다시 시도해 주세요."
    is WaypointResult.TimedOut -> "내비게이션 응답 시간이 초과됐어요. 다시 시도해 주세요."
    is WaypointResult.Cancelled -> "경유지 추가가 취소됐어요. 다시 시도해 주세요."
    is WaypointResult.Unsupported -> "현재 내비게이션에서 경유지 추가를 지원하지 않아요. 다시 시도할 수 있어요."
    is WaypointResult.CapacityReached -> "내비게이션에 더 추가할 수 있는 경유지가 없어요. 기존 경유지를 확인해 주세요."
}

private fun AssistStoredState.clearExposure() = copy(lastRecommendationKey = null, exposedRecommendationKeys = emptySet())

private fun AssistViewState.clearTransient() = copy(
    recommendationStartedAtMs = null,
    navigationTargets = emptyMap(),
    pending = null, navigationResult = null, requestedScreen = null, activeRecommendationKey = null,
    ui = ui.copy(celebration = null, refuel = null, pendingStation = null, routeStation = null, error = null),
)

private fun unavailablePresentation() = AssistPresentation(FuelRouteUi(), Screen.Widget, null, AssistAvailability.UNAVAILABLE)
