package dev.fuelpilot

import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.Screen

fun monitoringPermissionsSatisfied(location: Boolean, notification: Boolean) = location && notification
enum class BootMonitoringAction { NONE, ASK_USER_TO_RESUME }
fun bootMonitoringAction(requested: Boolean) = if (requested) BootMonitoringAction.ASK_USER_TO_RESUME else BootMonitoringAction.NONE

data class MonitoringUiState(val requested: Boolean, val running: Boolean, val message: String)
data class BackgroundAdvice(val key: String, val message: String)

/** Advisory only: background notifications never initiate navigation. */
fun backgroundAdvice(state: AssistViewState): BackgroundAdvice? {
    val presentation = state.presentation
    if (!state.ready || state.ui.motion == Motion.Unknown || presentation.availability != AssistAvailability.READY ||
        state.pending != null || state.ui.refuel != null) return null
    if (presentation.targetScreen !in setOf(Screen.Recommendation, Screen.Passing, Screen.LongDrive)) return null
    val key = presentation.recommendationKey?.takeIf { it.isNotBlank() } ?: return null
    val station = presentation.route.recommended ?: return null
    return BackgroundAdvice("$key:${presentation.targetScreen}", "${station.name} 주유 안내를 확인해 주세요. 정차 후 앱을 열어 주세요.")
}
