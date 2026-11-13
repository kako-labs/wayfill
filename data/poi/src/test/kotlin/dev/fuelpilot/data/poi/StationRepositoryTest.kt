package dev.fuelpilot.data.poi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StationRepositoryTest {
    @Test
    fun outcomeTypesRemainDistinct() {
        val outcomes = listOf<StationResult>(
            StationResult.Available(emptyList()),
            StationResult.Stale(emptyList()),
            StationResult.Unavailable,
            StationResult.Unsupported,
        )

        assertEquals(4, outcomes.map { it::class }.toSet().size)
    }

    @Test
    fun defaultRepositoryReturnsUnsupportedWithoutPlaceholderStations() = runBlocking {
        val result = UnsupportedStationRepository.findStations()

        assertSame(StationResult.Unsupported, result)
    }
}
