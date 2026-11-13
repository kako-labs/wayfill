package dev.fuelpilot

import dev.fuelpilot.data.location.LocationRepository
import dev.fuelpilot.data.location.LocationResult
import dev.fuelpilot.data.location.VehicleLocation
import dev.fuelpilot.data.navigation.NaviHelperPort
import dev.fuelpilot.data.navigation.NaviRead
import dev.fuelpilot.data.poi.gateway.KatecProjection
import dev.fuelpilot.model.NavigationTargetProvenance
import dev.fuelpilot.model.VerifiedStationNavigationTarget
import kotlin.math.ceil

/**
 * Road metrics from the vehicle navigation's own trip preview (NaviHelper.requestTrip).
 *
 * Without an active route this is complete: road distance and ETA from the navigation's position.
 * With an active route it returns null, because the navigation cannot compute station-to-destination
 * distance or expose route geometry; route mode is served by the gateway's routed corridor instead
 * (see GatewayRouteCorridor and ProductionStationRepository).
 */
class NavigationTripRoadMetrics(
    private val port: NaviHelperPort,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : StationRoadMetrics {
    override suspend fun measure(request: RoadMetricsRequest): VerifiedRoadMetricsSnapshot? {
        if (request.route != null) return null
        val measuredAt = epochSeconds()
        val metrics = request.catalog.stations.map { raw ->
            val (latitude, longitude) = KatecProjection.toWgs84(raw.coordinates.x, raw.coordinates.y)
            val trip = port.measureTrip(latitude, longitude) as? NaviRead.Available ?: return null
            VerifiedStationRoadMetric(
                stationId = raw.id,
                reachable = true,
                roadDistanceKm = trip.value.distanceMeters / 1000.0,
                alongRouteKm = null,
                etaMinutes = ceil(trip.value.etaSeconds / 60.0).toInt(),
                detourMinutes = null,
                navigationTarget = VerifiedStationNavigationTarget(
                    stationId = raw.id,
                    latitude = latitude,
                    longitude = longitude,
                    provenance = NavigationTargetProvenance.VERIFIED_ROAD_METRICS_WGS84,
                ),
            )
        }
        return VerifiedRoadMetricsSnapshot(request, measuredAt, measuredAt + SNAPSHOT_LIFETIME_SECONDS, metrics)
    }

    private companion object { const val SNAPSHOT_LIFETIME_SECONDS = 30L }
}

/**
 * Uses the navigation's own position so station discovery and trip previews share one origin. The
 * fallback is used only when the navigation has no location support at all, never to mask a transient
 * navigation failure with a position from another source.
 */
class NavigationLocationRepository(
    private val port: NaviHelperPort,
    private val fallback: LocationRepository,
) : LocationRepository {
    override suspend fun readLocation(): LocationResult = when (val read = port.readCurrentLocation()) {
        is NaviRead.Available -> LocationResult.Available(VehicleLocation(read.value.latitude, read.value.longitude))
        NaviRead.Unavailable -> LocationResult.Unavailable
        NaviRead.Unsupported -> fallback.readLocation()
    }
}
