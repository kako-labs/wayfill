package dev.fuelpilot.feature.assist

import dev.fuelpilot.model.FuelDecision
import dev.fuelpilot.model.FuelDecisionInput
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.Screen

enum class AssistAvailability {
    READY,
    STALE,
    UNAVAILABLE,
    UNSUPPORTED,
}

data class AssistPresentationInput(
    val decision: FuelDecision,
    val evaluatedInput: FuelDecisionInput,
    val routeSnapshotId: String?,
)

data class AssistPresentation(
    val route: FuelRouteUi,
    val targetScreen: Screen?,
    val recommendationKey: String?,
    val availability: AssistAvailability,
)
