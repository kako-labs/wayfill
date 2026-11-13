package dev.fuelpilot

import dev.fuelpilot.data.energy.FuelLevelSample
import dev.fuelpilot.data.energy.FuelLevelSource
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.ui.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelDetectionTest {
    private fun tank(liters: Double) = FuelLevelSample((liters * 1000).toFloat(), 60_000f)

    @Test fun greetsOnceAfterPumpingStopsWithTheDeliveredAmount() {
        val detector = RefuelDetector { 1_000 }
        val levels = listOf(8.0, 8.0, 14.0, 26.0, 38.0, 57.5, 57.6, 57.6, 57.6)
        val events = levels.mapNotNull { detector.update(Motion.Parked, tank(it)) }
        val event = events.single()
        assertEquals("refuel-1000", event.id)
        assertEquals(49.6, event.liters!!, 1e-3)
        assertTrue(event.isFull)
        assertTrue(event.wasLow)
        assertNull(event.rangeKm)
    }

    @Test fun noGreetingWhilePumpingOrForSmallNoise() {
        val detector = RefuelDetector()
        listOf(30.0, 30.2, 29.9, 30.1, 30.0).forEach { assertNull(detector.update(Motion.Parked, tank(it))) }
        val pumping = RefuelDetector()
        listOf(20.0, 23.5, 27.0, 30.5).forEach { assertNull(pumping.update(Motion.Parked, tank(it))) }
    }

    @Test fun leavingParkOrLosingTheReadingEndsTheStop() {
        val detector = RefuelDetector()
        detector.update(Motion.Parked, tank(10.0))
        detector.update(Motion.Parked, tank(30.0))
        assertNull(detector.update(Motion.Moving, tank(30.0)))
        // A new stop starts from the current level, so the earlier rise is never greeted.
        listOf(30.0, 30.0, 30.0).forEach { assertNull(detector.update(Motion.Parked, tank(it))) }
        detector.update(Motion.Parked, null)
        assertNull(detector.update(Motion.Parked, tank(31.0)))
    }

    @Test fun anEvDoesNotProduceEvents() = runTest {
        val unsupported = FuelLevelSource { VehicleSignal.Unsupported }
        val events = refuelEvents(flowOf(Motion.Parked), unsupported, pollMs = 1_000).take(1)
        val collected = kotlinx.coroutines.withTimeoutOrNull(10_000) { events.toList() }
        assertNull(collected)
    }

    @Test fun parkedPollingEmitsTheDetectedRefuel() = runTest {
        val readings = ArrayDeque(listOf(10.0, 25.0, 40.0, 40.1, 40.1).map { VehicleSignal.Available(tank(it), 1L) })
        val source = FuelLevelSource { readings.removeFirstOrNull() ?: VehicleSignal.Available(tank(40.1), 1L) }
        val event = refuelEvents(flowOf(Motion.Parked), source, RefuelDetector { 7 }, pollMs = 5_000).first()
        assertEquals("refuel-7", event.id)
        assertEquals(30.1, event.liters!!, 1e-3)
    }
}
