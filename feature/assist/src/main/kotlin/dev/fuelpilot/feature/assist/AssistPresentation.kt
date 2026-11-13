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

/** An input the decision needs. Copy names the missing one instead of always blaming the vehicle. */
enum class AssistSignal(val label: String) {
    VEHICLE_ENERGY("차량 연료"),
    LOCATION("현재 위치"),
    ROUTE("내비 경로"),
    STATIONS("주유소"),
}

data class AssistPresentation(
    val route: FuelRouteUi,
    val targetScreen: Screen?,
    val recommendationKey: String?,
    val availability: AssistAvailability,
    /** Inputs that were read but not fresh. Empty when the cause is unknown or nothing was read. */
    val blockedBy: Set<AssistSignal> = emptySet(),
)

/** Vehicle, location and route are fine; only the station source is missing. */
fun Set<AssistSignal>.onlyStationsMissing(): Boolean = this == setOf(AssistSignal.STATIONS)

fun Set<AssistSignal>.labels(): String = sortedBy { it.ordinal }.joinToString("·") { it.label }

fun AssistPresentation.statusMessage(): String? {
    if (availability == AssistAvailability.READY) return null
    if (blockedBy.onlyStationsMissing()) return when (availability) {
        AssistAvailability.STALE -> "주유소 정보를 새로 확인하고 있어요."
        else -> "차량과 경로는 확인했어요. 주유소 정보가 연결되면 안내할게요."
    }
    if (blockedBy.isEmpty()) return when (availability) {
        AssistAvailability.STALE -> "최신 차량과 경로 정보를 확인 중이에요."
        AssistAvailability.UNAVAILABLE -> "차량과 경로 정보를 확인할 수 없어요."
        AssistAvailability.UNSUPPORTED -> "이 환경에서는 주유 안내 정보를 지원하지 않아요."
        AssistAvailability.READY -> null
    }
    return when (availability) {
        AssistAvailability.STALE -> "${blockedBy.labels()} 정보를 새로 확인하고 있어요."
        else -> "${blockedBy.labels()} 정보를 확인할 수 없어요."
    }
}
