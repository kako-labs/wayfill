package dev.fuelpilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.fuelpilot.demo.DemoActivity
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.ui.Motion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HistoryFlowTest {
    @get:Rule val compose=createAndroidComposeRule<DemoActivity>()
    private fun waitFor(predicate:()->Boolean)=compose.waitUntil(10_000,predicate)
    @Before fun resetSyntheticHistoryOnly() {
        waitFor{compose.activity.runtime.state.value.ready&&compose.activity.runtime.state.value.ui.motion==Motion.Parked}
        compose.runOnIdle{compose.activity.runtime.dispatch(AssistIntent.ClearRefuelHistory)}
        waitFor{compose.activity.runtime.state.value.persisted.refuelHistory.isEmpty()}
    }
    private fun addOneRecord() {
        compose.runOnIdle{compose.activity.sources.refuel("small")}
        waitFor{compose.activity.runtime.state.value.persisted.refuelHistory.size==1}
        compose.onNodeWithTag("nav_records").performClick()
        compose.onNodeWithTag("history_screen").assertIsDisplayed()
        compose.onAllNodesWithTag("history_record").assertCountEquals(1)
    }
    @Test fun verifiedRefuelRecordAndDetailSurviveRotationWithoutDuplicate() {
        addOneRecord()
        compose.onNodeWithTag("history_record").performClick()
        compose.onNodeWithText("확인된 주유량").assertIsDisplayed()
        val record=compose.activity.runtime.state.value.persisted.refuelHistory.single()
        compose.activityRule.scenario.recreate()
        waitFor{compose.activity.runtime.state.value.ready}
        compose.onNodeWithTag("history_screen").assertIsDisplayed()
        assertEquals(record,compose.activity.runtime.state.value.persisted.refuelHistory.single())
        compose.runOnIdle{compose.activity.sources.duplicate()}
        compose.waitForIdle()
        assertEquals(1,compose.activity.runtime.state.value.persisted.refuelHistory.size)
    }
    @Test fun deleteNeedsConfirmationAndDoesNotRecreateARepeatedEvent() {
        addOneRecord()
        compose.onNodeWithTag("history_delete").performClick()
        compose.onNodeWithTag("history_cancel_delete").performClick()
        compose.onNodeWithTag("history_record").assertIsDisplayed()
        compose.onNodeWithTag("history_delete").performClick()
        compose.onNodeWithTag("history_confirm_delete").performClick()
        waitFor{compose.activity.runtime.state.value.persisted.refuelHistory.isEmpty()}
        compose.onNodeWithTag("history_empty").assertIsDisplayed()
        compose.runOnIdle{compose.activity.sources.duplicate()}
        compose.waitForIdle()
        assertTrue(compose.activity.runtime.state.value.persisted.refuelHistory.isEmpty())
    }
    @Test fun departureClosesHistoryAndItsConfirmationWithoutDeletingRecords() {
        addOneRecord()
        compose.onNodeWithTag("history_delete").performClick()
        compose.runOnIdle{compose.activity.sources.motion.value=Motion.Moving}
        waitFor{compose.activity.runtime.state.value.ui.motion==Motion.Moving}
        compose.onNodeWithTag("history_screen").assertDoesNotExist()
        compose.onNodeWithTag("history_confirm_delete").assertDoesNotExist()
        compose.onNodeWithTag("nav_records").assertDoesNotExist()
        assertEquals(1,compose.activity.runtime.state.value.persisted.refuelHistory.size)
    }
}
