package dev.fuelpilot.model

import kotlin.math.roundToInt

object RecommendationReason {
    fun create(
        basis: SelectionBasis,
        station: FuelStationCandidate,
    ): String {
        val roundedDistance = station.distanceFromCurrentKm.roundToInt()
        if (!station.businessInfoFresh && basis == SelectionBasis.NEAREST_REACHABLE) {
            return "현재 위치에서 약 ${roundedDistance}km로 가깝지만, 영업 정보는 출발 전에 확인해 주세요."
        }

        val verifiedReason = when (basis) {
            SelectionBasis.LAST_SAFE_STOP ->
                "이곳을 지나면 다음 주유소까지 연료 여유가 부족할 수 있어요."
            SelectionBasis.MINIMUM_DETOUR -> station.detourMinutes?.let {
                "현재 경로에서 ${it}분만 벗어나 들를 수 있어요."
            } ?: "현재 위치에서 약 ${roundedDistance}km 떨어진 주유소예요."
            SelectionBasis.NEAREST_REACHABLE ->
                "현재 위치에서 약 ${roundedDistance}km로, 도달 가능한 주유소 중 가장 가까워요."
        }

        return if (station.businessInfoFresh) {
            verifiedReason
        } else {
            verifiedReason.removeSuffix(".") + " 영업 정보는 출발 전에 확인해 주세요."
        }
    }
}
