package dev.fuelpilot

import dev.fuelpilot.data.navigation.WaypointRequest
import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistPresentation
import dev.fuelpilot.feature.assist.AssistViewState
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.FuelStation
import dev.fuelpilot.ui.FuelUiState
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import dev.fuelpilot.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MonitoringPolicyTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun hidingAndShowingTheAppReevaluatesAnUnchangedRecommendation() = runTest {
        val states = MutableStateFlow(recommendationState())
        val visible = MutableStateFlow(true)
        val observed = mutableListOf<BackgroundAdvice?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitoringSnapshots(states, visible).collect { (state, uiVisible) ->
                observed += if (uiVisible) null else backgroundAdvice(state)
            }
        }
        runCurrent()
        assertNull(observed.single())
        visible.value = false; runCurrent()
        assertEquals("station-1", observed.last()?.stationId)
        visible.value = true; runCurrent()
        assertNull(observed.last())
        assertEquals(3, observed.size)
    }

    @Test fun dismissedCompanionIsNotReintroducedAsABackgroundAlert() {
        val state = recommendationState()
        val dismissed = state.copy(persisted = state.persisted.copy(exposedRecommendationKeys = setOf("recommendation-1")))
        assertNull(backgroundAdvice(dismissed))
        assertTrue(backgroundAdvice(dismissed.copy(activeRecommendationKey = "recommendation-1")) != null)
    }

    @Test fun notificationUsesTheSameReasonAndRouteFactsAsTheCompanion() {
        val state = recommendationState()
        val route = state.presentation.route.copy(rangeKm = 18.0, destinationKm = 38.0,
            nextAfterPassing = FuelStation("next", "다음", 35.0, 2))
        val advice = backgroundAdvice(state.copy(presentation = state.presentation.copy(route = route, targetScreen = Screen.Passing)))!!
        assertEquals("지나치면 다음 주유소까지 연료 부족 예상", advice.reason)
        assertEquals("3km 앞 · 경로 +1분", advice.routeFacts)
    }

    @Test fun speechUsesTheSameAdviceIdentityAndFactsAsTheHun() {
        val state = recommendationState()
        val advice = backgroundAdvice(state)!!
        val speech = recommendationSpeech(advice)
        assertEquals("recommendation-1", speech.recommendationKey)
        assertEquals("3km 앞 · 경로 +1분. Station One에서 주유가 필요해요. 안내할까요?", speech.text)
        assertEquals("ko-KR", speech.localeTag)
        assertEquals(
            RecommendationVoiceCommand(
                RecommendationActionKind.GUIDE_NOW,
                speech.recommendationKey,
                advice.stationId,
            ),
            recommendationVoiceCommand(
                state,
                RecommendationActionKind.GUIDE_NOW,
                speech.recommendationKey,
                advice.stationId,
            ),
        )
        assertNull(recommendationVoiceCommand(
            state,
            RecommendationActionKind.LATER,
            "stale-recommendation",
            advice.stationId,
        ))
    }

    @Test fun adviceIsCreatedForFreshParkedOrMovingRecommendationButNotUnknown() {
        val valid = recommendationState()
        assertEquals(BackgroundAdvice("recommendation-1", "station-1", "Station One", routeFacts = "3km 앞 · 경로 +1분"), backgroundAdvice(valid))
        assertEquals(
            BackgroundAdvice("recommendation-1", "station-1", "Station One", routeFacts = "3km 앞 · 경로 +1분"),
            backgroundAdvice(valid.copy(ui = valid.ui.copy(motion = Motion.Moving))),
        )

        assertNull(backgroundAdvice(valid.copy(ready = false)))
        assertNull(backgroundAdvice(valid.copy(ui = valid.ui.copy(motion = Motion.Unknown))))
        assertNull(backgroundAdvice(valid.copy(presentation = valid.presentation.copy(availability = AssistAvailability.STALE))))
        assertNull(backgroundAdvice(valid.copy(presentation = valid.presentation.copy(availability = AssistAvailability.UNSUPPORTED))))
        assertNull(backgroundAdvice(valid.copy(presentation = valid.presentation.copy(targetScreen = Screen.LongDrive))))
        assertNull(backgroundAdvice(valid.copy(pending = WaypointRequest("request-1", "station-1"))))
        assertNull(backgroundAdvice(valid.copy(navigationResult = WaypointResult.Succeeded("request-1", "station-1"))))
        assertNull(backgroundAdvice(valid.copy(ui = valid.ui.copy(refuel = RefuelEvent("refuel-1")))))
    }

    @Test fun actionRequiresExactCurrentRecommendationAndStationIdentity() {
        val valid = recommendationState()
        assertEquals(
            RecommendationActionDecision.DISPATCH,
            recommendationActionDecision(valid, "recommendation-1", "station-1"),
        )
        assertEquals(
            RecommendationActionDecision.DISPATCH,
            recommendationActionDecision(
                valid.copy(ui = valid.ui.copy(motion = Motion.Moving)),
                "recommendation-1",
                "station-1",
            ),
        )
        assertEquals(
            RecommendationActionDecision.REJECT,
            recommendationActionDecision(
                valid.copy(ui = valid.ui.copy(motion = Motion.Unknown)),
                "recommendation-1",
                "station-1",
            ),
        )
        assertEquals(
            RecommendationActionDecision.REJECT,
            recommendationActionDecision(valid, "recommendation-2", "station-1"),
        )
        assertEquals(
            RecommendationActionDecision.REJECT,
            recommendationActionDecision(valid, "recommendation-1", "station-2"),
        )
        assertEquals(
            RecommendationActionDecision.REJECT,
            recommendationActionDecision(valid, "recommendation-1\nforged", "station-1"),
        )
        assertEquals(
            RecommendationActionDecision.REJECT,
            recommendationActionDecision(
                valid.copy(presentation = valid.presentation.copy(availability = AssistAvailability.UNAVAILABLE)),
                "recommendation-1",
                "station-1",
            ),
        )
    }

    @Test fun cooldownHasDeterministicBoundaryAndFailsClosedOnClockRollback() {
        assertTrue(recommendationCooldownElapsed(null, nowMs = 1_000L))
        assertFalse(recommendationCooldownElapsed(1_000L, 1_000L + RECOMMENDATION_COOLDOWN_MS - 1))
        assertTrue(recommendationCooldownElapsed(1_000L, 1_000L + RECOMMENDATION_COOLDOWN_MS))
        assertFalse(recommendationCooldownElapsed(2_000L, 1_999L))
        assertFalse(recommendationCooldownElapsed(null, nowMs = -1L))
        assertFalse(recommendationCooldownElapsed(null, nowMs = 1_000L, cooldownMs = -1L))
    }

    @Test fun hunTimeoutIsFifteenSecondsAndIndependentFromRecommendationCooldown() {
        assertEquals(15_000L, RECOMMENDATION_HUN_TIMEOUT_MS)
        assertTrue(RECOMMENDATION_COOLDOWN_MS > RECOMMENDATION_HUN_TIMEOUT_MS)
    }

    @Test fun foregroundMonitoringStartRequiresParkedMotion() {
        assertTrue(monitoringStartAllowed(Motion.Parked))
        assertFalse(monitoringStartAllowed(Motion.Moving))
        assertFalse(monitoringStartAllowed(Motion.Unknown))
    }

    @Test fun automotiveMonitoringRequiresFusedLocationPermission() {
        assertFalse(monitoringPermissionsSatisfiedForEnvironment(
            location = true,
            notification = true,
            automotive = true,
            fusedLocation = false,
        ))
    }

    @Test fun nonAutomotiveMonitoringDoesNotRequireFusedLocationPermission() {
        assertTrue(monitoringPermissionsSatisfiedForEnvironment(
            location = true,
            notification = true,
            automotive = false,
            fusedLocation = false,
        ))
    }

    private fun recommendationState() = AssistViewState(
        ready = true,
        ui = FuelUiState(motion = Motion.Parked),
        presentation = AssistPresentation(
            route = FuelRouteUi(recommended = FuelStation("station-1", "  Station\nOne  ", 3.0, 1)),
            targetScreen = Screen.Recommendation,
            recommendationKey = "recommendation-1",
            availability = AssistAvailability.READY,
        ),
    )
}
