package dev.fuelpilot.data.energy

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EnergyRepositoryTest {
    @Test
    fun outcomeTypesRemainDistinct() {
        val outcomes = listOf<EnergyResult>(
            EnergyResult.Available(rangeRemainingKm = 1.0),
            EnergyResult.Stale(rangeRemainingKm = 1.0),
            EnergyResult.Unavailable,
            EnergyResult.Unsupported,
        )

        assertEquals(4, outcomes.map { it::class }.toSet().size)
    }

    @Test
    fun defaultRepositoryReturnsUnsupportedWithoutPlaceholderRange() = runBlocking {
        val result = UnsupportedEnergyRepository.readEnergy()

        assertSame(EnergyResult.Unsupported, result)
    }

    @Test fun aaosRangeMapsMetersAndFreshTimestamp() = runBlocking {
        val repository = AaosRangeEnergyRepository(source(VehicleSignal.Available(123_400f, 90L)), { 100L }, 20L)

        val result = repository.readEnergy() as EnergyResult.Available
        assertEquals(123.4, result.rangeRemainingKm, 0.0001)
    }

    @Test fun aaosRangeRejectsInvalidAndDistinguishesStaleUnsupported() = runBlocking {
        assertSame(EnergyResult.Unavailable,
            AaosRangeEnergyRepository(source(VehicleSignal.Available(Float.NaN, 90L)), { 100L }).readEnergy())
        assertEquals(EnergyResult.Stale(1.0),
            AaosRangeEnergyRepository(source(VehicleSignal.Available(1000f, 1L)), { 100L }, 10L).readEnergy())
        assertSame(EnergyResult.Unsupported,
            AaosRangeEnergyRepository(source(VehicleSignal.Unsupported), { 100L }).readEnergy())
    }

    private fun source(read: VehicleSignal<Float>) = object : VehiclePropertySource {
        override suspend fun readRangeMeters() = read
        override fun observeSafety(): Flow<VehicleSignal<VehicleSafetySnapshot>> = emptyFlow()
    }
}
