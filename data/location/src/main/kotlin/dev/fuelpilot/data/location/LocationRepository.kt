package dev.fuelpilot.data.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
)

sealed interface LocationResult {
    data class Available(val location: VehicleLocation) : LocationResult

    data class Stale(val location: VehicleLocation) : LocationResult

    data object Unavailable : LocationResult

    data object Unsupported : LocationResult
}

interface LocationRepository {
    suspend fun readLocation(): LocationResult
}

object UnsupportedLocationRepository : LocationRepository {
    override suspend fun readLocation(): LocationResult = LocationResult.Unsupported
}

data class FusedLocationSample(
    val latitude: Double,
    val longitude: Double,
    val confidence: Double?,
    val timestampMillis: Long,
)

sealed interface FusedLocationRead {
    data class Available(val sample: FusedLocationSample) : FusedLocationRead
    data object Unavailable : FusedLocationRead
    data object Unsupported : FusedLocationRead
}

interface FusedLocationPort {
    fun isSupported(): Boolean
    fun hasPermission(): Boolean
    /** Returns only a newly delivered SDK update, never getLastKnownLocation data. */
    suspend fun awaitNextLocationUpdate(): FusedLocationRead
}

class PleosFusedLocationRepository(
    private val port: FusedLocationPort,
    private val readTimeoutMs: Long = 3_000L,
    private val maxAttempts: Int = 2,
    private val retryDelayMs: Long = 150L,
) : LocationRepository {
    init { require(readTimeoutMs > 0 && maxAttempts > 0 && retryDelayMs >= 0) }

    override suspend fun readLocation(): LocationResult {
        if (!port.isSupported() || !port.hasPermission()) return LocationResult.Unsupported
        repeat(maxAttempts) { attempt ->
            val read = try { withTimeoutOrNull(readTimeoutMs) { port.awaitNextLocationUpdate() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { FusedLocationRead.Unavailable }
                ?: FusedLocationRead.Unavailable
            when (read) {
                is FusedLocationRead.Available -> return map(read.sample)
                FusedLocationRead.Unsupported -> return LocationResult.Unsupported
                FusedLocationRead.Unavailable -> if (attempt + 1 < maxAttempts) delay(retryDelayMs)
            }
        }
        return LocationResult.Unavailable
    }

    private fun map(sample: FusedLocationSample): LocationResult {
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0 ||
            sample.confidence?.let { !it.isFinite() || it < 0.0 } == true
        ) return LocationResult.Unavailable
        // The SDK exposes timestampMillis but does not document its clock basis, so it is not used for freshness.
        // Confidence is not documented as meter accuracy, so it is not relabeled as accuracyMeters.
        return LocationResult.Available(VehicleLocation(sample.latitude, sample.longitude, accuracyMeters = null))
    }
}
