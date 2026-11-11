package dev.fuelpilot.data.energy

import kotlinx.coroutines.runBlocking
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
}
