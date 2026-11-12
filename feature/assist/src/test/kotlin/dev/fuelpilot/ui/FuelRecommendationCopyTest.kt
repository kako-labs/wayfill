package dev.fuelpilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelRecommendationCopyTest {
    private val station = FuelStation("a", "긴 이름의\n주유소", 3.2, 2)

    @Test fun passingWarningRequiresVerifiedRangeAndNextStationDistance() {
        val route = FuelRouteUi(rangeKm = 18.0, destinationKm = 38.0, recommended = station,
            nextAfterPassing = FuelStation("b", "다음", 35.0, 1))
        assertEquals("지나치면 다음 주유소까지 연료 부족 예상", fuelRecommendationCopy(route, true).reason)
        assertEquals("목적지 전에 주유가 필요해요", fuelRecommendationCopy(route, false).reason)
        assertEquals("목적지 전에 주유가 필요해요", fuelRecommendationCopy(route.copy(nextAfterPassing = null), true).reason)
    }

    @Test fun missingOrInvalidMeasurementsNeverBecomeAZeroOrAFuelShortageClaim() {
        for (range in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val copy = fuelRecommendationCopy(FuelRouteUi(rangeKm = range, destinationKm = 95.0,
                recommended = station.copy(distanceKm = range, extraMinutes = -1)), true)
            assertEquals("현재 경로의 주유 추천", copy.reason)
            assertEquals("", copy.routeFacts)
        }
    }

    @Test fun companionAndNotificationUseNormalizedNameAndTheSameRouteFacts() {
        val copy = fuelRecommendationCopy(FuelRouteUi(75.0, 95.0, station), false)
        assertEquals("긴 이름의 주유소", copy.station)
        assertEquals("3.2km 앞 · 경로 +2분", copy.routeFacts)
        assertFalse(copy.reason.contains("마지막"))
    }

    @Test fun zeroDetourIsValidAndReachableDestinationIsNotCalledAShortage() {
        val copy = fuelRecommendationCopy(FuelRouteUi(95.0, 95.0, station.copy(distanceKm = 0.0, extraMinutes = 0)), false)
        assertEquals("현재 경로의 주유 추천", copy.reason)
        assertEquals("0km 앞 · 경로 +0분", copy.routeFacts)
    }

    @Test fun splitAndShortWindowsDoNotDependOnThePhysicalDisplaySize() {
        assertTrue(useCompactCompanion(420f, 900f))
        assertTrue(useCompactCompanion(1000f, 400f))
        assertFalse(useCompactCompanion(760f, 480f))
        assertFalse(useCompactCompanion(1682f, 1210f))
    }
}
