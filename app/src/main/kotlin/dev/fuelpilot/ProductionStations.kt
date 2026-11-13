package dev.fuelpilot

import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.data.poi.gateway.*
import dev.fuelpilot.model.*
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Implement only after the provider's datum/projection/axes and both directions are verified. */
fun interface VerifiedStationCoordinateTransform {
    suspend fun toKatec(location: VehicleLocation): StationCoordinates?
}

object UnverifiedStationCoordinateTransform : VerifiedStationCoordinateTransform {
    override suspend fun toKatec(location: VehicleLocation): StationCoordinates? = null
}

/**
 * KATEC transform validated against live Opinet rows and independent OSM positions (see [KatecProjection]).
 * Only the validated Korean extent is converted; anywhere else stays unverified instead of extrapolating.
 */
object ValidatedKatecTransform : VerifiedStationCoordinateTransform {
    private val latitudes = 32.5..39.0
    private val longitudes = 124.0..132.0

    override suspend fun toKatec(location: VehicleLocation): StationCoordinates? {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in latitudes || location.longitude !in longitudes) return null
        val (x, y) = KatecProjection.fromWgs84(location.latitude, location.longitude)
        return StationCoordinates(CoordinateSystem.KATEC, x, y).takeIf { it.isValid() }
    }
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
    /** Read when [fuel] is null, e.g. from the vehicle's own INFO_FUEL_TYPE. Null result stays unverified. */
    fuelSource: (suspend () -> StationFuel?)? = null,
    /** Without a route, only this many nearest catalog rows are road-measured (each costs a trip preview). */
    maxNearbyMeasured: Int = 6,
    /** With a route, stations and road metrics come from this routed corridor instead of a nearby catalog. */
    routeCorridor: RouteCorridorSource? = null,
): ProductionStationRepository = ProductionStationRepository(
    location, navigation, discovery, roadMetrics, coordinateTransform, fuel, epochSeconds, fuelSource, maxNearbyMeasured,
    routeCorridor,
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
    private val fuelSource: (suspend () -> StationFuel?)? = null,
    private val maxNearbyMeasured: Int = 6,
    private val routeCorridor: RouteCorridorSource? = null,
) : ContextualStationRepository {
    init { require(maxNearbyMeasured > 0) }

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
            val verifiedFuel = fuel ?: fuelSource?.invoke() ?: return fail(ProductionStationFailure.FUEL_UNVERIFIED)
            val origin = (location.readLocation() as? LocationResult.Available)?.location
                ?: return fail(ProductionStationFailure.LOCATION_UNAVAILABLE)
            if (!validLocation(origin)) return fail(ProductionStationFailure.LOCATION_UNAVAILABLE)
            val routeRead = navigation.readRoute() as? RouteResult.Available
                ?: return fail(ProductionStationFailure.ROUTE_UNAVAILABLE)
            val route = routeRead.route
            if (expected != null && !sameTrip(expected.origin, expected.route, origin, route))
                return fail(ProductionStationFailure.CONTEXT_CHANGED)
            if (route != null && (route.id.isBlank() || !validDistance(route.remainingDistanceKm)))
                return fail(ProductionStationFailure.ROUTE_UNAVAILABLE)
            if (route != null && routeCorridor != null)
                return readCorridor(routeCorridor, verifiedFuel, origin, routeRead)
            val coordinates = coordinateTransform.toKatec(origin)
                ?: return fail(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED)
            if (coordinates.system != CoordinateSystem.KATEC || !coordinates.isValid())
                return fail(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED)
            val discovered = when (val read = discovery.discover(StationDiscoveryQuery(coordinates, 5000, verifiedFuel))) {
                is StationDiscoveryResult.Unavailable -> return fail(ProductionStationFailure.DISCOVERY_UNAVAILABLE, read.reason)
                is StationDiscoveryResult.Available -> read.catalog.copy(stations = read.catalog.stations.toList())
            }
            if (!fresh(discovered.fetchedAtEpochSeconds, discovered.expiresAtEpochSeconds, 300))
                return fail(ProductionStationFailure.STALE_CATALOG)
            if (!validCatalog(discovered, verifiedFuel)) return fail(ProductionStationFailure.INVALID_CATALOG)
            // Provider distance only chooses which nearby rows to measure; it never becomes a road metric.
            val catalog = if (route == null) discovered.copy(stations = discovered.stations
                .sortedBy { it.providerDistanceMeters }.take(maxNearbyMeasured)) else discovered
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
            if (!stillSameTrip(origin, routeRead)) return fail(ProductionStationFailure.CONTEXT_CHANGED)
            if (!fresh(catalog.fetchedAtEpochSeconds, catalog.expiresAtEpochSeconds, 300))
                return fail(ProductionStationFailure.STALE_CATALOG)
            if (!fresh(measured.measuredAtEpochSeconds, measured.expiresAtEpochSeconds, 30))
                return fail(ProductionStationFailure.STALE_ROAD_METRICS)
            val catalogById = catalog.stations.associateBy { it.id }
            return ProductionStationRead.Available(rows.filter { it.reachable }.map { metric ->
                val raw = catalogById.getValue(metric.stationId)
                FuelStationCandidate(raw.id, raw.name, metric.roadDistanceKm, metric.alongRouteKm,
                    metric.etaMinutes, metric.detourMinutes, OpenStatus.UNKNOWN,
                    fuelCompatible = raw.fuel == verifiedFuel, businessInfoFresh = false,
                    navigationTarget = metric.navigationTarget)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return fail(ProductionStationFailure.DISCOVERY_UNAVAILABLE)
        }
    }

    /**
     * Route mode: the gateway routes origin -> destination, proves the provider searches covered the whole
     * corridor, and measures each station with a routed via-station detour. Coverage is proven for that routed
     * path, so it is accepted for the navigation's own route only while both agree on the remaining distance.
     */
    private suspend fun readCorridor(
        corridor: RouteCorridorSource,
        fuel: StationFuel,
        origin: VehicleLocation,
        routeRead: RouteResult.Available,
    ): ProductionStationRead {
        fun fail(reason: ProductionStationFailure, discoveryReason: DiscoveryFailure? = null) =
            ProductionStationRead.Unavailable(reason, discoveryReason)
        val route = requireNotNull(routeRead.route)
        val latitude = route.destinationLatitude ?: return fail(ProductionStationFailure.ROAD_METRICS_UNAVAILABLE)
        val longitude = route.destinationLongitude ?: return fail(ProductionStationFailure.ROAD_METRICS_UNAVAILABLE)
        val query = RouteCorridorQuery(origin.latitude, origin.longitude, latitude, longitude, fuel,
            navigationRemainingMeters = route.remainingDistanceKm * 1000.0)
        val snapshot = when (val read = corridor.measure(query)) {
            is RouteCorridorResult.Available -> read.snapshot
            is RouteCorridorResult.Unavailable -> return when (read.reason) {
                CorridorFailure.QUOTA_EXHAUSTED ->
                    fail(ProductionStationFailure.DISCOVERY_UNAVAILABLE, DiscoveryFailure.QUOTA_EXHAUSTED)
                CorridorFailure.STALE -> fail(ProductionStationFailure.STALE_ROAD_METRICS)
                CorridorFailure.INVALID_RESPONSE -> fail(ProductionStationFailure.INVALID_ROAD_METRICS)
                else -> fail(ProductionStationFailure.ROAD_METRICS_UNAVAILABLE)
            }
        }
        if (!snapshot.coverageVerified) return fail(ProductionStationFailure.ROUTE_COVERAGE_UNVERIFIED)
        val navigationMeters = route.remainingDistanceKm * 1000.0
        if (abs(snapshot.remainingDistanceMeters - navigationMeters) >
            max(ROUTE_AGREEMENT_MIN_METERS, navigationMeters * ROUTE_AGREEMENT_RATIO))
            return fail(ProductionStationFailure.ROUTE_COVERAGE_UNVERIFIED)
        if (!fresh(snapshot.fetchedAtEpochSeconds, snapshot.expiresAtEpochSeconds, 30))
            return fail(ProductionStationFailure.STALE_ROAD_METRICS)
        val rows = snapshot.stations.toList()
        if (rows.map { it.id }.distinct().size != rows.size || rows.any { row ->
                row.fuel != fuel || row.id.isBlank() || row.name.isBlank() || !validDistance(row.alongRouteMeters) ||
                    !validDistance(row.roadDistanceMeters) || row.roadDistanceMeters < row.alongRouteMeters ||
                    row.etaSeconds < 0 || row.detourSeconds < 0 ||
                    !validLocation(VehicleLocation(row.latitude, row.longitude)) })
            return fail(ProductionStationFailure.INVALID_ROAD_METRICS)
        // Suspended network/routing work cannot publish a result for an old route or origin.
        if (!stillSameTrip(origin, routeRead)) return fail(ProductionStationFailure.CONTEXT_CHANGED)
        if (!fresh(snapshot.fetchedAtEpochSeconds, snapshot.expiresAtEpochSeconds, 30))
            return fail(ProductionStationFailure.STALE_ROAD_METRICS)
        // The two routings end at the same destination but may differ slightly in length; a station the
        // navigation's route would already have finished before is not on that route's remaining part.
        return ProductionStationRead.Available(rows.filter { it.alongRouteMeters <= navigationMeters }.map { row ->
            FuelStationCandidate(row.id, row.name, row.roadDistanceMeters / 1000.0, row.alongRouteMeters / 1000.0,
                ceil(row.etaSeconds / 60.0).toInt(), ceil(row.detourSeconds / 60.0).toInt(), OpenStatus.UNKNOWN,
                fuelCompatible = true, businessInfoFresh = false,
                navigationTarget = VerifiedStationNavigationTarget(row.id, row.latitude, row.longitude,
                    NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84))
        })
    }

    /** Re-reads location and route after suspended work; see [sameTrip]. */
    private suspend fun stillSameTrip(origin: VehicleLocation, routeRead: RouteResult.Available): Boolean {
        val latest = (location.readLocation() as? LocationResult.Available)?.location ?: return false
        val route = navigation.readRoute() as? RouteResult.Available ?: return false
        return sameTrip(origin, routeRead.route, latest, route.route)
    }

    /**
     * Whether two reads a moment apart describe the same trip. A moving vehicle never reads the same position
     * twice (a query takes about 2 s, 55 m at 100 km/h) and PLEOS rounds the remaining distance to whole
     * kilometres, so exact equality would reject every answer while driving. The route identity and destination
     * must match exactly; the position may drift up to [MAX_TRIP_DRIFT_METERS] and the remaining distance up to
     * [MAX_REMAINING_DRIFT_KM]. Distances measured from the earlier position are then too long by at most that
     * drift, which only makes a recommendation earlier, never later.
     */
    private fun sameTrip(a: VehicleLocation, routeA: ActiveRoute?, b: VehicleLocation, routeB: ActiveRoute?): Boolean {
        if ((routeA == null) != (routeB == null)) return false
        if (routeA != null && routeB != null && (routeA.id != routeB.id || routeA.destinationName != routeB.destinationName ||
                routeA.destinationLatitude != routeB.destinationLatitude || routeA.destinationLongitude != routeB.destinationLongitude ||
                abs(routeA.remainingDistanceKm - routeB.remainingDistanceKm) > MAX_REMAINING_DRIFT_KM)) return false
        return validLocation(b) && distanceMeters(a, b) <= MAX_TRIP_DRIFT_METERS
    }

    private fun distanceMeters(a: VehicleLocation, b: VehicleLocation): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).let { it * it }
        return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun fresh(fetched: Long, expires: Long, maxAge: Long): Boolean {
        val now = epochSeconds()
        return fetched >= 0 && fetched <= now && expires > now && expires > fetched && expires - fetched <= maxAge
    }

    private fun validCatalog(catalog: RawStationCatalog, fuel: StationFuel): Boolean =
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

    private companion object {
        const val ROUTE_AGREEMENT_MIN_METERS = 2000.0
        const val ROUTE_AGREEMENT_RATIO = 0.15
        const val MAX_TRIP_DRIFT_METERS = 300.0
        const val MAX_REMAINING_DRIFT_KM = 1.5
    }
    private fun validLocation(value: VehicleLocation) = value.latitude.isFinite() && value.latitude in -90.0..90.0 &&
        value.longitude.isFinite() && value.longitude in -180.0..180.0 &&
        (value.accuracyMeters?.let { it.isFinite() && it >= 0 } ?: true)
}
