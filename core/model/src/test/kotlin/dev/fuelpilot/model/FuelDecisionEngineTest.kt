package dev.fuelpilot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelDecisionEngineTest {
    private val engine = FuelDecisionEngine()

    @Test
    fun `returns no action when destination arrival keeps the requested reserve`() {
        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 180.0,
                minimumReserveKm = 50.0,
                route = ActiveRoute("route-1", "부산역", 100.0),
                stations = emptyList(),
            ),
        )

        assertEquals(FuelDecision.NoAction(predictedArrivalRangeKm = 80.0), result)
    }

    @Test
    fun `recommends the current station as last safe stop when the next one is unsafe`() {
        val current = station(
            id = "a",
            name = "한강주유소",
            distanceKm = 18.0,
            routeDistanceKm = 18.0,
            openStatus = OpenStatus.OPEN,
        )
        val unsafeNext = station(
            id = "b",
            name = "산마루주유소",
            distanceKm = 82.0,
            routeDistanceKm = 82.0,
            openStatus = OpenStatus.OPEN,
        )

        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 120.0,
                minimumReserveKm = 50.0,
                route = ActiveRoute("route-2", "강릉역", 100.0),
                stations = listOf(current, unsafeNext),
            ),
        )

        assertTrue(result is FuelDecision.Recommendation)
        result as FuelDecision.Recommendation
        assertEquals(RecommendationMode.LAST_SAFE_STOP, result.mode)
        assertEquals(current, result.primary)
        assertEquals(102.0, result.rangeAfterStopKm, 0.001)
        assertEquals(20.0, result.predictedArrivalRangeKm!!, 0.001)
    }

    @Test
    fun `without a destination recommends the nearest open compatible reachable station`() {
        val nearestUnknown = station("unknown", "정보확인주유소", 2.0, openStatus = OpenStatus.UNKNOWN)
        val nearestOpen = station("open", "열린주유소", 4.0, openStatus = OpenStatus.OPEN)
        val closed = station("closed", "닫힌주유소", 1.0, openStatus = OpenStatus.CLOSED)
        val incompatible = station("lpg", "LPG충전소", 0.5, openStatus = OpenStatus.OPEN, compatible = false)

        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 30.0,
                minimumReserveKm = 50.0,
                route = null,
                stations = listOf(nearestUnknown, nearestOpen, closed, incompatible),
            ),
        )

        assertTrue(result is FuelDecision.Recommendation)
        result as FuelDecision.Recommendation
        assertEquals(RecommendationMode.NEARBY, result.mode)
        assertEquals(nearestOpen, result.primary)
        assertEquals(listOf(nearestUnknown), result.alternatives)
    }

    @Test
    fun `stale energy data never produces a recommendation`() {
        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 40.0,
                route = null,
                energyStatus = SignalStatus.STALE,
                stations = listOf(station("a", "가까운주유소", 2.0, openStatus = OpenStatus.OPEN)),
            ),
        )

        assertEquals(FuelDecision.Unavailable(UnavailableReason.STALE_ENERGY), result)
    }

    @Test
    fun `distance only route candidate can trigger inside the alert distance`() {
        val candidate = station(
            id = "distance-only",
            name = "거리기준주유소",
            distanceKm = 15.0,
            routeDistanceKm = 15.0,
            etaMinutes = null,
            openStatus = OpenStatus.OPEN,
        )

        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 100.0,
                minimumReserveKm = 50.0,
                route = ActiveRoute("route-3", "속초", 80.0),
                stations = listOf(candidate),
            ),
        )

        assertTrue(result is FuelDecision.Recommendation)
        assertEquals(candidate, (result as FuelDecision.Recommendation).primary)
    }

    @Test
    fun `returns unavailable when nearby mode has no reachable compatible station`() {
        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 10.0,
                route = null,
                stations = listOf(
                    station("far", "먼주유소", 12.0, openStatus = OpenStatus.OPEN),
                    station("closed", "닫힌주유소", 2.0, openStatus = OpenStatus.CLOSED),
                ),
            ),
        )

        assertEquals(FuelDecision.Unavailable(UnavailableReason.NO_REACHABLE_STATION), result)
    }

    private fun station(
        id: String,
        name: String,
        distanceKm: Double,
        routeDistanceKm: Double? = null,
        etaMinutes: Int? = null,
        openStatus: OpenStatus,
        compatible: Boolean = true,
    ) = FuelStationCandidate(
        id = id,
        name = name,
        distanceFromCurrentKm = distanceKm,
        distanceAlongRouteKm = routeDistanceKm,
        etaMinutes = etaMinutes,
        openStatus = openStatus,
        fuelCompatible = compatible,
        businessInfoFresh = true,
    )
}
