package dev.fuelpilot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelDecisionEngineTest {
    private val engine = FuelDecisionEngine()

    @Test fun routeReserveIncludesTheActualRoadDistanceToTheStation() {
        for (roadDistance in listOf(35.0, 85.0)) {
            val candidate = station("detour", "Detour", roadDistance, 20.0, openStatus = OpenStatus.OPEN)
            val input = FuelDecisionInput(80.0, 50.0, ActiveRoute("route", "destination", 100.0), listOf(candidate))
            assertEquals(FuelDecision.Unavailable(UnavailableReason.NO_SAFE_STATION), engine.evaluate(input))
        }
    }

    @Test fun routeStopAndNextRemainingRangeIncludeTheirDetours() {
        val primary = station("a", "A", 25.0, 20.0, openStatus = OpenStatus.OPEN)
        val next = station("b", "B", 90.0, 60.0, openStatus = OpenStatus.OPEN)
        val result = engine.evaluate(FuelDecisionInput(80.0, 50.0,
            ActiveRoute("route", "destination", 100.0), listOf(primary, next))) as FuelDecision.Recommendation
        assertEquals(55.0, result.rangeAfterStopKm, 0.0)
        assertEquals(-10.0, result.rangeAfterNextKm!!, 0.0)
    }

    @Test
    fun recommendationIncludesFirstRouteCandidateAfterLastSafeStop() {
        val lastSafe = station(
            id = "last-safe",
            name = "마지막안심주유소",
            distanceKm = 20.0,
            routeDistanceKm = 20.0,
            openStatus = OpenStatus.OPEN,
        )
        val unsafeNext = station(
            id = "unsafe-next",
            name = "다음주유소",
            distanceKm = 110.0,
            routeDistanceKm = 110.0,
            openStatus = OpenStatus.OPEN,
        )

        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 100.0,
                minimumReserveKm = 50.0,
                route = ActiveRoute("route-passing", "목적지", 130.0),
                stations = listOf(lastSafe, unsafeNext),
            ),
        ) as FuelDecision.Recommendation

        assertEquals(lastSafe, result.primary)
        assertEquals(unsafeNext, result.nextAfterPrimary)
        assertEquals(-10.0, result.rangeAfterNextKm!!, 0.001)
    }

    @Test
    fun unsafeNextCandidateIsNeverPromotedToPrimary() {
        val lastSafe = station(
            id = "last-safe",
            name = "마지막안심주유소",
            distanceKm = 15.0,
            routeDistanceKm = 15.0,
            openStatus = OpenStatus.OPEN,
        )
        val unsafeNext = station(
            id = "unsafe-next",
            name = "다음주유소",
            distanceKm = 90.0,
            routeDistanceKm = 90.0,
            openStatus = OpenStatus.OPEN,
        )

        val result = engine.evaluate(
            FuelDecisionInput(
                rangeRemainingKm = 100.0,
                minimumReserveKm = 50.0,
                route = ActiveRoute("route-safety", "목적지", 120.0),
                stations = listOf(lastSafe, unsafeNext),
            ),
        ) as FuelDecision.Recommendation

        assertEquals(lastSafe, result.primary)
        assertTrue(unsafeNext !in result.alternatives)
        assertEquals(unsafeNext, result.nextAfterPrimary)
    }

    @Test
    fun passingStageStartsOnlyInsidePassingThreshold() {
        fun stageAt(distanceKm: Double, etaMinutes: Int): RecommendationStage {
            val primary = station(
                id = "primary-$distanceKm-$etaMinutes",
                name = "마지막안심주유소",
                distanceKm = distanceKm,
                routeDistanceKm = distanceKm,
                etaMinutes = etaMinutes,
                openStatus = OpenStatus.OPEN,
            )
            val result = engine.evaluate(
                FuelDecisionInput(
                    rangeRemainingKm = 80.0,
                    minimumReserveKm = 50.0,
                    route = ActiveRoute("route-threshold", "목적지", 60.0),
                    stations = listOf(primary),
                ),
            ) as FuelDecision.Recommendation
            return result.stage
        }

        assertEquals(RecommendationStage.RECOMMEND, stageAt(distanceKm = 5.1, etaMinutes = 6))
        assertEquals(RecommendationStage.PASSING, stageAt(distanceKm = 5.0, etaMinutes = 6))
        assertEquals(RecommendationStage.PASSING, stageAt(distanceKm = 6.0, etaMinutes = 5))
    }

    @Test
    fun unsupportedSignalsReturnMatchingUnavailableReasons() {
        val base = FuelDecisionInput(
            rangeRemainingKm = 100.0,
            route = ActiveRoute("route-unsupported", "목적지", 80.0),
            stations = emptyList(),
        )

        assertEquals(
            FuelDecision.Unavailable(UnavailableReason.ENERGY_UNSUPPORTED),
            engine.evaluate(base.copy(energyStatus = SignalStatus.UNSUPPORTED)),
        )
        assertEquals(
            FuelDecision.Unavailable(UnavailableReason.LOCATION_UNSUPPORTED),
            engine.evaluate(base.copy(locationStatus = SignalStatus.UNSUPPORTED)),
        )
        assertEquals(
            FuelDecision.Unavailable(UnavailableReason.ROUTE_UNSUPPORTED),
            engine.evaluate(base.copy(routeStatus = SignalStatus.UNSUPPORTED)),
        )
    }

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
