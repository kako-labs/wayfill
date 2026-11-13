package dev.fuelpilot.model

enum class SignalStatus { FRESH, STALE, UNAVAILABLE, UNSUPPORTED }

enum class OpenStatus { OPEN, CLOSED, UNKNOWN }

enum class RecommendationMode { LAST_SAFE_STOP, NEARBY }

enum class RecommendationStage { RECOMMEND, PASSING }

enum class SelectionBasis { LAST_SAFE_STOP, MINIMUM_DETOUR, NEAREST_REACHABLE }

enum class UnavailableReason {
    STALE_ENERGY,
    ENERGY_UNAVAILABLE,
    ENERGY_UNSUPPORTED,
    STALE_LOCATION,
    LOCATION_UNAVAILABLE,
    LOCATION_UNSUPPORTED,
    STALE_ROUTE,
    ROUTE_UNAVAILABLE,
    ROUTE_UNSUPPORTED,
    NO_SAFE_STATION,
    NO_REACHABLE_STATION,
}

data class ActiveRoute(
    val id: String,
    val destinationName: String,
    val remainingDistanceKm: Double,
)

/** Source proof carried with a WGS84 station coordinate; arbitrary coordinates are not navigation-ready. */
enum class NavigationTargetProvenance {
    VERIFIED_PROVIDER_WGS84,
    VERIFIED_ROAD_METRICS_WGS84,
}

data class VerifiedStationNavigationTarget(
    val stationId: String,
    val latitude: Double,
    val longitude: Double,
    val provenance: NavigationTargetProvenance,
    val poiId: String? = null,
    val address: String? = null,
    val poiSubId: String? = null,
    val poiProvider: String? = null,
) {
    fun isValid(): Boolean = stationId.isNotBlank() && latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        listOf(poiId, address, poiSubId, poiProvider).all { it == null || it.isNotBlank() }
}

data class FuelStationCandidate(
    val id: String,
    val name: String,
    /** Verified road distance to the station, including the approach/detour. */
    val distanceFromCurrentKm: Double,
    /** Progress along the active route; used for ordering and alert timing, not fuel reachability. */
    val distanceAlongRouteKm: Double? = null,
    val etaMinutes: Int? = null,
    val detourMinutes: Int? = null,
    val openStatus: OpenStatus,
    val fuelCompatible: Boolean,
    val businessInfoFresh: Boolean,
    /** Null means the station may be shown, but navigation must fail closed as Unsupported. */
    val navigationTarget: VerifiedStationNavigationTarget? = null,
) {
    val effectiveOpenStatus: OpenStatus
        get() = if (businessInfoFresh) openStatus else OpenStatus.UNKNOWN
}

data class AlertPolicy(
    val leadDistanceKm: Double = 20.0,
    val leadMinutes: Int = 15,
    val passingDistanceKm: Double = 5.0,
    val passingMinutes: Int = 5,
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
        val basis: SelectionBasis,
        val primary: FuelStationCandidate,
        val alternatives: List<FuelStationCandidate>,
        val predictedArrivalRangeKm: Double? = null,
        val rangeAfterStopKm: Double,
        val nextAfterPrimary: FuelStationCandidate? = null,
        val rangeAfterNextKm: Double? = null,
        val stage: RecommendationStage = RecommendationStage.RECOMMEND,
    ) : FuelDecision

    data class Unavailable(val reason: UnavailableReason) : FuelDecision
}
