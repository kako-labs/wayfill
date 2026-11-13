package dev.fuelpilot

import dev.fuelpilot.data.energy.VehicleFuelType
import dev.fuelpilot.data.energy.VehicleFuelTypeSource
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.data.poi.gateway.StationFuel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStationFuelTest {
    @Test fun singleLiquidFuelMapsToTheProviderProduct() {
        assertEquals(StationFuel.GASOLINE, stationFuelFor(setOf(VehicleFuelType.UNLEADED)))
        assertEquals(StationFuel.DIESEL, stationFuelFor(setOf(VehicleFuelType.DIESEL)))
        assertEquals(StationFuel.DIESEL, stationFuelFor(setOf(VehicleFuelType.BIODIESEL)))
        assertEquals(StationFuel.LPG, stationFuelFor(setOf(VehicleFuelType.LPG)))
    }

    @Test fun pluginHybridIgnoresTheElectricPart() {
        assertEquals(StationFuel.GASOLINE, stationFuelFor(setOf(VehicleFuelType.UNLEADED, VehicleFuelType.ELECTRIC)))
    }

    @Test fun ambiguousOrNonLiquidVehiclesStayUnverified() {
        assertNull(stationFuelFor(setOf(VehicleFuelType.UNLEADED, VehicleFuelType.LPG)))
        assertNull(stationFuelFor(setOf(VehicleFuelType.ELECTRIC)))
        assertNull(stationFuelFor(setOf(VehicleFuelType.HYDROGEN)))
        assertNull(stationFuelFor(setOf(VehicleFuelType.LEADED)))
        assertNull(stationFuelFor(emptySet()))
    }

    @Test fun providerRetriesUntilAvailableAndThenKeepsTheStaticValue() = runTest {
        var reads = 0
        val responses = ArrayDeque<VehicleSignal<Set<VehicleFuelType>>>(listOf(
            VehicleSignal.Unavailable,
            VehicleSignal.Available(setOf(VehicleFuelType.DIESEL), 1L),
        ))
        val provider = VehicleStationFuelProvider(VehicleFuelTypeSource { reads++; responses.removeFirst() })
        assertNull(provider.read())
        assertEquals(StationFuel.DIESEL, provider.read())
        assertEquals(StationFuel.DIESEL, provider.read())
        assertEquals(2, reads)
    }
}
