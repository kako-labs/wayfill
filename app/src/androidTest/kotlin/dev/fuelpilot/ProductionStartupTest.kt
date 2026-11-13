package dev.fuelpilot

import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.ui.Motion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProductionStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Without a vehicle source (phone AVD) motion must stay Unknown and settings locked. On an automotive
     * target the real AAOS adapter reads the gear, so motion becomes known. On a fresh target the vehicle
     * energy permission is not granted, so even with the station gateway configured the production build
     * stays unsupported and must never show a recommendation or synthetic data.
     */
    @Test fun realStartupStaysUnsupportedAndHonestAboutMotion() {
        val app = compose.activity.application as FuelPilotApplication
        val automotive = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        compose.waitUntil(10_000) { app.runtime.state.value.ready && app.runtime.state.value.presentation.availability == AssistAvailability.UNSUPPORTED }
        assertNull(app.runtime.state.value.presentation.route.rangeKm)
        assertNull(app.runtime.state.value.presentation.route.recommended)
        compose.onNodeWithTag("demo_banner").assertDoesNotExist()
        compose.onNodeWithTag("guide_now").assertDoesNotExist()
        if (automotive) {
            compose.waitUntil(10_000) { app.runtime.state.value.ui.motion != Motion.Unknown }
            compose.activityRule.scenario.recreate()
            assertNotEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
            compose.onNodeWithTag("guide_now").assertDoesNotExist()
        } else {
            assertEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
            compose.onNodeWithTag("unknown_companion").assertIsDisplayed()
            compose.onNodeWithTag("nav_settings").assertDoesNotExist()
            compose.onNodeWithTag("demo_launch").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("unknown_companion").assertIsDisplayed()
            assertEquals(Motion.Unknown, app.runtime.state.value.ui.motion)
        }
    }
}
