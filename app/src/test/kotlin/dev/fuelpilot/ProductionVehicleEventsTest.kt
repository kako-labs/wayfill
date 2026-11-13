package dev.fuelpilot

import dev.fuelpilot.data.energy.GearSelection
import dev.fuelpilot.data.energy.UxRestrictionState
import dev.fuelpilot.data.energy.VehicleSafetySnapshot
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.ui.Motion
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionVehicleEventsTest {
    @Test fun parkedRequiresParkAndKnownUnrestrictedUxState() {
        assertEquals(Motion.Parked, motion(GearSelection.PARK, UxRestrictionState.NOT_RESTRICTED))
        assertEquals(Motion.Unknown, motion(GearSelection.PARK, UxRestrictionState.UNKNOWN))
    }

    @Test fun activeUxRestrictionAlwaysFailsClosedAsMoving() {
        GearSelection.entries.forEach { gear ->
            assertEquals(Motion.Moving, motion(gear, UxRestrictionState.RESTRICTED, active = 511))
        }
    }

    @Test fun driveReverseAndNeutralAreMovingAndErrorsUnknown() {
        listOf(GearSelection.DRIVE, GearSelection.REVERSE, GearSelection.NEUTRAL).forEach {
            assertEquals(Motion.Moving, motion(it, UxRestrictionState.NOT_RESTRICTED))
        }
        assertEquals(Motion.Unknown, mapMotion(VehicleSignal.Unavailable))
        assertEquals(Motion.Unknown, mapMotion(VehicleSignal.Unsupported))
    }

    private fun motion(gear: GearSelection, ux: UxRestrictionState, active: Int? = 0) =
        mapMotion(VehicleSignal.Available(VehicleSafetySnapshot(gear, ux, active), 1L))
}
