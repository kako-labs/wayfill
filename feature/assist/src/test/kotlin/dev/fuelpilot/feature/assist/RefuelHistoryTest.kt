package dev.fuelpilot.feature.assist

import dev.fuelpilot.ui.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RefuelHistoryTest {
    private val now=100L*24*60*60*1000
    private fun step(state:AssistViewState,intent:AssistIntent)=reduceAssist(state,intent,now,1000,"request").state
    private fun initial(motion:Motion=Motion.Parked)=AssistViewState(ready=true,ui=FuelUiState(motion=motion))

    @Test fun confirmedEventIsSanitizedAndDuplicateDoesNotRewriteOrAddHistory() {
        val first=step(initial(),AssistIntent.RefuelConfirmed(RefuelEvent("event",Double.NaN,-10.0,true)))
        assertEquals(listOf(RefuelRecord("event",now,isFull=true)),first.persisted.refuelHistory)
        val duplicate=step(first,AssistIntent.RefuelConfirmed(RefuelEvent("event",50.0,600.0)))
        assertEquals(first.persisted.refuelHistory,duplicate.persisted.refuelHistory)
    }
    @Test fun movingConfirmedEventIsRecordedWithoutBroadActing() {
        val state=step(initial(Motion.Moving),AssistIntent.RefuelConfirmed(RefuelEvent("moving",5.0,100.0)))
        assertEquals(1,state.persisted.refuelHistory.size)
        assertNull(state.ui.celebration)
        assertNull(state.ui.refuel)
    }
    @Test fun deletingHistoryKeepsDeduplicationAndCannotReplayTheSameEvent() {
        val first=step(initial(),AssistIntent.RefuelConfirmed(RefuelEvent("one",5.0)))
        val clear=step(first.copy(ui=first.ui.copy(refuel=null,celebration=null)),AssistIntent.ClearRefuelHistory)
        assertTrue(clear.persisted.refuelHistory.isEmpty())
        assertTrue("one" in clear.persisted.processedRefuels)
        val repeat=step(clear,AssistIntent.RefuelConfirmed(RefuelEvent("one",5.0)))
        assertTrue(repeat.persisted.refuelHistory.isEmpty())
        assertNull(repeat.ui.celebration)
    }
    @Test fun historyDeletionIsRejectedWhenMovingOrUnknown() {
        for(motion in listOf(Motion.Moving,Motion.Unknown)) {
            val state=initial(motion).copy(persisted=AssistStoredState(refuelHistory=listOf(RefuelRecord("one",now))))
            assertEquals(state.persisted,step(state,AssistIntent.ClearRefuelHistory).persisted)
        }
    }
    @Test fun historyHasDeterministicRetentionCapAndSafeOptionalNumbers() {
        val old=RefuelRecord("old",now-HISTORY_RETENTION_MS-1)
        val boundary=RefuelRecord("boundary",now-HISTORY_RETENTION_MS)
        val records=(0..120).map{RefuelRecord("id-%03d".format(it),now,Double.POSITIVE_INFINITY,-1.0)}
        val kept=retainedRefuelHistory(records+old+boundary+records[0],now)
        assertEquals(100,kept.size)
        assertEquals("id-000",kept.first().id)
        assertEquals("id-099",kept.last().id)
        assertTrue(kept.all{it.liters==null&&it.rangeKm==null})
        assertEquals(listOf(boundary),retainedRefuelHistory(listOf(old,boundary),now))
    }
    @Test fun codecPreservesDelimiterUnicodeIdsAndRejectsMalformedOrFutureSchema() {
        val record=RefuelRecord("주유|:\n한글",now,5.5,600.0,true,true)
        assertEquals(record,RefuelRecordCodec.decode(RefuelRecordCodec.encode(record)))
        for(value in listOf("garbage","2|1|YQ|||0|0","1|-1|YQ|||0|0","1|1|@@|||0|0","1|1|YQ|||yes|0"))
            assertNull(value,RefuelRecordCodec.decode(value))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun failedHistoryDeletionKeepsTheLastDurableRecordsAndReportsStorageFailure()=runTest {
        val record=RefuelRecord("one",now,5.0,100.0)
        val saved=AssistStoredState(processedRefuels=mapOf("one" to now),refuelHistory=listOf(record))
        val store=object:AssistStateStore {
            override suspend fun read()=saved
            override suspend fun write(state:AssistStoredState){error("disk unavailable")}
        }
        val vm=AssistViewModel(store,scope=backgroundScope,wallClockMs={now})
        runCurrent()
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked));runCurrent()
        vm.dispatch(AssistIntent.ClearRefuelHistory);runCurrent()
        assertFalse(vm.state.value.ready)
        assertEquals(listOf(record),vm.state.value.persisted.refuelHistory)
        assertTrue("one" in vm.state.value.persisted.processedRefuels)
    }
}
