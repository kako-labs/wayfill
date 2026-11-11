package dev.fuelpilot.data.location

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
