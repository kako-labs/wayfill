package dev.fuelpilot.data.poi.gateway

/** A catalog is geographic source data, never a FuelStationCandidate or a reachability claim. */
enum class CoordinateSystem { KATEC, WGS84 }
enum class StationFuel(val providerCode: String) {
    GASOLINE("B027"), DIESEL("D047"), PREMIUM_GASOLINE("B034"), KEROSENE("C004"), LPG("K015");
    companion object { fun fromCode(code: String): StationFuel = entries.first { it.providerCode == code } }
}
data class StationCoordinates(val system: CoordinateSystem, val x: Double, val y: Double) {
    fun isValid(): Boolean = x.isFinite() && y.isFinite() && when (system) {
        CoordinateSystem.KATEC -> x in 0.0..2_000_000.0 && y in 0.0..2_000_000.0
        CoordinateSystem.WGS84 -> x in -180.0..180.0 && y in -90.0..90.0
    }
}
data class StationDiscoveryQuery(val coordinates: StationCoordinates, val radiusMeters: Int, val fuel: StationFuel)
data class RawCatalogStation(
    val id: String,
    val name: String,
    val fuel: StationFuel,
    val priceKrwPerLiter: Double,
    val coordinates: StationCoordinates,
    /** Source-provided distance: never road distance or along-route progress. */
    val providerDistanceMeters: Double,
    /** The aroundAll API supplies no price observation timestamp. Fetch time is not observation time. */
    val priceObservedAtEpochSeconds: Long?,
)
data class StationProvenance(
    val provider: String,
    val endpoint: String,
    val coordinateSystem: CoordinateSystem,
    val roadMetricsVerified: Boolean,
    val cacheHit: Boolean,
)
data class RawStationCatalog(
    val stations: List<RawCatalogStation>,
    val fetchedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val provenance: StationProvenance,
)
enum class DiscoveryFailure { UNCONFIGURED, INVALID_QUERY, COORDINATE_TRANSFORM_UNVERIFIED, QUOTA_EXHAUSTED, STALE, INVALID_RESPONSE, NETWORK }
sealed interface StationDiscoveryResult {
    data class Available(val catalog: RawStationCatalog) : StationDiscoveryResult
    data class Unavailable(val reason: DiscoveryFailure) : StationDiscoveryResult
}
interface StationDiscovery {
    suspend fun discover(query: StationDiscoveryQuery): StationDiscoveryResult
}
