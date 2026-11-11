package dev.fuelpilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.ui.Motion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductionStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realStartupStaysUnsupportedAndUnknownWithLockedSettings() {
        val app = compose.activity.application as FuelPilotApplication
        compose.waitUntil(10_000) { app.runtime.state.value.ready && app.runtime.state.value.presentation.availability == AssistAvailability.UNSUPPORTED }
        assertEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
        assertNull(app.runtime.state.value.presentation.route.rangeKm)
        assertNull(app.runtime.state.value.presentation.route.recommended)
        compose.onNodeWithTag("demo_banner").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("+10km").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("−10km").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav_home").performClick()
        assertEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
        compose.onNodeWithTag("demo_launch").performScrollTo().assertIsDisplayed()
    }
}
