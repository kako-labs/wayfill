package dev.fuelpilot.ui

import org.junit.Assert.*
import org.junit.Test

class FuelUiStateTest {
    private val fuel = RefuelEvent("receipt-1", 42.0, 610.0, true)
    private fun parked() = FuelUiState(motion = Motion.Parked)
    @Test fun confirmedRefuelPlaysOnce() {
        val first = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(fuel))
        assertEquals("receipt-1", first.celebration?.id)
        val done = reduceFuelUi(first, FuelUiEvent.CelebrationFinished(fuel.id))
        assertNull(reduceFuelUi(done, FuelUiEvent.RefuelConfirmed(fuel)).celebration)
    }
    @Test fun movingInterruptsWithoutReplayingWhenParked() {
        val first = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(fuel))
        val moving = reduceFuelUi(first, FuelUiEvent.MotionChanged(Motion.Moving))
        assertNull(moving.celebration); assertEquals(Screen.Widget, moving.screen)
        assertNull(reduceFuelUi(moving, FuelUiEvent.MotionChanged(Motion.Parked)).celebration)
    }
    @Test fun unknownConsumesEventWithoutLargeAnimation() {
        val unknown = reduceFuelUi(FuelUiState(), FuelUiEvent.RefuelConfirmed(fuel))
        assertNull(unknown.celebration); assertTrue(unknown.seenIds.contains(fuel.id))
    }
    @Test fun waypointMustBeConfirmedAndMatchPendingId() {
        val pending = reduceFuelUi(parked(), FuelUiEvent.SelectStation("A"))
        assertEquals(Screen.Adding, pending.screen); assertNull(pending.routeStation)
        assertNull(reduceFuelUi(pending, FuelUiEvent.RouteAdded("B")).routeStation)
        assertEquals("A", reduceFuelUi(pending, FuelUiEvent.RouteAdded("A")).routeStation)
    }
    @Test fun turningCharacterOffKeepsRefuelConfirmation() {
        val first = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(fuel))
        val disabled = reduceFuelUi(first, FuelUiEvent.CharacterChanged(false))
        assertNull(disabled.celebration); assertEquals(fuel, disabled.refuel)
    }
    @Test fun missingOrInvalidVolumeIsNotInvented() {
        val state = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(RefuelEvent("missing", Double.NaN)))
        assertNotNull(state.refuel); assertNull(state.refuel?.liters)
    }
    @Test fun remountUsesOriginalStartTimeRatherThanRestarting() {
        val first = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(fuel), nowMs = 1000)
        assertEquals(1000L, first.celebration!!.startedAtMs)
        assertEquals(Pose.Cheek, celebrationPose(first.celebration, nowMs = 1900))
        val duplicate = reduceFuelUi(first, FuelUiEvent.RefuelConfirmed(fuel), nowMs = 2200)
        assertEquals(first.celebration, duplicate.celebration)
        assertNull(celebrationPose(duplicate.celebration!!, nowMs = 3400))
    }
    @Test fun characterRemainsAfterTheDanceAndWhileMoving() {
        val initial = reduceFuelUi(parked(), FuelUiEvent.RefuelConfirmed(fuel))
        val done = reduceFuelUi(initial, FuelUiEvent.CelebrationFinished(fuel.id))
        assertTrue(characterPresentation(done).visible)
        val moving = reduceFuelUi(done, FuelUiEvent.MotionChanged(Motion.Moving))
        assertTrue(characterPresentation(moving).visible); assertFalse(characterPresentation(moving).playful)
        assertTrue(characterPresentation(FuelUiState(characterEnabled = false)).visible)
    }
}
