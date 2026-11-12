package dev.fuelpilot.ui

import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistPresentation
import dev.fuelpilot.feature.assist.AssistStoredState
import dev.fuelpilot.feature.assist.AssistViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveUiModelTest {
    private val station = FuelStation("station-a", "안전 주유소", 12.0, 3)

    private fun state(
        motion: Motion,
        availability: AssistAvailability = AssistAvailability.READY,
        screen: Screen = Screen.Recommendation,
        ready: Boolean = true,
        station: FuelStation? = this.station,
        alreadyExposed: Boolean = false,
    ) = AssistViewState(
        ready = ready,
        ui = FuelUiState(motion = motion, screen = screen),
        presentation = AssistPresentation(
            route = FuelRouteUi(recommended = station),
            targetScreen = Screen.Recommendation,
            recommendationKey = station?.let { "recommendation-1" },
            availability = availability,
        ),
        persisted = AssistStoredState(
            exposedRecommendationKeys = if (alreadyExposed) setOf("recommendation-1") else emptySet(),
        ),
    )

    @Test fun movingUsesGlanceableModeWithTheExactCurrentRecommendation() {
        val model = adaptiveUiModel(state(Motion.Moving))
        assertEquals(AdaptiveMode.Glanceable, model.mode)
        assertEquals(AdaptiveStatus.Recommendation, model.status)
        assertEquals("station-a", model.actionableStationId)
        assertEquals("recommendation-1", model.actionableRecommendationKey)
    }

    @Test fun unknownIsAlwaysReadOnly() {
        val model = adaptiveUiModel(state(Motion.Unknown))
        assertEquals(AdaptiveMode.Glanceable, model.mode)
        assertEquals(AdaptiveStatus.Recommendation, model.status)
        assertNull(model.actionableStationId)
        assertNull(model.actionableRecommendationKey)
    }

    @Test fun dismissedMovingRecommendationDoesNotReturnItsActions() {
        val model = adaptiveUiModel(state(Motion.Moving, alreadyExposed = true))
        assertEquals(AdaptiveStatus.Monitoring, model.status)
        assertNull(model.actionableStationId)
        assertNull(model.actionableRecommendationKey)
    }

    @Test fun parkedReadyRecommendationExposesOnlyItsCurrentStation() {
        val model = adaptiveUiModel(state(Motion.Parked))
        assertEquals(AdaptiveMode.Detailed, model.mode)
        assertEquals(AdaptiveStatus.Recommendation, model.status)
        assertEquals("station-a", model.actionableStationId)
        assertEquals("recommendation-1", model.actionableRecommendationKey)
    }

    @Test fun staleUnsupportedAndLoadingStatesNeverExposeAnAction() {
        val stale = adaptiveUiModel(state(Motion.Parked, AssistAvailability.STALE))
        val unsupported = adaptiveUiModel(state(Motion.Parked, AssistAvailability.UNSUPPORTED))
        val loading = adaptiveUiModel(state(Motion.Parked, ready = false))
        assertEquals(AdaptiveStatus.Stale, stale.status)
        assertEquals(AdaptiveStatus.Unsupported, unsupported.status)
        assertEquals(AdaptiveStatus.Loading, loading.status)
        assertNull(stale.actionableStationId)
        assertNull(unsupported.actionableStationId)
        assertNull(loading.actionableStationId)
        assertNull(stale.actionableRecommendationKey)
        assertNull(unsupported.actionableRecommendationKey)
        assertNull(loading.actionableRecommendationKey)
    }

    @Test fun transactionScreensDoNotReuseTheRecommendationAction() {
        val pending = adaptiveUiModel(state(Motion.Parked, screen = Screen.Adding))
        val success = adaptiveUiModel(state(Motion.Parked, screen = Screen.Added))
        assertEquals(AdaptiveStatus.Pending, pending.status)
        assertEquals(AdaptiveStatus.Success, success.status)
        assertNull(pending.actionableStationId)
        assertNull(success.actionableStationId)
    }

    @Test fun movingNavigationSuccessIsVisibleAndCannotRepeatTheAction() {
        val fromResult = state(Motion.Moving).copy(
            navigationResult = WaypointResult.Succeeded("request-1", "station-a"),
        )
        val fromRoute = state(Motion.Moving).let {
            it.copy(ui = it.ui.copy(routeStation = "station-a"))
        }
        listOf(fromResult, fromRoute).forEach { success ->
            val model = adaptiveUiModel(success)
            assertEquals(AdaptiveMode.Glanceable, model.mode)
            assertEquals(AdaptiveStatus.Success, model.status)
            assertNull(model.actionableStationId)
            assertNull(model.actionableRecommendationKey)
        }
    }

    @Test fun movingNavigationFailureKeepsOnlyTheFreshRetryAction() {
        val failed = state(Motion.Moving).let {
            it.copy(
                ui = it.ui.copy(error = "다시 시도해 주세요."),
                navigationResult = WaypointResult.Failed("request-1", "station-a"),
            )
        }
        val model = adaptiveUiModel(failed)
        assertEquals(AdaptiveStatus.Error, model.status)
        assertEquals("station-a", model.actionableStationId)
        assertEquals("recommendation-1", model.actionableRecommendationKey)
    }
}
