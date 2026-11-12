package dev.fuelpilot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.feature.assist.AssistPresentation
import dev.fuelpilot.feature.assist.AssistViewState
import dev.fuelpilot.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SplitWindowUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state(motion: Motion) = AssistViewState(
        ready = true, assistVisible = true, activeRecommendationKey = "current",
        ui = FuelUiState(motion = motion, screen = if (motion == Motion.Parked) Screen.Recommendation else Screen.Widget),
        presentation = AssistPresentation(
            route = FuelRouteUi(18.0, 38.0, FuelStation("a", "이름이 아주 긴 고속도로 서울방향 주유소", 3.0, 2),
                FuelStation("b", "다음", 35.0, 3)),
            targetScreen = Screen.Passing, recommendationKey = "current", availability = AssistAvailability.READY),
    )

    private fun verifyWindow(width: Int, height: Int, motion: Motion) {
        val intents = mutableListOf<AssistIntent>()
        compose.setContent { Box(Modifier.requiredSize(width.dp, height.dp).testTag("split_test_window")) {
            FuelPilotUi(state(motion), { intents += it })
        } }
        val guide = compose.onNodeWithTag("guide_now").assertIsDisplayed()
        val later = compose.onNodeWithTag("later").assertIsDisplayed()
        val guideBounds = guide.fetchSemanticsNode().boundsInRoot
        val laterBounds = later.fetchSemanticsNode().boundsInRoot
        val windowBounds = compose.onNodeWithTag("split_test_window").fetchSemanticsNode().boundsInRoot
        for (bounds in listOf(guideBounds, laterBounds)) {
            assertTrue("Both actions must fit completely inside the window", bounds.left >= windowBounds.left &&
                bounds.right <= windowBounds.right && bounds.top >= windowBounds.top && bounds.bottom <= windowBounds.bottom)
        }
        assertTrue("Actions must not overlap", guideBounds.intersect(laterBounds).isEmpty)
        assertTrue("Guide target must be at least 76dp", guideBounds.height >= with(compose.density) { 76.dp.toPx() } - 1f)
        compose.onNodeWithTag("parked_details").assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onNodeWithTag("split_test_window").captureToImage().asAndroidBitmap()
        File(context.filesDir, "split-${width}x${height}-${motion.name}.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        guide.performClick()
        assertEquals(listOf(AssistIntent.SelectStation("a", "current")), intents)
    }

    @Test fun narrowMovingWindowKeepsLongNameAndActionsSeparate() = verifyWindow(420, 620, Motion.Moving)
    @Test fun shortMovingWindowKeepsActionsOnScreen() = verifyWindow(640, 340, Motion.Moving)
    @Test fun narrowParkedRecommendationDoesNotRequireScrollingToAnAction() = verifyWindow(420, 620, Motion.Parked)

    @Test fun narrowParkedAppShellGivesItsWholeWindowToTheRecommendation() {
        compose.setContent { Box(Modifier.requiredSize(420.dp,620.dp).testTag("shell_window")) {
            AppShell(state(Motion.Parked),{})
        } }
        val guide=compose.onNodeWithTag("guide_now").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val later=compose.onNodeWithTag("later").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val window=compose.onNodeWithTag("shell_window").fetchSemanticsNode().boundsInRoot
        assertTrue(guide.bottom<=window.bottom&&later.bottom<=window.bottom)
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
        compose.onNodeWithTag("nav_records").assertDoesNotExist()
    }

    @Test fun unknownWindowHasAConnectionStateAndNoNavigationAction() {
        compose.setContent { Box(Modifier.requiredSize(420.dp,620.dp).testTag("split_test_window")) {
            FuelPilotUi(state(Motion.Unknown),{})
        } }
        compose.onNodeWithTag("unknown_companion").assertIsDisplayed()
        compose.onNodeWithTag("guide_now").assertDoesNotExist()
        compose.onNodeWithTag("later").assertDoesNotExist()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"split-420x620-Unknown.png").outputStream().use {
            compose.onNodeWithTag("split_test_window").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun parkedOverviewShowsItsStationAndUsesTheSameCurrentRequestIdentity() {
        val intents=mutableListOf<AssistIntent>()
        compose.setContent { Box(Modifier.requiredSize(1600.dp,1000.dp).testTag("home_test_window")) {
            AppShell(state(Motion.Parked),{intents+=it})
        } }
        compose.onNodeWithTag("home_guide_now").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"home-overview.png").outputStream().use {
            compose.onNodeWithTag("home_test_window").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithTag("home_guide_now").performClick()
        assertEquals(listOf(AssistIntent.SelectStation("a","current")),intents)
    }
}
