package dev.fuelpilot

import dev.fuelpilot.data.location.LocationRepository
import dev.fuelpilot.data.location.LocationResult
import dev.fuelpilot.data.location.VehicleLocation
import dev.fuelpilot.data.navigation.NaviCommandStatus
import dev.fuelpilot.data.navigation.NaviHelperPort
import dev.fuelpilot.data.navigation.NaviPosition
import dev.fuelpilot.data.navigation.NaviRead
import dev.fuelpilot.data.navigation.NaviRouteSnapshot
import dev.fuelpilot.data.navigation.NaviTrip
import dev.fuelpilot.data.navigation.NavigationTarget
import dev.fuelpilot.data.poi.gateway.CoordinateSystem
import dev.fuelpilot.data.poi.gateway.RawCatalogStation
import dev.fuelpilot.data.poi.gateway.RawStationCatalog
import dev.fuelpilot.data.poi.gateway.StationCoordinates
import dev.fuelpilot.data.poi.gateway.StationDiscovery
import dev.fuelpilot.data.poi.gateway.StationDiscoveryQuery
import dev.fuelpilot.data.poi.gateway.StationDiscoveryResult
import dev.fuelpilot.data.poi.gateway.StationFuel
import dev.fuelpilot.data.poi.gateway.StationProvenance
import dev.fuelpilot.model.ActiveRoute
import dev.fuelpilot.model.NavigationTargetProvenance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRoadMetricsTest {
    private val gsTower = RawCatalogStation("A0009974", "GS Tower", StationFuel.GASOLINE, 1878.0,
        StationCoordinates(CoordinateSystem.KATEC, 314996.159, 544938.508), 330.0, null)
    private val catalog = RawStationCatalog(listOf(gsTower), 100, 400,
        StationProvenance("OPINET", "aroundAll", CoordinateSystem.KATEC, roadMetricsVerified = false, cacheHit = false))
    private val origin = VehicleLocation(37.5, 127.03)

    private class FakePort(
        val trip: NaviRead<NaviTrip> = NaviRead.Available(NaviTrip(1520, 250)),
        val position: NaviRead<NaviPosition> = NaviRead.Available(NaviPosition(37.41, 127.09)),
    ) : NaviHelperPort {
        val trips = mutableListOf<Pair<Double, Double>>()
        override fun isSupported() = true
        override suspend fun readRoute(): NaviRead<NaviRouteSnapshot?> = NaviRead.Available(null)
        override suspend fun readWaypointCount(): NaviRead<Int> = NaviRead.Available(0)
        override suspend fun addWaypoint(target: NavigationTarget, index: Int) = NaviCommandStatus.UNSUPPORTED
        override suspend fun setDestination(target: NavigationTarget) = NaviCommandStatus.UNSUPPORTED
        override suspend fun measureTrip(latitude: Double, longitude: Double): NaviRead<NaviTrip> { trips += latitude to longitude; return trip }
        override suspend fun readCurrentLocation() = position
    }

    @Test fun nearbyMetricsComeFromTheNavigationTripToTheConvertedStation() = runTest {
        val port = FakePort()
        val snapshot = NavigationTripRoadMetrics(port) { 100 }.measure(RoadMetricsRequest(origin, null, catalog))!!
        val metric = snapshot.stations.single()
        assertEquals(1.52, metric.roadDistanceKm, 1e-9)
        assertEquals(5, metric.etaMinutes)
        assertNull(metric.alongRouteKm)
        assertNull(metric.detourMinutes)
        assertEquals(NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84, metric.navigationTarget!!.provenance)
        assertEquals(37.502702, port.trips.single().first, 1e-5)
        assertEquals(127.036392, port.trips.single().second, 1e-5)
        assertEquals(130L, snapshot.expiresAtEpochSeconds)
    }

    @Test fun anActiveRouteStaysUnverifiedWithoutExternalRouting() = runTest {
        val port = FakePort()
        assertNull(NavigationTripRoadMetrics(port) { 100 }.measure(RoadMetricsRequest(origin, ActiveRoute("r", "d", 30.0), catalog)))
        assertTrue(port.trips.isEmpty())
    }

    @Test fun anyUnmeasuredStationFailsTheWholeSnapshot() = runTest {
        assertNull(NavigationTripRoadMetrics(FakePort(trip = NaviRead.Unavailable)) { 100 }.measure(RoadMetricsRequest(origin, null, catalog)))
    }

    @Test fun locationUsesTheNavigationPositionAndFallsBackOnlyWhenUnsupported() = runTest {
        val fused = object : LocationRepository { override suspend fun readLocation() = LocationResult.Available(VehicleLocation(39.2, -123.1)) }
        assertEquals(LocationResult.Available(VehicleLocation(37.41, 127.09)), NavigationLocationRepository(FakePort(), fused).readLocation())
        assertSame(LocationResult.Unavailable, NavigationLocationRepository(FakePort(position = NaviRead.Unavailable), fused).readLocation())
        assertEquals(LocationResult.Available(VehicleLocation(39.2, -123.1)),
            NavigationLocationRepository(FakePort(position = NaviRead.Unsupported), fused).readLocation())
    }

    @Test fun gridCacheReusesAnUnexpiredCatalogAndSnapsTheQuery() = runTest {
        var now = 100L
        val queries = mutableListOf<StationDiscoveryQuery>()
        val delegate = object : StationDiscovery {
            override suspend fun discover(query: StationDiscoveryQuery): StationDiscoveryResult {
                queries += query
                return StationDiscoveryResult.Available(catalog)
            }
        }
        val cache = GridCachingStationDiscovery(delegate, cellMeters = 500.0) { now }
        val first = StationDiscoveryQuery(StationCoordinates(CoordinateSystem.KATEC, 314_701.0, 544_801.0), 5000, StationFuel.GASOLINE)
        val nearby = first.copy(coordinates = StationCoordinates(CoordinateSystem.KATEC, 314_960.0, 544_990.0))
        assertTrue(cache.discover(first) is StationDiscoveryResult.Available)
        assertTrue(cache.discover(nearby) is StationDiscoveryResult.Available)
        assertEquals(1, queries.size)
        assertEquals(314_750.0, queries.single().coordinates.x, 0.0)
        assertEquals(544_750.0, queries.single().coordinates.y, 0.0)
        now = 400L
        cache.discover(nearby)
        assertEquals(2, queries.size)
        cache.discover(first.copy(fuel = StationFuel.DIESEL))
        assertEquals(3, queries.size)
    }
}
