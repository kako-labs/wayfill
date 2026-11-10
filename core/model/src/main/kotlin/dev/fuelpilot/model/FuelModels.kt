package dev.fuelpilot.model

enum class SignalStatus { FRESH, STALE, UNAVAILABLE }

enum class OpenStatus { OPEN, CLOSED, UNKNOWN }

enum class RecommendationMode { LAST_SAFE_STOP, NEARBY }

enum class UnavailableReason {
    STALE_ENERGY,
    ENERGY_UNAVAILABLE,
    STALE_LOCATION,
    LOCATION_UNAVAILABLE,
    STALE_ROUTE,
    ROUTE_UNAVAILABLE,
    NO_SAFE_STATION,
    NO_REACHABLE_STATION,
}

data class ActiveRoute(
    val id: String,
    val destinationName: String,
    val remainingDistanceKm: Double,
)

data class FuelStationCandidate(
    val id: String,
    val name: String,
    val distanceFromCurrentKm: Double,
    val distanceAlongRouteKm: Double? = null,
    val etaMinutes: Int? = null,
    val openStatus: OpenStatus,
    val fuelCompatible: Boolean,
    val businessInfoFresh: Boolean,
) {
    val effectiveOpenStatus: OpenStatus
        get() = if (businessInfoFresh) openStatus else OpenStatus.UNKNOWN
}

data class AlertPolicy(
    val leadDistanceKm: Double = 20.0,
    val leadMinutes: Int = 15,
)

data class FuelDecisionInput(
    val rangeRemainingKm: Double,
    val minimumReserveKm: Double = 50.0,
    val route: ActiveRoute?,
    val stations: List<FuelStationCandidate>,
    val energyStatus: SignalStatus = SignalStatus.FRESH,
    val locationStatus: SignalStatus = SignalStatus.FRESH,
    val routeStatus: SignalStatus = SignalStatus.FRESH,
    val alertPolicy: AlertPolicy = AlertPolicy(),
)

sealed interface FuelDecision {
    data class NoAction(val predictedArrivalRangeKm: Double) : FuelDecision

    data class Monitoring(
        val predictedArrivalRangeKm: Double,
        val lastSafeStation: FuelStationCandidate,
    ) : FuelDecision

    data class Recommendation(
        val mode: RecommendationMode,
        val primary: FuelStationCandidate,
        val alternatives: List<FuelStationCandidate>,
        val predictedArrivalRangeKm: Double? = null,
        val rangeAfterStopKm: Double,
    ) : FuelDecision

    data class Unavailable(val reason: UnavailableReason) : FuelDecision
}
