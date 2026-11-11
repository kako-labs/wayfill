package dev.fuelpilot

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.fuelpilot.feature.assist.AssistViewState
import dev.fuelpilot.ui.FuelPilotSettings
import dev.fuelpilot.ui.FuelUiState
import dev.fuelpilot.ui.Motion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reducedMotionSettingsKeepsTheMascotStillAcrossItsBlinkCycle() {
        compose.setContent {
            MaterialTheme {
                FuelPilotSettings(AssistViewState(ready = true,
                    ui = FuelUiState(motion = Motion.Parked, reduceMotion = true)), {}, {})
            }
        }
        val mascot = compose.onNodeWithContentDescription("안내하는 뽀동이", useUnmergedTree = true)
        val baseline = mascot.captureToImage().asAndroidBitmap()
        repeat(28) {
            SystemClock.sleep(250)
            assertTrue("Reduced-motion settings must retain a still character",
                baseline.sameAs(mascot.captureToImage().asAndroidBitmap()))
        }
    }
}
