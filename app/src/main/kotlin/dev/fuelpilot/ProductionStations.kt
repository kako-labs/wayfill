package dev.fuelpilot

import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.data.poi.gateway.*
import dev.fuelpilot.model.*
import kotlinx.coroutines.CancellationException

/** Implement only after the provider's datum/projection/axes and both directions are verified. */
fun interface VerifiedStationCoordinateTransform {
    suspend fun toKatec(location: VehicleLocation): StationCoordinates?
}

object UnverifiedStationCoordinateTransform : VerifiedStationCoordinateTransform {
    override suspend fun toKatec(location: VehicleLocation): StationCoordinates? = null
}

/** Immutable context must be echoed by the SDK adapter; null route means confirmed no route. */
data class RoadMetricsRequest(
    val origin: VehicleLocation,
    val route: ActiveRoute?,
    val catalog: RawStationCatalog,
)

/** These fields must come from actual navigation routing, never providerDistanceMeters. */
data class VerifiedStationRoadMetric(
    val stationId: String,
    val reachable: Boolean,
    val roadDistanceKm: Double,
    val alongRouteKm: Double?,
    val etaMinutes: Int,
    val detourMinutes: Int?,
    val navigationTarget: VerifiedStationNavigationTarget? = null,
)

data class VerifiedRoadMetricsSnapshot(
    val request: RoadMetricsRequest,
    val measuredAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val stations: List<VerifiedStationRoadMetric>,
    /** Proof of relevant route-corridor coverage, beyond merely routing nearby POIs. */
    val routeCoverageVerified: Boolean = false,
)

fun interface StationRoadMetrics {
    /** null means unsupported/unavailable. Implementations must propagate coroutine cancellation. */
    suspend fun measure(request: RoadMetricsRequest): VerifiedRoadMetricsSnapshot?
}

object UnsupportedStationRoadMetrics : StationRoadMetrics {
    override suspend fun measure(request: RoadMetricsRequest): VerifiedRoadMetricsSnapshot? = null
}

enum class ProductionStationFailure {
    FUEL_UNVERIFIED, LOCATION_UNAVAILABLE, ROUTE_UNAVAILABLE, COORDINATE_TRANSFORM_UNVERIFIED,
    DISCOVERY_UNAVAILABLE, STALE_CATALOG, INVALID_CATALOG,
    ROAD_METRICS_UNAVAILABLE, INVALID_ROAD_METRICS, STALE_ROAD_METRICS, CONTEXT_CHANGED, ROUTE_COVERAGE_UNVERIFIED,
}

sealed interface ProductionStationRead {
    data class Available(val stations: List<FuelStationCandidate>) : ProductionStationRead
    data class Unavailable(
        val reason: ProductionStationFailure,
        val discoveryReason: DiscoveryFailure? = null,
    ) : ProductionStationRead
}

/** Coordinator passes the exact location/route used by the decision input. */
interface ContextualStationRepository : StationRepository {
    suspend fun findStationsFor(origin: VehicleLocation, route: ActiveRoute?): StationResult
}

fun createProductionStations(
    location: LocationRepository,
    navigation: NavigationRepository,
    discovery: StationDiscovery,
    roadMetrics: StationRoadMetrics = UnsupportedStationRoadMetrics,
    coordinateTransform: VerifiedStationCoordinateTransform = UnverifiedStationCoordinateTransform,
    fuel: StationFuel? = null,
    epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
): ProductionStationRepository = ProductionStationRepository(
    location, navigation, discovery, roadMetrics, coordinateTransform, fuel, epochSeconds,
)

/** The sole production join from a raw source catalog into decision-engine candidates. */
class ProductionStationRepository internal constructor(
    private val location: LocationRepository,
    private val navigation: NavigationRepository,
    private val discovery: StationDiscovery,
    private val roadMetrics: StationRoadMetrics,
    private val coordinateTransform: VerifiedStationCoordinateTransform,
    private val fuel: StationFuel?,
    private val epochSeconds: () -> Long,
) : ContextualStationRepository {
    override suspend fun findStations(): StationResult = when (val read = readStations()) {
        is ProductionStationRead.Available -> StationResult.Available(read.stations)
        is ProductionStationRead.Unavailable -> StationResult.Unavailable
    }

    override suspend fun findStationsFor(origin: VehicleLocation, route: ActiveRoute?): StationResult =
        when (val read = readStationsFor(origin, route)) {
            is ProductionStationRead.Available -> StationResult.Available(read.stations)
            is ProductionStationRead.Unavailable -> StationResult.Unavailable
        }

    suspend fun readStationsFor(origin: VehicleLocation, route: ActiveRoute?): ProductionStationRead =
        readStations(RoadContext(origin, route))

    suspend fun readStations(): ProductionStationRead = readStations(null)

    private data class RoadContext(val origin: VehicleLocation, val route: ActiveRoute?)

    private suspend fun readStations(expected: RoadContext?): ProductionStationRead {
        fun fail(reason: ProductionStationFailure, discoveryReason: DiscoveryFailure? = null) =
            ProductionStationRead.Unavailable(reason, discoveryReason)
        try {
            val verifiedFuel = fuel ?: return fail(ProductionStationFailure.FUEL_UNVERIFIED)
            val origin = (location.readLocation() as? LocationResult.Available)?.location
                ?: return fail(ProductionStationFailure.LOCATION_UNAVAILABLE)
            if (!validLocation(origin)) return fail(ProductionStationFailure.LOCATION_UNAVAILABLE)
            val routeRead = navigation.readRoute() as? RouteResult.Available
                ?: return fail(ProductionStationFailure.ROUTE_UNAVAILABLE)
            val route = routeRead.route
            if (expected != null && (origin != expected.origin || route != expected.route))
                return fail(ProductionStationFailure.CONTEXT_CHANGED)
            if (route != null && (route.id.isBlank() || !validDistance(route.remainingDistanceKm)))
                return fail(ProductionStationFailure.ROUTE_UNAVAILABLE)
            val coordinates = coordinateTransform.toKatec(origin)
                ?: return fail(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED)
            if (coordinates.system != CoordinateSystem.KATEC || !coordinates.isValid())
                return fail(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED)
            val catalog = when (val read = discovery.discover(StationDiscoveryQuery(coordinates, 5000, verifiedFuel))) {
                is StationDiscoveryResult.Unavailable -> return fail(ProductionStationFailure.DISCOVERY_UNAVAILABLE, read.reason)
                is StationDiscoveryResult.Available -> read.catalog.copy(stations = read.catalog.stations.toList())
            }
            if (!fresh(catalog.fetchedAtEpochSeconds, catalog.expiresAtEpochSeconds, 300))
                return fail(ProductionStationFailure.STALE_CATALOG)
            if (!validCatalog(catalog)) return fail(ProductionStationFailure.INVALID_CATALOG)
            val request = RoadMetricsRequest(origin, route, catalog)
            val measured = roadMetrics.measure(request)
                ?: return fail(ProductionStationFailure.ROAD_METRICS_UNAVAILABLE)
            if (measured.request != request) return fail(ProductionStationFailure.INVALID_ROAD_METRICS)
            if (route != null && !measured.routeCoverageVerified)
                return fail(ProductionStationFailure.ROUTE_COVERAGE_UNVERIFIED)
            if (!fresh(measured.measuredAtEpochSeconds, measured.expiresAtEpochSeconds, 30))
                return fail(ProductionStationFailure.STALE_ROAD_METRICS)
            val rows = measured.stations.toList()
            if (rows.map { it.stationId }.toSet() != catalog.stations.map { it.id }.toSet() ||
                rows.map { it.stationId }.distinct().size != rows.size || rows.any { !validMetric(it, route) })
                return fail(ProductionStationFailure.INVALID_ROAD_METRICS)
            // Suspended network/routing work cannot publish a result for an old route or origin.
            if (location.readLocation() != LocationResult.Available(origin) || navigation.readRoute() != routeRead)
                return fail(ProductionStationFailure.CONTEXT_CHANGED)
            if (!fresh(catalog.fetchedAtEpochSeconds, catalog.expiresAtEpochSeconds, 300))
                return fail(ProductionStationFailure.STALE_CATALOG)
            if (!fresh(measured.measuredAtEpochSeconds, measured.expiresAtEpochSeconds, 30))
                return fail(ProductionStationFailure.STALE_ROAD_METRICS)
            val catalogById = catalog.stations.associateBy { it.id }
            return ProductionStationRead.Available(rows.filter { it.reachable }.map { metric ->
                val raw = catalogById.getValue(metric.stationId)
                FuelStationCandidate(raw.id, raw.name, metric.roadDistanceKm, metric.alongRouteKm,
                    metric.etaMinutes, metric.detourMinutes, OpenStatus.UNKNOWN,
                    fuelCompatible = raw.fuel == fuel, businessInfoFresh = false,
                    navigationTarget = metric.navigationTarget)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return fail(ProductionStationFailure.DISCOVERY_UNAVAILABLE)
        }
    }

    private fun fresh(fetched: Long, expires: Long, maxAge: Long): Boolean {
        val now = epochSeconds()
        return fetched >= 0 && fetched <= now && expires > now && expires > fetched && expires - fetched <= maxAge
    }

    private fun validCatalog(catalog: RawStationCatalog): Boolean =
        catalog.provenance.let { it.provider == "OPINET" && it.endpoint == "aroundAll" &&
            it.coordinateSystem == CoordinateSystem.KATEC && !it.roadMetricsVerified } &&
            catalog.stations.size <= 200 && catalog.stations.map { it.id }.distinct().size == catalog.stations.size &&
            catalog.stations.all { it.id.isNotBlank() && it.id.length <= 64 && it.name.isNotBlank() &&
                it.name.length <= 200 && it.fuel == fuel && it.coordinates.system == CoordinateSystem.KATEC &&
                it.coordinates.isValid() && it.priceKrwPerLiter.isFinite() && it.priceKrwPerLiter in 1.0..100000.0 &&
                it.providerDistanceMeters.isFinite() && it.providerDistanceMeters in 0.0..100000.0 &&
                it.priceObservedAtEpochSeconds == null }

    private fun validMetric(metric: VerifiedStationRoadMetric, route: ActiveRoute?): Boolean =
        validDistance(metric.roadDistanceKm) && metric.etaMinutes >= 0 &&
            (metric.detourMinutes == null || metric.detourMinutes >= 0) &&
            (metric.navigationTarget == null ||
                (metric.navigationTarget.stationId == metric.stationId && metric.navigationTarget.isValid())) &&
            if (route == null) metric.alongRouteKm == null && metric.detourMinutes == null
            else metric.alongRouteKm?.let { validDistance(it) && it <= route.remainingDistanceKm } == true &&
                metric.detourMinutes != null

    private fun validDistance(value: Double) = value.isFinite() && value >= 0
    private fun validLocation(value: VehicleLocation) = value.latitude.isFinite() && value.latitude in -90.0..90.0 &&
        value.longitude.isFinite() && value.longitude in -180.0..180.0 &&
        (value.accuracyMeters?.let { it.isFinite() && it >= 0 } ?: true)
}
