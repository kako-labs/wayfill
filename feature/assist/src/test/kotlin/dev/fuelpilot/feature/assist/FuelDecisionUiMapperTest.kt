package dev.fuelpilot.feature.assist

import dev.fuelpilot.model.ActiveRoute
import dev.fuelpilot.model.FuelDecision
import dev.fuelpilot.model.FuelDecisionInput
import dev.fuelpilot.model.FuelStationCandidate
import dev.fuelpilot.model.OpenStatus
import dev.fuelpilot.model.RecommendationMode
import dev.fuelpilot.model.RecommendationStage
import dev.fuelpilot.model.SelectionBasis
import dev.fuelpilot.model.SignalStatus
import dev.fuelpilot.model.UnavailableReason
import dev.fuelpilot.ui.FuelStation
import dev.fuelpilot.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FuelDecisionUiMapperTest {
    private val mapper = FuelDecisionUiMapper()

    @Test
    fun noActionHidesTheAssistSurface() {
        val presentation = mapper.map(
            presentationInput(
                decision = FuelDecision.NoAction(predictedArrivalRangeKm = 80.0),
            ),
        )

        assertNull(presentation.targetScreen)
        assertNull(presentation.recommendationKey)
        assertNull(presentation.route.recommended)
        assertEquals(AssistAvailability.READY, presentation.availability)
    }

    @Test
    fun monitoringProducesCtaFreeWidgetAndPreservesLastSafeStation() {
        val lastSafe = station(id = "last-safe", name = "안심주유소", distanceKm = 18.0)

        val presentation = mapper.map(
            presentationInput(
                decision = FuelDecision.Monitoring(
                    predictedArrivalRangeKm = 65.0,
                    lastSafeStation = lastSafe,
                ),
            ),
        )

        assertEquals(Screen.Widget, presentation.targetScreen)
        assertEquals("last-safe", presentation.route.recommended?.id)
        assertEquals(listOf("last-safe"), presentation.route.stations.map { it.id })
        assertNull(presentation.route.recommendationReason)
        assertNull(presentation.recommendationKey)
    }

    @Test
    fun recommendationMapsPrimaryAlternativesNextAfterPassingAndReadableReason() {
        val primary = station(
            id = "primary",
            name = "마지막 안전 주유소",
            distanceKm = 18.0,
            detourMinutes = 2,
        )
        val firstAlternative = station(id = "far-first", name = "먼 후보", distanceKm = 14.0)
        val secondAlternative = station(id = "near-second", name = "가까운 후보", distanceKm = 4.0)
        val next = station(id = "next", name = "다음 주유소", distanceKm = 105.0)
        val decision = recommendation(
            primary = primary,
            alternatives = listOf(firstAlternative, secondAlternative),
            nextAfterPrimary = next,
            rangeAfterStopKm = 82.0,
            rangeAfterNextKm = -5.0,
        )

        val presentation = mapper.map(presentationInput(decision = decision))

        assertEquals(Screen.Recommendation, presentation.targetScreen)
        assertEquals(
            FuelStation("primary", "마지막 안전 주유소", 18.0, 2, 82.0),
            presentation.route.recommended,
        )
        assertEquals(listOf("primary", "far-first", "near-second"), presentation.route.stations.map { it.id })
        assertEquals(
            FuelStation("next", "다음 주유소", 105.0, null, -5.0),
            presentation.route.nextAfterPassing,
        )
        assertEquals(
            "이곳을 지나면 다음 주유소까지 연료 여유가 부족할 수 있어요.",
            presentation.route.recommendationReason,
        )
        assertEquals(120.0, presentation.route.rangeKm!!, 0.001)
        assertEquals(100.0, presentation.route.destinationKm!!, 0.001)
    }

    @Test
    fun passingRecommendationSelectsPassingScreen() {
        val decision = recommendation(
            primary = station(id = "passing", name = "곧 지나칠 주유소", distanceKm = 4.0),
            stage = RecommendationStage.PASSING,
        )

        val presentation = mapper.map(presentationInput(decision = decision))

        assertEquals(Screen.Passing, presentation.targetScreen)
    }

    @Test
    fun staleUnavailableAndUnsupportedInputsProduceNullNumbersAndDistinctAvailability() {
        val cases = listOf(
            Triple(
                presentationInput(
                    decision = FuelDecision.Unavailable(UnavailableReason.STALE_ENERGY),
                    energyStatus = SignalStatus.STALE,
                ),
                AssistAvailability.STALE,
                "stale",
            ),
            Triple(
                presentationInput(
                    decision = FuelDecision.Unavailable(UnavailableReason.LOCATION_UNAVAILABLE),
                    locationStatus = SignalStatus.UNAVAILABLE,
                ),
                AssistAvailability.UNAVAILABLE,
                "unavailable",
            ),
            Triple(
                presentationInput(
                    decision = FuelDecision.Unavailable(UnavailableReason.ROUTE_UNSUPPORTED),
                    routeStatus = SignalStatus.UNSUPPORTED,
                ),
                AssistAvailability.UNSUPPORTED,
                "unsupported",
            ),
        )

        cases.forEach { (input, expectedAvailability, label) ->
            val presentation = mapper.map(input)
            assertEquals(label, expectedAvailability, presentation.availability)
            assertEquals(label, Screen.Widget, presentation.targetScreen)
            assertNull(label, presentation.route.rangeKm)
            assertNull(label, presentation.route.destinationKm)
            assertNull(label, presentation.route.recommended)
            assertNull(label, presentation.route.nextAfterPassing)
            assertEquals(label, emptyList<FuelStation>(), presentation.route.stations)
            assertNull(label, presentation.recommendationKey)
        }
    }

    @Test
    fun recommendationKeyChangesOnlyWithDocumentedInputs() {
        val primary = station(id = "primary", name = "기준 주유소", distanceKm = 18.0)
        val baseDecision = recommendation(primary = primary)
        val baseInput = presentationInput(
            decision = baseDecision,
            routeSnapshotId = "route-snapshot-1",
        )
        val baseKey = mapper.map(baseInput).recommendationKey

        assertNotNull(baseKey)
        assertEquals(
            baseKey,
            mapper.map(
                baseInput.copy(
                    decision = baseDecision.copy(
                        basis = SelectionBasis.MINIMUM_DETOUR,
                        predictedArrivalRangeKm = 7.0,
                        rangeAfterStopKm = 1.0,
                        alternatives = listOf(station("other", "다른 후보", 2.0)),
                    ),
                    evaluatedInput = baseInput.evaluatedInput.copy(
                        rangeRemainingKm = 999.0,
                        minimumReserveKm = 1.0,
                    ),
                ),
            ).recommendationKey,
        )
        assertNotEquals(baseKey, mapper.map(baseInput.copy(routeSnapshotId = "route-snapshot-2")).recommendationKey)
        assertNotEquals(
            baseKey,
            mapper.map(baseInput.copy(decision = baseDecision.copy(mode = RecommendationMode.NEARBY))).recommendationKey,
        )
        assertEquals(
            baseKey,
            mapper.map(baseInput.copy(decision = baseDecision.copy(stage = RecommendationStage.PASSING))).recommendationKey,
        )
        assertNotEquals(
            baseKey,
            mapper.map(
                baseInput.copy(
                    decision = baseDecision.copy(primary = primary.copy(id = "different-primary")),
                ),
            ).recommendationKey,
        )
    }

    @Test
    fun mapperPreservesDecisionOrderAndValuesWithoutRerankingFilteringOrSynthesis() {
        val primary = station(id = "primary", name = "선택된 주유소", distanceKm = 30.0)
        val closedAlternative = station(
            id = "closed-first",
            name = "엔진이 준 첫 후보",
            distanceKm = 90.0,
            openStatus = OpenStatus.CLOSED,
            fuelCompatible = false,
        )
        val nearerAlternative = station(id = "nearer-second", name = "엔진이 준 둘째 후보", distanceKm = 1.0)
        val next = station(id = "unsafe-next", name = "안전하지 않은 다음 후보", distanceKm = 130.0)
        val decision = recommendation(
            primary = primary,
            alternatives = listOf(closedAlternative, nearerAlternative),
            nextAfterPrimary = next,
            rangeAfterStopKm = 17.25,
            rangeAfterNextKm = -10.5,
        )

        val route = mapper.map(presentationInput(decision = decision)).route

        assertEquals(listOf("primary", "closed-first", "nearer-second"), route.stations.map { it.id })
        assertEquals(17.25, route.recommended?.remainingAtStationKm!!, 0.001)
        assertEquals(-10.5, route.nextAfterPassing?.remainingAtStationKm!!, 0.001)
        assertNull(route.stations[1].remainingAtStationKm)
        assertNull(route.stations[2].remainingAtStationKm)
    }

    private fun presentationInput(
        decision: FuelDecision,
        routeSnapshotId: String? = "route-snapshot-1",
        energyStatus: SignalStatus = SignalStatus.FRESH,
        locationStatus: SignalStatus = SignalStatus.FRESH,
        routeStatus: SignalStatus = SignalStatus.FRESH,
    ) = AssistPresentationInput(
        decision = decision,
        evaluatedInput = FuelDecisionInput(
            rangeRemainingKm = 120.0,
            minimumReserveKm = 50.0,
            route = ActiveRoute("route-1", "목적지", 100.0),
            stations = emptyList(),
            energyStatus = energyStatus,
            locationStatus = locationStatus,
            routeStatus = routeStatus,
        ),
        routeSnapshotId = routeSnapshotId,
    )

    private fun recommendation(
        primary: FuelStationCandidate,
        alternatives: List<FuelStationCandidate> = emptyList(),
        nextAfterPrimary: FuelStationCandidate? = null,
        rangeAfterStopKm: Double = 50.0,
        rangeAfterNextKm: Double? = null,
        stage: RecommendationStage = RecommendationStage.RECOMMEND,
    ) = FuelDecision.Recommendation(
        mode = RecommendationMode.LAST_SAFE_STOP,
        basis = SelectionBasis.LAST_SAFE_STOP,
        primary = primary,
        alternatives = alternatives,
        predictedArrivalRangeKm = 20.0,
        rangeAfterStopKm = rangeAfterStopKm,
        nextAfterPrimary = nextAfterPrimary,
        rangeAfterNextKm = rangeAfterNextKm,
        stage = stage,
    )

    private fun station(
        id: String,
        name: String,
        distanceKm: Double,
        detourMinutes: Int? = null,
        openStatus: OpenStatus = OpenStatus.OPEN,
        fuelCompatible: Boolean = true,
    ) = FuelStationCandidate(
        id = id,
        name = name,
        distanceFromCurrentKm = distanceKm,
        distanceAlongRouteKm = distanceKm,
        etaMinutes = null,
        detourMinutes = detourMinutes,
        openStatus = openStatus,
        fuelCompatible = fuelCompatible,
        businessInfoFresh = true,
    )
}
