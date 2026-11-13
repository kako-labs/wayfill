package dev.fuelpilot

import dev.fuelpilot.data.energy.FuelLevelSample
import dev.fuelpilot.data.energy.FuelLevelSource
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive

/**
 * Candidate refuel detector, DISABLED by default (see FuelPilotApplication). It has not been validated on a
 * real combustion vehicle: the PLEOS emulator is EV-only and exposes no fuel door signal.
 *
 * A refuel is confirmed only within one parked stop: the tank must rise by at least [minRiseLiters] over
 * the level first seen in that stop, and then hold still (within [stableToleranceLiters]) for
 * [stableSamples] consecutive samples, so the greeting comes after pumping ends. Range is never used.
 * Leaving Park or losing the fuel reading ends the stop. One event per stop at most.
 */
class RefuelDetector(
    private val minRiseLiters: Double = 3.0,
    private val stableToleranceLiters: Double = 0.3,
    private val stableSamples: Int = 2,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    init { require(minRiseLiters > 0 && stableToleranceLiters >= 0 && stableSamples > 0) }

    private var baselineMl: Double? = null
    private var lastMl: Double? = null
    private var stableCount = 0
    private var emitted = false

    fun update(motion: Motion, sample: FuelLevelSample?): RefuelEvent? {
        if (motion != Motion.Parked || sample == null) {
            baselineMl = null; lastMl = null; stableCount = 0; emitted = false
            return null
        }
        val level = sample.levelMl.toDouble()
        val base = baselineMl ?: level.also { baselineMl = it; lastMl = it }
        val previous = lastMl ?: level
        lastMl = level
        if (emitted) return null
        if (level < base) { baselineMl = level; stableCount = 0; return null }
        val risen = (level - base) / 1000.0 >= minRiseLiters
        stableCount = if (risen && kotlin.math.abs(level - previous) / 1000.0 <= stableToleranceLiters) stableCount + 1 else 0
        if (stableCount < stableSamples) return null
        emitted = true
        val capacity = sample.capacityMl.toDouble()
        return RefuelEvent(
            id = "refuel-${epochSeconds()}",
            liters = (level - base) / 1000.0,
            rangeKm = null,
            isFull = level >= capacity * 0.95,
            wasLow = base <= capacity * 0.15,
        )
    }
}

/** Samples the tank only while parked; any other motion ends the stop inside the detector. */
@OptIn(ExperimentalCoroutinesApi::class)
fun refuelEvents(
    motion: Flow<Motion>,
    fuel: FuelLevelSource,
    detector: RefuelDetector = RefuelDetector(),
    pollMs: Long = 5_000L,
): Flow<RefuelEvent> = motion.flatMapLatest { current ->
    if (current != Motion.Parked) flowOf<Pair<Motion, FuelLevelSample?>>(current to null)
    else flow<Pair<Motion, FuelLevelSample?>> {
        while (currentCoroutineContext().isActive) {
            emit(current to (fuel.readFuelLevel() as? VehicleSignal.Available)?.value)
            delay(pollMs)
        }
    }
}.mapNotNull { (current, sample) -> detector.update(current, sample) }
