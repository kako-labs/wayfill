package dev.fuelpilot

import dev.fuelpilot.data.energy.FuelLevelSource
import dev.fuelpilot.data.energy.GearSelection
import dev.fuelpilot.data.energy.UxRestrictionState
import dev.fuelpilot.data.energy.VehiclePropertySource
import dev.fuelpilot.data.energy.VehicleSafetySnapshot
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AaosVehicleEvents(
    source: VehiclePropertySource,
    fuelLevel: FuelLevelSource? = null,
    /** Off until RefuelDetector is validated on a real combustion vehicle. */
    refuelDetectionEnabled: Boolean = false,
) : VehicleEvents {
    override val motion: Flow<Motion> = source.observeSafety()
        .map(::mapMotion)
        .onStart { emit(Motion.Unknown) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(Motion.Unknown)
        }

    // Range changes are never refuel proof. The tank-level candidate runs only when explicitly enabled.
    override val refuels: Flow<RefuelEvent> =
        if (refuelDetectionEnabled && fuelLevel != null) refuelEvents(motion, fuelLevel)
            .catch { failure -> if (failure is CancellationException) throw failure }
        else emptyFlow()
}

internal fun mapMotion(read: VehicleSignal<VehicleSafetySnapshot>): Motion = when (read) {
    is VehicleSignal.Available -> when {
        read.value.uxRestrictions == UxRestrictionState.RESTRICTED -> Motion.Moving
        read.value.uxRestrictions == UxRestrictionState.UNKNOWN -> Motion.Unknown
        read.value.gear == GearSelection.PARK -> Motion.Parked
        read.value.gear in setOf(GearSelection.DRIVE, GearSelection.REVERSE, GearSelection.NEUTRAL) -> Motion.Moving
        else -> Motion.Unknown
    }
    VehicleSignal.Unavailable, VehicleSignal.Unsupported -> Motion.Unknown
}
