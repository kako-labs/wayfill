package dev.fuelpilot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecommendationReasonTest {
    @Test
    fun `explains the last safe fuel opportunity in plain language`() {
        val reason = RecommendationReason.create(
            basis = SelectionBasis.LAST_SAFE_STOP,
            station = station(distanceKm = 18.0),
        )

        assertEquals("이곳을 지나면 다음 주유소까지 연료 여유가 부족할 수 있어요.", reason)
    }

    @Test
    fun `explains a verified minimum detour in minutes`() {
        val reason = RecommendationReason.create(
            basis = SelectionBasis.MINIMUM_DETOUR,
            station = station(distanceKm = 8.0, detourMinutes = 3),
        )

        assertEquals("현재 경로에서 3분만 벗어나 들를 수 있어요.", reason)
    }

    @Test
    fun `explains the nearest reachable station without route language`() {
        val reason = RecommendationReason.create(
            basis = SelectionBasis.NEAREST_REACHABLE,
            station = station(distanceKm = 2.2),
        )

        assertEquals("현재 위치에서 약 2km로, 도달 가능한 주유소 중 가장 가까워요.", reason)
        assertFalse(reason.contains("경로"))
        assertFalse(reason.contains("우회"))
    }

    @Test
    fun `adds a check warning when business information is stale`() {
        val reason = RecommendationReason.create(
            basis = SelectionBasis.NEAREST_REACHABLE,
            station = station(distanceKm = 2.2, businessInfoFresh = false),
        )

        assertEquals("현재 위치에서 약 2km로 가깝지만, 영업 정보는 출발 전에 확인해 주세요.", reason)
    }

    private fun station(
        distanceKm: Double,
        detourMinutes: Int? = null,
        businessInfoFresh: Boolean = true,
    ) = FuelStationCandidate(
        id = "station",
        name = "안심주유소",
        distanceFromCurrentKm = distanceKm,
        detourMinutes = detourMinutes,
        openStatus = OpenStatus.OPEN,
        fuelCompatible = true,
        businessInfoFresh = businessInfoFresh,
    )
}
