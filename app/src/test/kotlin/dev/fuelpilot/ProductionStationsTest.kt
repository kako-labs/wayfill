package dev.fuelpilot

import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.data.poi.gateway.*
import dev.fuelpilot.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProductionStationsTest {
    @Test fun nearbyCatalogWithoutVerifiedRouteCoverageCannotClaimLastSafeStop() = runTest {
        val incomplete = StationRoadMetrics { snapshot(it).copy(routeCoverageVerified = false) }
        assertEquals(ProductionStationFailure.ROUTE_COVERAGE_UNVERIFIED,
            failure(repository(metrics = incomplete).readStations()))
    }
    @Test fun unspecifiedVehicleFuelFailsBeforeDiscovery() = runTest {
        val repo = createProductionStations(location, navigation, source { error("Fuel must be verified first") })
        assertEquals(ProductionStationFailure.FUEL_UNVERIFIED, failure(repo.readStations()))
        assertSame(StationResult.Unavailable, repo.findStations())
    }

    @Test fun nearbyModeMeasuresOnlyTheNearestRowsAndJoinsTheirMetrics() = runTest {
        val rows = (1..9).map { rawStation.copy(id = "s$it", name = "Station $it", providerDistanceMeters = (10 - it) * 100.0) }
        val many = catalog.copy(stations = rows)
        var measuredIds = emptyList<String>()
        val metrics = StationRoadMetrics { request ->
            measuredIds = request.catalog.stations.map { it.id }
            VerifiedRoadMetricsSnapshot(request, 99, 110, request.catalog.stations.map { raw ->
                VerifiedStationRoadMetric(raw.id, true, 1.0, null, 3, null, verifiedTarget.copy(stationId = raw.id))
            })
        }
        val repo = createProductionStations(location, nav { RouteResult.Available(null) },
            source { StationDiscoveryResult.Available(many) }, metrics, transform, StationFuel.GASOLINE,
            epochSeconds = { 100 }, maxNearbyMeasured = 3)
        val read = repo.readStations() as ProductionStationRead.Available
        assertEquals(listOf("s9", "s8", "s7"), measuredIds)
        assertEquals(listOf("s9", "s8", "s7"), read.stations.map { it.id })
    }

    @Test fun validatedKatecTransformConvertsOnlyInsideKorea() = runTest {
        val seoul = ValidatedKatecTransform.toKatec(VehicleLocation(37.5027, 127.0364))!!
        assertEquals(CoordinateSystem.KATEC, seoul.system)
        assertEquals(314996.879, seoul.x, 0.01)
        assertEquals(544938.297, seoul.y, 0.01)
        assertNull(ValidatedKatecTransform.toKatec(VehicleLocation(39.237255, -123.150032)))
        assertNull(ValidatedKatecTransform.toKatec(VehicleLocation(Double.NaN, 127.0)))
    }

    @Test fun vehicleFuelSourceSuppliesTheProductWhenNoFixedFuelIsGiven() = runTest {
        val unknown = createProductionStations(location, navigation, source { error("Fuel must be verified first") },
            fuelSource = { null })
        assertEquals(ProductionStationFailure.FUEL_UNVERIFIED, failure(unknown.readStations()))
        val known = createProductionStations(location, navigation, source { error("Transform must be verified first") },
            fuelSource = { StationFuel.GASOLINE }, epochSeconds = { 100 })
        assertEquals(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED, failure(known.readStations()))
    }

    @Test fun rawCatalogAloneCannotProduceCandidatesAndTransformIsNeverGuessed() = runTest {
        var discoveries = 0
        val discovery = source { discoveries++; StationDiscoveryResult.Available(catalog) }
        val ordinary = createProductionStations(location, navigation, discovery, fuel = StationFuel.GASOLINE, epochSeconds = { 100 })
        assertEquals(ProductionStationFailure.COORDINATE_TRANSFORM_UNVERIFIED, failure(ordinary.readStations()))
        assertEquals(0, discoveries)
        val native = repository(discovery = discovery)
        assertEquals(ProductionStationFailure.ROAD_METRICS_UNAVAILABLE, failure(native.readStations()))
        assertSame(StationResult.Unavailable, native.findStations())
    }

    @Test fun verifiedRoadMetricsJoinByIdAndNeverUseProviderDistanceOrInventBusinessHours() = runTest {
        val read = repository(metrics = validMetrics).readStations() as ProductionStationRead.Available
        val station = read.stations.single()
        assertEquals("a", station.id)
        assertEquals(12.5, station.distanceFromCurrentKm, 0.0)
        assertEquals(11.0, station.distanceAlongRouteKm!!, 0.0)
        assertEquals(7, station.detourMinutes)
        assertEquals(OpenStatus.UNKNOWN, station.openStatus)
        assertFalse(station.businessInfoFresh)
        assertTrue(station.fuelCompatible)
        assertEquals(verifiedTarget, station.navigationTarget)
    }

    @Test fun invalidOrIncompleteMetricsFailClosed() = runTest {
        val badLists = listOf(
            emptyList(), listOf(metric.copy(stationId = "other")), listOf(metric, metric),
            listOf(metric.copy(roadDistanceKm = Double.NaN)), listOf(metric.copy(roadDistanceKm = -1.0)),
            listOf(metric.copy(alongRouteKm = Double.POSITIVE_INFINITY)), listOf(metric.copy(alongRouteKm = 101.0)),
            listOf(metric.copy(etaMinutes = -1)), listOf(metric.copy(detourMinutes = -1)),
            listOf(metric.copy(alongRouteKm = null)), listOf(metric.copy(detourMinutes = null)),
            listOf(metric.copy(navigationTarget = verifiedTarget.copy(stationId = "other"))),
            listOf(metric.copy(navigationTarget = verifiedTarget.copy(latitude = Double.NaN))),
        )
        for (rows in badLists) {
            val port = StationRoadMetrics { snapshot(it).copy(stations = rows) }
            assertEquals(ProductionStationFailure.INVALID_ROAD_METRICS, failure(repository(metrics = port).readStations()))
        }
    }

    @Test fun staleFutureOrOverlongMetricSnapshotsAndMismatchedContextAreRejected() = runTest {
        for ((measured, expires) in listOf(80L to 100L, 101L to 110L, 90L to 121L)) {
            val port = StationRoadMetrics { snapshot(it).copy(measuredAtEpochSeconds = measured, expiresAtEpochSeconds = expires) }
            assertEquals(ProductionStationFailure.STALE_ROAD_METRICS, failure(repository(metrics = port).readStations()))
        }
        val wrongRoute = StationRoadMetrics {
            snapshot(it).copy(request = it.copy(route = route.copy(id = "previous")))
        }
        assertEquals(ProductionStationFailure.INVALID_ROAD_METRICS, failure(repository(metrics = wrongRoute).readStations()))
    }

    @Test fun staleCatalogAndInvalidCatalogNeverReachRouting() = runTest {
        val mustNotRoute = StationRoadMetrics { error("Invalid catalog reached navigation") }
        val expired = catalog.copy(expiresAtEpochSeconds = 100)
        assertEquals(ProductionStationFailure.STALE_CATALOG,
            failure(repository(source { StationDiscoveryResult.Available(expired) }, mustNotRoute).readStations()))
        for (raw in listOf(catalog.copy(stations = listOf(rawStation, rawStation)),
            catalog.copy(stations = listOf(rawStation.copy(providerDistanceMeters = Double.NaN))),
            catalog.copy(stations = listOf(rawStation.copy(priceKrwPerLiter = -1.0))))) {
            assertEquals(ProductionStationFailure.INVALID_CATALOG,
                failure(repository(source { StationDiscoveryResult.Available(raw) }, mustNotRoute).readStations()))
        }
    }

    @Test fun unavailableGatewayReasonIsPreserved() = runTest {
        val repo = repository(source { StationDiscoveryResult.Unavailable(DiscoveryFailure.UNCONFIGURED) })
        val read = repo.readStations() as ProductionStationRead.Unavailable
        assertEquals(DiscoveryFailure.UNCONFIGURED, read.discoveryReason)
        assertSame(StationResult.Unavailable, repo.findStations())
    }

    @Test fun routeChangeWhileMeasuringAndCoordinatorContextMismatchRejectCandidates() = runTest {
        var current = route
        val nav = nav { RouteResult.Available(current) }
        val metrics = StationRoadMetrics { request -> current = route.copy(id = "new"); snapshot(request) }
        val repo = repository(metrics = metrics, nav = nav)
        assertEquals(ProductionStationFailure.CONTEXT_CHANGED, failure(repo.readStations()))
        assertEquals(ProductionStationFailure.CONTEXT_CHANGED, failure(repo.readStationsFor(origin, route)))
        assertSame(StationResult.Unavailable, repo.findStationsFor(origin, route))
    }

    @Test fun originChangeAndExpirationDuringFinalContextReadRejectCandidates() = runTest {
        var reads = 0
        val moving = object : LocationRepository {
            override suspend fun readLocation() = LocationResult.Available(
                if (++reads == 1) origin else origin.copy(latitude = 38.0))
        }
        assertEquals(ProductionStationFailure.CONTEXT_CHANGED,
            failure(repository(metrics = validMetrics, loc = moving).readStations()))
        var now = 100L
        val expiresDuringRead = object : LocationRepository {
            override suspend fun readLocation(): LocationResult { if (++reads > 1) now = 111; return LocationResult.Available(origin) }
        }
        reads = 0
        val repo = createProductionStations(expiresDuringRead, navigation, source { StationDiscoveryResult.Available(catalog) },
            validMetrics, transform, StationFuel.GASOLINE, epochSeconds = { now })
        assertEquals(ProductionStationFailure.STALE_ROAD_METRICS, failure(repo.readStations()))
    }

    @Test fun confirmedNoRouteAcceptsOnlyNearbyRoadMetricsAndUnreachableRowsAreExcluded() = runTest {
        val nearby = StationRoadMetrics { snapshot(it).copy(stations = listOf(metric.copy(alongRouteKm = null, detourMinutes = null))) }
        val repo = repository(metrics = nearby, nav = nav { RouteResult.Available(null) })
        assertEquals(1, (repo.readStationsFor(origin, null) as ProductionStationRead.Available).stations.size)
        assertEquals(ProductionStationFailure.INVALID_ROAD_METRICS,
            failure(repository(metrics = validMetrics, nav = nav { RouteResult.Available(null) }).readStations()))
        val unreachable = StationRoadMetrics { snapshot(it).copy(stations = listOf(metric.copy(reachable = false))) }
        assertTrue((repository(metrics = unreachable).readStations() as ProductionStationRead.Available).stations.isEmpty())
    }

    @Test fun cancellationPropagates() = runTest {
        val repo = repository(metrics = StationRoadMetrics { throw CancellationException("cancelled") })
        try { repo.readStations(); fail("Cancellation was swallowed") } catch (_: CancellationException) { /* expected */ }
    }

    private val origin = VehicleLocation(37.0, 127.0)
    private val route = ActiveRoute("route", "destination", 100.0)
    private val coordinates = StationCoordinates(CoordinateSystem.KATEC, 314681.8, 544837.0)
    private val rawStation = RawCatalogStation("a", "Station A", StationFuel.GASOLINE, 1700.0, coordinates, 25.0, null)
    private val catalog = RawStationCatalog(listOf(rawStation), 90, 200,
        StationProvenance("OPINET", "aroundAll", CoordinateSystem.KATEC, false, false))
    private val verifiedTarget = VerifiedStationNavigationTarget(
        stationId = "a",
        latitude = 37.5,
        longitude = 127.0,
        provenance = NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84,
        poiId = "verified-poi-a",
        poiProvider = "PLEOS_NAVI",
    )
    private val metric = VerifiedStationRoadMetric("a", true, 12.5, 11.0, 20, 7, verifiedTarget)
    private val transform = VerifiedStationCoordinateTransform { coordinates }
    private val location = object : LocationRepository { override suspend fun readLocation() = LocationResult.Available(origin) }
    private val navigation = nav { RouteResult.Available(route) }
    private val validMetrics = StationRoadMetrics { snapshot(it) }

    private fun snapshot(request: RoadMetricsRequest) = VerifiedRoadMetricsSnapshot(request, 99, 110, listOf(metric), routeCoverageVerified = true)
    private fun source(read: suspend () -> StationDiscoveryResult) = object : StationDiscovery {
        override suspend fun discover(query: StationDiscoveryQuery) = read()
    }
    private fun nav(read: suspend () -> RouteResult) = object : NavigationRepository {
        override suspend fun readRoute() = read()
        override suspend fun addWaypoint(request: WaypointRequest) = WaypointResult.Unsupported(request.requestId, request.stationId)
    }
    private fun repository(
        discovery: StationDiscovery = source { StationDiscoveryResult.Available(catalog) },
        metrics: StationRoadMetrics = UnsupportedStationRoadMetrics,
        nav: NavigationRepository = navigation,
        loc: LocationRepository = location,
    ) = createProductionStations(loc, nav, discovery, metrics, transform, StationFuel.GASOLINE, epochSeconds = { 100 })
    private fun failure(read: ProductionStationRead) = (read as ProductionStationRead.Unavailable).reason
}
