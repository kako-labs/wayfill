package dev.fuelpilot.data.location

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LocationRepositoryTest {
    @Test
    fun outcomeTypesRemainDistinct() {
        val location = VehicleLocation(latitude = 1.0, longitude = 1.0)
        val outcomes = listOf<LocationResult>(
            LocationResult.Available(location),
            LocationResult.Stale(location),
            LocationResult.Unavailable,
            LocationResult.Unsupported,
        )

        assertEquals(4, outcomes.map { it::class }.toSet().size)
    }

    @Test
    fun defaultRepositoryReturnsUnsupportedWithoutPlaceholderCoordinates() = runBlocking {
        val result = UnsupportedLocationRepository.readLocation()

        assertSame(LocationResult.Unsupported, result)
    }
}
