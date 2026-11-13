package dev.fuelpilot

import dev.fuelpilot.data.location.PleosFusedLocationPort
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPermissionsTest {
    @Test fun automotiveRequestsVehicleEnergyAndPleosLocation() {
        val requested = monitoringRuntimePermissions(automotive = true, sdkInt = 34)
        assertTrue(CAR_ENERGY_PERMISSION in requested)
        assertTrue(PleosFusedLocationPort.FUSED_LOCATION_PERMISSION in requested)
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
    }

    @Test fun nonAutomotiveDoesNotRequestCarPermissions() {
        val requested = monitoringRuntimePermissions(automotive = false, sdkInt = 34)
        assertFalse(CAR_ENERGY_PERMISSION in requested)
        assertFalse(PleosFusedLocationPort.FUSED_LOCATION_PERMISSION in requested)
    }
}
