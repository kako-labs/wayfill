package dev.fuelpilot

import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistViewState
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.Screen
import dev.fuelpilot.ui.fuelRecommendationCopy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun monitoringSnapshots(states: Flow<AssistViewState>, visible: Flow<Boolean>) =
    combine(states, visible) { state, uiVisible -> state to uiVisible }

fun monitoringPermissionsSatisfied(location: Boolean, notification: Boolean) = location && notification

enum class BootMonitoringAction { NONE, ASK_USER_TO_RESUME }

fun bootMonitoringAction(requested: Boolean) =
    if (requested) BootMonitoringAction.ASK_USER_TO_RESUME else BootMonitoringAction.NONE

data class MonitoringUiState(val requested: Boolean, val running: Boolean, val message: String)

data class BackgroundAdvice(
    val key: String,
    val stationId: String,
    val stationName: String,
    val reason: String = "현재 경로의 주유 추천",
    val routeFacts: String = "",
)

enum class RecommendationActionDecision { DISPATCH, REJECT }

internal const val RECOMMENDATION_HUN_TIMEOUT_MS = 15_000L
const val RECOMMENDATION_COOLDOWN_MS = 15 * 60 * 1_000L
private const val MAX_RECOMMENDATION_IDENTITY_LENGTH = 512
private val actionableRecommendationScreens = setOf(Screen.Recommendation, Screen.Passing)
private val actionableRecommendationMotion = setOf(Motion.Parked, Motion.Moving)

fun monitoringStartAllowed(motion: Motion): Boolean = motion == Motion.Parked

/** A HUN is offered only when the reducer can accept SelectStation at this instant. */
fun backgroundAdvice(state: AssistViewState): BackgroundAdvice? {
    if (!state.ready || state.ui.motion !in actionableRecommendationMotion || state.pending != null ||
        state.navigationResult is WaypointResult.Succeeded || state.ui.refuel != null || state.ui.celebration != null) return null
    val presentation = state.presentation
    if (presentation.availability != AssistAvailability.READY ||
        presentation.targetScreen !in actionableRecommendationScreens) return null
    val key = presentation.recommendationKey?.takeIf(::validRecommendationIdentity) ?: return null
    // A companion dismissal must not return as an alert just because the app was hidden.
    if (key in state.persisted.exposedRecommendationKeys && state.activeRecommendationKey != key) return null
    val station = presentation.route.recommended ?: return null
    if (!validRecommendationIdentity(station.id)) return null
    val name = station.name.notificationText().takeIf { it.isNotBlank() } ?: return null
    val copy = fuelRecommendationCopy(presentation.route, presentation.targetScreen == Screen.Passing)
    return BackgroundAdvice(key, station.id, name, copy.reason, copy.routeFacts)
}

/** Revalidates immutable notification identity against the current shared runtime snapshot. */
fun recommendationActionDecision(
    state: AssistViewState,
    expectedKey: String,
    expectedStationId: String,
): RecommendationActionDecision {
    if (!validRecommendationIdentity(expectedKey) || !validRecommendationIdentity(expectedStationId)) {
        return RecommendationActionDecision.REJECT
    }
    val current = backgroundAdvice(state) ?: return RecommendationActionDecision.REJECT
    return if (current.key == expectedKey && current.stationId == expectedStationId) {
        RecommendationActionDecision.DISPATCH
    } else {
        RecommendationActionDecision.REJECT
    }
}

/** Wall-clock rollback is fail-closed so a restored preference cannot cause an alert storm. */
fun recommendationCooldownElapsed(
    lastHandledAtMs: Long?,
    nowMs: Long,
    cooldownMs: Long = RECOMMENDATION_COOLDOWN_MS,
): Boolean {
    if (nowMs < 0 || cooldownMs < 0) return false
    if (lastHandledAtMs == null) return true
    if (lastHandledAtMs < 0) return false
    return nowMs >= lastHandledAtMs && nowMs - lastHandledAtMs >= cooldownMs
}

internal fun validRecommendationIdentity(value: String): Boolean =
    value.isNotBlank() && value.length <= MAX_RECOMMENDATION_IDENTITY_LENGTH && value.none(Char::isISOControl)

private fun String.notificationText(): String =
    replace(Regex("\\s+"), " ").trim().take(80)
