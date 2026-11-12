package dev.fuelpilot.data.location

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlinx.coroutines.test.runTest

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

    @Test fun unsupportedOrMissingPermissionFailsClosed() = runTest {
        assertSame(LocationResult.Unsupported, PleosFusedLocationRepository(port(supported = false)).readLocation())
        assertSame(LocationResult.Unsupported, PleosFusedLocationRepository(port(permission = false)).readLocation())
    }

    @Test fun validatesCoordinatesAndDoesNotGuessTimestampClock() = runTest {
        val sample = FusedLocationSample(37.5, 127.0, 4.5, timestampMillis = 1L)
        assertEquals(LocationResult.Available(VehicleLocation(37.5, 127.0, accuracyMeters = null)),
            PleosFusedLocationRepository(port(read = FusedLocationRead.Available(sample))).readLocation())
        assertSame(LocationResult.Unavailable,
            PleosFusedLocationRepository(port(read = FusedLocationRead.Available(sample.copy(latitude = 91.0)))).readLocation())
        assertSame(LocationResult.Unavailable,
            PleosFusedLocationRepository(port(read = FusedLocationRead.Available(sample.copy(confidence = -1.0)))).readLocation())
    }

    @Test fun retriesUnavailableOnlyWithinBound() = runTest {
        var calls = 0
        val port = object : FusedLocationPort {
            override fun isSupported() = true
            override fun hasPermission() = true
            override suspend fun awaitNextLocationUpdate(): FusedLocationRead {
                calls++
                return FusedLocationRead.Unavailable
            }
        }
        assertSame(LocationResult.Unavailable,
            PleosFusedLocationRepository(port, readTimeoutMs = 100, maxAttempts = 2, retryDelayMs = 0).readLocation())
        assertEquals(2, calls)
    }

    private fun port(
        supported: Boolean = true,
        permission: Boolean = true,
        read: FusedLocationRead = FusedLocationRead.Unavailable,
    ) = object : FusedLocationPort {
        override fun isSupported() = supported
        override fun hasPermission() = permission
        override suspend fun awaitNextLocationUpdate() = read
    }
}
