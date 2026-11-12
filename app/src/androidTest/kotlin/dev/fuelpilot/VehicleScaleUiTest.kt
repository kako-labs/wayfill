package dev.fuelpilot

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.fuelpilot.demo.DemoActivity
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.Screen
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VehicleScaleUiTest {
    @get:Rule val compose = createAndroidComposeRule<DemoActivity>()

    private fun waitForState(predicate: () -> Boolean) = compose.waitUntil(10_000, predicate)

    @Before fun showFreshParkedRecommendation() {
        compose.runOnIdle {
            compose.activity.sources.motion.value = Motion.Parked
            compose.activity.sources.select("recommendation")
        }
        waitForState {
            val state = compose.activity.runtime.state.value
            state.ready && state.ui.motion == Motion.Parked &&
                state.presentation.availability == AssistAvailability.READY &&
                state.presentation.route.recommended != null && state.ui.screen == Screen.Recommendation
        }
    }

    @Test fun movingHasOnlyTheCurrentTwoActionRecommendationAndUnknownIsReadOnly() {
        compose.onNodeWithTag("demo_moving").performScrollTo().performClick()
        waitForState {
            val state = compose.activity.runtime.state.value
            state.ui.motion == Motion.Moving && state.activeRecommendationKey != null
        }

        compose.onNodeWithTag("moving_companion").assertIsDisplayed()
        compose.onNodeWithTag("podong_calm").assertIsDisplayed()
        compose.onNodeWithTag("guide_now").assertIsDisplayed()
        compose.onNodeWithTag("later").assertIsDisplayed()
        compose.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("moving_recommendation_actions")),
        ).assertCountEquals(2)
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
        compose.onNodeWithTag("parked_settings").assertDoesNotExist()

        compose.runOnIdle { compose.activity.runtime.dispatch(AssistIntent.MotionChanged(Motion.Unknown)) }
        waitForState { compose.activity.runtime.state.value.ui.motion == Motion.Unknown }
        compose.onNodeWithTag("unknown_companion").assertIsDisplayed()
        compose.onNodeWithTag("podong_calm").assertIsDisplayed()
        compose.onNodeWithTag("guide_now").assertDoesNotExist()
        compose.onNodeWithTag("later").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
    }

    @Test fun parkedNavigationExposesAssistDetailsAndSettings() {
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
        compose.onNodeWithTag("nav_assist").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").assertIsDisplayed()

        compose.onNodeWithTag("nav_assist").performClick()
        compose.onNodeWithTag("parked_recommendation").assertIsDisplayed()
        compose.onNodeWithTag("parked_details").assertIsDisplayed()
        compose.onNodeWithTag("parked_guide_now").assertIsDisplayed()
        compose.onNodeWithTag("parked_later").assertIsDisplayed()

        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test fun drivingInterruptionAlwaysReturnsParkedUiToHome() {
        compose.onNodeWithTag("nav_assist").performClick()
        compose.onNodeWithTag("parked_recommendation").assertIsDisplayed()
        compose.onNodeWithTag("demo_moving").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.motion == Motion.Moving }
        compose.onNodeWithTag("demo_parked").performScrollTo().performClick()
        waitForState {
            val state = compose.activity.runtime.state.value
            state.ui.motion == Motion.Parked && state.presentation.availability == AssistAvailability.READY
        }
        compose.onNodeWithTag("parked_recommendation").assertDoesNotExist()
        compose.onNodeWithTag("home_state_ready").assertIsDisplayed()

        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("demo_moving").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.motion == Motion.Moving }
        compose.onNodeWithTag("moving_companion").assertIsDisplayed()
        compose.onNodeWithTag("demo_parked").performScrollTo().performClick()
        waitForState {
            val state = compose.activity.runtime.state.value
            state.ui.motion == Motion.Parked && state.presentation.availability == AssistAvailability.READY
        }
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.onNodeWithTag("home_state_ready").assertIsDisplayed()
    }

    @Test fun unsupportedStateIsExplicitOnHomeAndAssistSurfaces() {
        compose.onNodeWithTag("demo_unsupported").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.presentation.availability == AssistAvailability.UNSUPPORTED }

        compose.onNodeWithTag("home_state_unsupported").assertIsDisplayed()
        compose.onNodeWithTag("nav_assist").performClick()
        compose.onNodeWithTag("state_unsupported").assertIsDisplayed()
        compose.onNodeWithTag("parked_guide_now").assertDoesNotExist()
    }

    @Test fun verifiedRefuelShowsSuccessWithoutInventingASecondAction() {
        compose.onNodeWithTag("demo_small").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.refuel != null }
        compose.onNodeWithTag("nav_assist").performClick()

        compose.onNodeWithTag("refuel_success").assertIsDisplayed()
        compose.onNodeWithTag("parked_guide_now").assertDoesNotExist()
        compose.onNodeWithTag("guide_now").assertDoesNotExist()
    }
}
