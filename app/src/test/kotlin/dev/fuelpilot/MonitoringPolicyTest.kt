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

class MonitoringPolicyTest {
    @Test fun adviceIsCreatedForFreshParkedOrMovingRecommendationButNotUnknown() {
        val valid = recommendationState()
        assertEquals(BackgroundAdvice("recommendation-1", "station-1", "Station One"), backgroundAdvice(valid))
        assertEquals(
            BackgroundAdvice("recommendation-1", "station-1", "Station One"),
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
