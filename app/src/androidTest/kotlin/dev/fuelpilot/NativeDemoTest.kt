package dev.fuelpilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.fuelpilot.demo.DemoActivity
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.Screen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NativeDemoTest {
    @get:Rule val compose = createAndroidComposeRule<DemoActivity>()

    private fun waitForState(predicate: () -> Boolean) = compose.waitUntil(10_000, predicate)

    @Before fun establishKnownParkedPolicy() {
        waitForState { compose.activity.runtime.state.value.ready && compose.activity.runtime.state.value.ui.motion == Motion.Parked }
        compose.runOnIdle { compose.activity.runtime.dispatch(AssistIntent.ReserveChanged(50)) }
        waitForState { compose.activity.runtime.state.value.persisted.minimumReserveKm == 50 }
    }

    @Test fun setupPolicyAndNavigationSurviveRotation() {
        waitForState { compose.activity.runtime.state.value.ready }
        compose.onNodeWithTag("demo_banner").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("+10km").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.persisted.minimumReserveKm >= 60 }
        val reserve = compose.activity.runtime.state.value.persisted.minimumReserveKm
        compose.activityRule.scenario.recreate()
        waitForState { compose.activity.runtime.state.value.ready }
        assertEquals(reserve, compose.activity.runtime.state.value.persisted.minimumReserveKm)
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithTag("nav_assist").performClick()
    }

    @Test fun wholeFlowNavigationSuccessFailureAndNearby() {
        waitForState { compose.activity.runtime.state.value.presentation.route.recommended != null }
        compose.onNodeWithTag("nav_assist").performClick()
        compose.onNodeWithText("여기서 주유하기").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        waitForState { compose.activity.runtime.state.value.ui.screen == Screen.Added }
        assertEquals(1, compose.activity.sources.navigationCalls)
        compose.onNodeWithTag("demo_navfailure").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.runtime.dispatch(AssistIntent.Continue) }
        compose.onNodeWithTag("demo_recommendation").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.screen == Screen.Recommendation }
        compose.onNodeWithText("여기서 주유하기").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.error != null }
        compose.onNodeWithTag("demo_nearby").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.persisted.routeId == null &&
            compose.activity.runtime.state.value.presentation.route.recommended != null }
        compose.onNodeWithTag("demo_navsuccess").performScrollTo().performClick()
        compose.onNodeWithText("여기서 주유하기").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.screen == Screen.Added }
        assertEquals(3, compose.activity.sources.navigationCalls)
    }

    @Test fun movingCancelsAndDuplicateRefuelDoesNotReplay() {
        waitForState { compose.activity.runtime.state.value.ready && compose.activity.runtime.state.value.ui.motion == Motion.Parked }
        compose.onNodeWithTag("demo_small").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.refuel != null }
        val eventId = compose.activity.runtime.state.value.ui.refuel!!.id
        compose.onNodeWithTag("demo_moving").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.motion == Motion.Moving }
        assertNull(compose.activity.runtime.state.value.ui.celebration)
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("+10km").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("demo_parked").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.motion == Motion.Parked }
        compose.onNodeWithTag("demo_duplicate").performScrollTo().performClick()
        compose.waitForIdle()
        assertNull(compose.activity.runtime.state.value.ui.refuel)
        assertTrue(eventId in compose.activity.runtime.state.value.persisted.processedRefuels)
        compose.activityRule.scenario.recreate()
        waitForState { compose.activity.runtime.state.value.ready }
        assertNull(compose.activity.runtime.state.value.ui.celebration)
        compose.runOnIdle { compose.activity.runtime.retry() }
        waitForState { compose.activity.runtime.state.value.ready && eventId in compose.activity.runtime.state.value.persisted.processedRefuels }
        compose.onNodeWithTag("demo_duplicate").performScrollTo().performClick()
        compose.waitForIdle()
        assertNull(compose.activity.runtime.state.value.ui.celebration)
        assertNull(compose.activity.runtime.state.value.ui.refuel)
    }
    @Test fun scenarioAvailabilityAndRefuelVariantsUseCoordinator() {
        waitForState { compose.activity.runtime.state.value.ready }
        for ((scenario, expected) in listOf(
            "stale" to dev.fuelpilot.feature.assist.AssistAvailability.STALE,
            "unsupported" to dev.fuelpilot.feature.assist.AssistAvailability.UNSUPPORTED,
            "unavailable" to dev.fuelpilot.feature.assist.AssistAvailability.UNAVAILABLE,
        )) {
            compose.onNodeWithTag("demo_$scenario").performScrollTo().performClick()
            waitForState { compose.activity.runtime.state.value.presentation.availability == expected }
        }
        compose.onNodeWithTag("demo_noaction").performScrollTo().performClick()
        waitForState { !compose.activity.runtime.state.value.overlayVisible }
        compose.onNodeWithTag("demo_passing").performScrollTo().performClick()
        waitForState { compose.activity.runtime.state.value.ui.screen == Screen.Passing }
        for ((variant, expected) in listOf("full" to dev.fuelpilot.ui.Variant.Full, "recovery" to dev.fuelpilot.ui.Variant.Recovery)) {
            compose.onNodeWithTag("demo_$variant").performScrollTo().performClick()
            waitForState { compose.activity.runtime.state.value.ui.refuel?.let { dev.fuelpilot.ui.refuelVariant(it) == expected } == true }
        }
    }

}
