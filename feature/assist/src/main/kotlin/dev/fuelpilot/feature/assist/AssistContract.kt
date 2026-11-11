package dev.fuelpilot.feature.assist

import dev.fuelpilot.data.navigation.WaypointRequest
import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.FuelUiEvent
import dev.fuelpilot.ui.FuelUiState
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import dev.fuelpilot.ui.Screen
import dev.fuelpilot.ui.reduceFuelUi

sealed interface AssistIntent {
    data class PresentationChanged(val value: AssistPresentation) : AssistIntent
    data class RouteChanged(val routeId: String?, val destinationId: String?) : AssistIntent
    data class MotionChanged(val value: Motion) : AssistIntent
    data class RefuelConfirmed(val value: RefuelEvent) : AssistIntent
    data class SelectStation(val stationId: String) : AssistIntent
    data class NavigationCompleted(val value: WaypointResult) : AssistIntent
    data class Show(val screen: Screen) : AssistIntent
    data class CelebrationFinished(val id: String) : AssistIntent
    data class CharacterChanged(val enabled: Boolean) : AssistIntent
    data class ReduceMotionChanged(val enabled: Boolean) : AssistIntent
    data object Continue : AssistIntent
}

sealed interface AssistEffect {
    data class AddWaypoint(val requestId: String, val stationId: String) : AssistEffect
}

data class AssistViewState(
    val ready: Boolean = false,
    val ui: FuelUiState = FuelUiState(),
    val presentation: AssistPresentation = unavailablePresentation(),
    val persisted: AssistStoredState = AssistStoredState(),
    val pending: WaypointRequest? = null,
    val navigationResult: WaypointResult? = null,
    val requestedScreen: Screen? = null,
    val activeRecommendationKey: String? = null,
    val overlayVisible: Boolean = true,
)

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
        is AssistIntent.RouteChanged -> {
            if (intent.routeId != state.persisted.routeId || intent.destinationId != state.persisted.destinationId) {
                next = state.clearTransient().copy(
                    presentation = unavailablePresentation(),
                    persisted = state.persisted.clearExposure().copy(routeId = intent.routeId, destinationId = intent.destinationId),
                )
            }
        }
        is AssistIntent.MotionChanged -> {
            next = if (intent.value == Motion.Parked) state.copy(ui = state.ui.copy(motion = intent.value))
            else state.clearTransient().copy(
                ui = state.clearTransient().ui.copy(motion = intent.value),
                // A repeated initial Unknown is a snapshot, not a departure event.
                persisted = if (state.ui.motion == intent.value) state.persisted else state.persisted.clearExposure(),
            )
        }
        is AssistIntent.PresentationChanged -> {
            val incoming = intent.value
            val newWarning = incoming.recommendationKey != null &&
                (incoming.recommendationKey !in state.persisted.exposedRecommendationKeys ||
                    (incoming.targetScreen == Screen.Passing && state.presentation.targetScreen != Screen.Passing))
            next = state.copy(
                presentation = incoming,
                requestedScreen = state.requestedScreen.takeUnless { newWarning },
                activeRecommendationKey = state.activeRecommendationKey.takeIf { it == incoming.recommendationKey },
            )
        }
        is AssistIntent.RefuelConfirmed -> {
            val retained = state.persisted.pruned(wallClockMs)
            val event = intent.value
            if (event.id.isNotBlank() && event.id !in retained.processedRefuels) {
                val stored = retained.copy(processedRefuels = retained.processedRefuels + (event.id to wallClockMs)).pruned(wallClockMs)
                val ui = reduceFuelUi(state.ui.copy(seenIds = retained.processedRefuels.keys),
                    FuelUiEvent.RefuelConfirmed(event), monotonicClockMs)
                next = state.copy(
                    ui = ui.copy(refuel = ui.refuel.takeIf { ui.motion == Motion.Parked }),
                    persisted = stored,
                    presentation = unavailablePresentation(),
                    pending = null,
                    navigationResult = null,
                    requestedScreen = null,
                    activeRecommendationKey = null,
                )
            }
        }
        is AssistIntent.SelectStation -> {
            val station = state.presentation.route.recommended
            if (state.ui.motion == Motion.Parked && state.pending == null && state.ui.refuel == null &&
                state.presentation.availability == AssistAvailability.READY &&
                state.presentation.targetScreen in recommendationScreens &&
                station?.id == intent.stationId && intent.stationId.isNotBlank() && requestId.isNotBlank()
            ) {
                next = state.copy(pending = WaypointRequest(requestId, intent.stationId), navigationResult = null, requestedScreen = null)
                effects = listOf(AssistEffect.AddWaypoint(requestId, intent.stationId))
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
        is AssistIntent.CharacterChanged -> next = state.copy(
            persisted = state.persisted.copy(characterEnabled = intent.enabled),
            ui = reduceFuelUi(state.ui, FuelUiEvent.CharacterChanged(intent.enabled), monotonicClockMs),
        )
        is AssistIntent.ReduceMotionChanged -> next = state.copy(
            persisted = state.persisted.copy(reduceMotion = intent.enabled),
            ui = state.ui.copy(reduceMotion = intent.enabled, celebration = state.ui.celebration.takeUnless { intent.enabled }),
        )
        AssistIntent.Continue -> next = state.copy(
            ui = state.ui.copy(refuel = null, celebration = null),
            navigationResult = null,
            requestedScreen = null,
            activeRecommendationKey = null,
        )
    }
    return AssistTransition(resolveScreen(next), effects)
}

private val recommendationScreens = setOf(Screen.Recommendation, Screen.Passing)
private val userScreens = setOf(Screen.Analysis, Screen.Settings, Screen.LongDrive)

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
    val activelyExposed = screen in recommendationScreens && recommendation && key != null
    val stored = if (activelyExposed) state.persisted.copy(
        lastRecommendationKey = key,
        exposedRecommendationKeys = state.persisted.exposedRecommendationKeys + key!!,
    ) else state.persisted
    return state.copy(
        persisted = stored,
        activeRecommendationKey = if (activelyExposed) key else state.activeRecommendationKey,
        overlayVisible = visible,
        ui = state.ui.copy(
            screen = screen,
            celebration = state.ui.celebration.takeIf { state.ui.motion == Motion.Parked },
            seenIds = stored.processedRefuels.keys,
            pendingStation = state.pending?.stationId,
            routeStation = (result as? WaypointResult.Succeeded)?.stationId,
            error = result?.errorMessage(),
            detailsAvailable = state.ui.motion == Motion.Parked && recommendation,
            statusMessage = when (presentation.availability) {
                AssistAvailability.READY -> null
                AssistAvailability.STALE -> "최신 차량과 경로 정보를 확인 중이에요."
                AssistAvailability.UNAVAILABLE -> "차량과 경로 정보를 확인할 수 없어요."
                AssistAvailability.UNSUPPORTED -> "이 환경에서는 주유 안내 정보를 지원하지 않아요."
            },
        ),
    )
}

private fun WaypointResult.errorMessage(): String? = when (this) {
    is WaypointResult.Succeeded -> null
    is WaypointResult.Failed -> "경유지를 추가하지 못했어요. 다시 시도해 주세요."
    is WaypointResult.TimedOut -> "내비게이션 응답 시간이 초과됐어요. 다시 시도해 주세요."
    is WaypointResult.Cancelled -> "경유지 추가가 취소됐어요. 다시 시도해 주세요."
    is WaypointResult.Unsupported -> "현재 내비게이션에서 경유지 추가를 지원하지 않아요. 다시 시도할 수 있어요."
}

private fun AssistStoredState.clearExposure() = copy(lastRecommendationKey = null, exposedRecommendationKeys = emptySet())

private fun AssistViewState.clearTransient() = copy(
    pending = null, navigationResult = null, requestedScreen = null, activeRecommendationKey = null,
    ui = ui.copy(celebration = null, refuel = null, pendingStation = null, routeStation = null, error = null),
)

private fun unavailablePresentation() = AssistPresentation(FuelRouteUi(), Screen.Widget, null, AssistAvailability.UNAVAILABLE)
