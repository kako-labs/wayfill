package dev.fuelpilot

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.fuelpilot.demo.DemoActivity
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.Screen
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Reproducible synthetic visual review; no screenshot is promoted to a real PLEOS capture. */
class VisualReviewTest {
    @get:Rule val compose=createAndroidComposeRule<DemoActivity>()
    private fun waitFor(predicate:()->Boolean)=compose.waitUntil(10_000,predicate)
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"review-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun captureProductStatesAndObserveDrawnGreetingThenCalmDeparture() {
        waitFor{compose.activity.runtime.state.value.ready&&compose.activity.runtime.state.value.ui.motion==Motion.Parked}
        compose.runOnIdle {
            compose.activity.runtime.dispatch(AssistIntent.ClearRefuelHistory)
            // This test explicitly enables drawing for visual QA even if the emulator reduces motion.
            compose.activity.runtime.dispatch(AssistIntent.ReduceMotionChanged(false))
            compose.activity.runtime.dispatch(AssistIntent.CharacterChanged(true))
            compose.activity.sources.select("recommendation")
        }
        waitFor{
            val state=compose.activity.runtime.state.value
            val stationId=state.presentation.route.recommended?.id
            state.ui.screen==Screen.Recommendation && stationId!=null &&
                state.persisted.routeId==compose.activity.sources.currentRouteId &&
                state.navigationTargets.containsKey(stationId)
        }
        capture("home")
        compose.onNodeWithTag("nav_assist").performClick()
        compose.onNodeWithTag("parked_details").assertIsDisplayed()
        capture("assist")
        compose.runOnIdle{compose.activity.sources.navigationDelayMs=2000}
        compose.onNodeWithTag("parked_guide_now").assertIsDisplayed().assertHasClickAction()
        val navigation = compose.activity.runtime.state.value
        val station = requireNotNull(navigation.presentation.route.recommended)
        compose.runOnIdle {
            compose.activity.runtime.dispatch(AssistIntent.SelectStation(station.id, navigation.presentation.recommendationKey))
        }
        waitFor{compose.activity.runtime.state.value.pending!=null}
        compose.onNodeWithTag("navigation_pending").assertIsDisplayed()
        capture("navigation-pending")
        waitFor{compose.activity.runtime.state.value.ui.screen==Screen.Added}
        compose.onNodeWithTag("navigation_success").assertIsDisplayed()
        capture("navigation-success")
        // Keep screenshot assertions at an intermediate frame instead of auto-advancing to idle.
        compose.mainClock.autoAdvance=false
        compose.runOnIdle{compose.activity.sources.refuel("full")}
        waitFor{compose.activity.runtime.state.value.ui.celebration!=null}
        compose.mainClock.advanceTimeByFrame()
        println("Visual greeting: reduced=${compose.activity.runtime.state.value.ui.reduceMotion}, ageMs=${System.nanoTime()/1_000_000L-(compose.activity.runtime.state.value.ui.celebration?.startedAtMs?:0)}")
        val mascot=compose.onNodeWithContentDescription("안내하는 뽀동이",useUnmergedTree=true)
        val first=mascot.captureToImage().asAndroidBitmap()
        SystemClock.sleep(850)
        compose.mainClock.advanceTimeBy(850)
        assertFalse("New drawn greeting must change the character pixels",first.sameAs(mascot.captureToImage().asAndroidBitmap()))
        capture("refuel-peak")
        compose.runOnIdle{compose.activity.sources.motion.value=Motion.Moving}
        waitFor{compose.activity.runtime.state.value.ui.motion==Motion.Moving}
        compose.mainClock.advanceTimeByFrame()
        val calm=compose.onNodeWithTag("podong_calm").captureToImage().asAndroidBitmap()
        SystemClock.sleep(600)
        compose.mainClock.advanceTimeBy(600)
        assertTrue("Departure must cancel the greeting and keep a still calm frame",calm.sameAs(compose.onNodeWithTag("podong_calm").captureToImage().asAndroidBitmap()))
        capture("moving")
        compose.mainClock.autoAdvance=true
        compose.runOnIdle{compose.activity.sources.motion.value=Motion.Parked}
        waitFor{compose.activity.runtime.state.value.ui.motion==Motion.Parked}
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithTag("history_record").assertIsDisplayed()
        capture("history")
        compose.onNodeWithTag("history_record").performClick()
        capture("history-detail")
        compose.onNodeWithTag("nav_settings").performClick()
        capture("settings")
        compose.runOnIdle{compose.activity.runtime.dispatch(AssistIntent.ReduceMotionChanged(true))}
    }
}
