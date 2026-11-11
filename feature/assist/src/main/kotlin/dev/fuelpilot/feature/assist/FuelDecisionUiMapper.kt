package dev.fuelpilot.feature.assist

import dev.fuelpilot.model.FuelDecision
import dev.fuelpilot.model.FuelStationCandidate
import dev.fuelpilot.model.RecommendationReason
import dev.fuelpilot.model.RecommendationStage
import dev.fuelpilot.model.UnavailableReason
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.FuelStation
import dev.fuelpilot.ui.Screen

class FuelDecisionUiMapper {
    fun map(input: AssistPresentationInput): AssistPresentation {
        val decision = input.decision
        val evaluatedInput = input.evaluatedInput
        val availableRoute = FuelRouteUi(
            rangeKm = evaluatedInput.rangeRemainingKm,
            destinationKm = evaluatedInput.route?.remainingDistanceKm,
        )

        return when (decision) {
            is FuelDecision.NoAction -> AssistPresentation(
                route = availableRoute,
                targetScreen = null,
                recommendationKey = null,
                availability = AssistAvailability.READY,
            )

            is FuelDecision.Monitoring -> {
                val lastSafeStation = decision.lastSafeStation.toUi()
                AssistPresentation(
                    route = availableRoute.copy(
                        recommended = lastSafeStation,
                        stations = listOf(lastSafeStation),
                    ),
                    targetScreen = Screen.Widget,
                    recommendationKey = null,
                    availability = AssistAvailability.READY,
                )
            }

            is FuelDecision.Recommendation -> {
                val primary = decision.primary.toUi(decision.rangeAfterStopKm)
                AssistPresentation(
                    route = availableRoute.copy(
                        recommended = primary,
                        nextAfterPassing = decision.nextAfterPrimary?.toUi(decision.rangeAfterNextKm),
                        stations = listOf(primary) + decision.alternatives.map { it.toUi() },
                        recommendationReason = RecommendationReason.create(decision.basis, decision.primary),
                    ),
                    targetScreen = when (decision.stage) {
                        RecommendationStage.RECOMMEND -> Screen.Recommendation
                        RecommendationStage.PASSING -> Screen.Passing
                    },
                    recommendationKey = recommendationKey(input.routeSnapshotId, decision),
                    availability = AssistAvailability.READY,
                )
            }

            is FuelDecision.Unavailable -> AssistPresentation(
                route = FuelRouteUi(),
                targetScreen = Screen.Widget,
                recommendationKey = null,
                availability = decision.reason.toAvailability(),
            )
        }
    }

    private fun FuelStationCandidate.toUi(remainingAtStationKm: Double? = null) = FuelStation(
        id = id,
        name = name,
        distanceKm = distanceFromCurrentKm,
        extraMinutes = detourMinutes,
        remainingAtStationKm = remainingAtStationKm,
    )

    /** Length-prefixing keeps arbitrary route and station IDs from colliding at separators. */
    private fun recommendationKey(
        routeSnapshotId: String?,
        decision: FuelDecision.Recommendation,
    ): String = listOf(
        routeSnapshotId.orEmpty(),
        decision.mode.name,
        decision.primary.id,
    ).joinToString(separator = "|") { value -> "${value.length}:$value" }

    private fun UnavailableReason.toAvailability() = when (this) {
        UnavailableReason.STALE_ENERGY,
        UnavailableReason.STALE_LOCATION,
        UnavailableReason.STALE_ROUTE,
        -> AssistAvailability.STALE

        UnavailableReason.ENERGY_UNSUPPORTED,
        UnavailableReason.LOCATION_UNSUPPORTED,
        UnavailableReason.ROUTE_UNSUPPORTED,
        -> AssistAvailability.UNSUPPORTED

        UnavailableReason.ENERGY_UNAVAILABLE,
        UnavailableReason.LOCATION_UNAVAILABLE,
        UnavailableReason.ROUTE_UNAVAILABLE,
        UnavailableReason.NO_SAFE_STATION,
        UnavailableReason.NO_REACHABLE_STATION,
        -> AssistAvailability.UNAVAILABLE
    }
}
