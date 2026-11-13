package dev.fuelpilot.data.poi.gateway

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener

/** Origin and destination of the active route, WGS84 degrees. */
data class RouteCorridorQuery(
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val fuel: StationFuel,
) {
    fun isValid(): Boolean = listOf(originLatitude, destinationLatitude).all { it.isFinite() && it in KOREA_LATITUDE } &&
        listOf(originLongitude, destinationLongitude).all { it.isFinite() && it in KOREA_LONGITUDE }
}

/** A station on the route with metrics from real routing (Kakao Mobility), never straight-line distance. */
data class CorridorStation(
    val id: String,
    val name: String,
    val fuel: StationFuel,
    val priceKrwPerLiter: Double,
    val latitude: Double,
    val longitude: Double,
    /** Along the routed path from the current origin to the point nearest the station. */
    val alongRouteMeters: Double,
    /** Routed distance to the station, including the approach half of the detour. */
    val roadDistanceMeters: Double,
    val etaSeconds: Int,
    /** Routed origin -> station -> destination minus origin -> destination. */
    val detourSeconds: Int,
)

data class RouteCorridorSnapshot(
    val fetchedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val stationsObservedAtEpochSeconds: Long,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Int,
    /** Every station within the corridor width was inside some provider search (server-side proof). */
    val coverageVerified: Boolean,
    val stations: List<CorridorStation>,
)

enum class CorridorFailure { UNCONFIGURED, INVALID_QUERY, QUOTA_EXHAUSTED, ROUTE_UNAVAILABLE, STALE, INVALID_RESPONSE, NETWORK }

sealed interface RouteCorridorResult {
    data class Available(val snapshot: RouteCorridorSnapshot) : RouteCorridorResult
    data class Unavailable(val reason: CorridorFailure) : RouteCorridorResult
}

fun interface RouteCorridorSource {
    suspend fun measure(query: RouteCorridorQuery): RouteCorridorResult
}

object UnsupportedRouteCorridor : RouteCorridorSource {
    override suspend fun measure(query: RouteCorridorQuery): RouteCorridorResult =
        RouteCorridorResult.Unavailable(CorridorFailure.UNCONFIGURED)
}

data class CorridorDocument(
    val schemaVersion: Int,
    val status: String,
    val reason: String?,
    val snapshot: RouteCorridorSnapshot?,
    val stationProvider: String?,
    val routingProvider: String?,
    val coordinateSystem: String?,
)

fun interface CorridorDecoder { fun decode(json: String): CorridorDocument }

object JsonCorridorDecoder : CorridorDecoder {
    override fun decode(json: String): CorridorDocument {
        val tokener = JSONTokener(json)
        val root = tokener.nextValue() as JSONObject
        require(tokener.nextClean() == '\u0000')
        require(root.get("schemaVersion") == 1)
        val status = root.getString("status")
        if (status == "unavailable") return CorridorDocument(1, status, root.getString("reason"), null, null, null, null)
        require(status == "available")
        val route = root.getJSONObject("route")
        val coverage = root.getJSONObject("coverage")
        val provenance = root.getJSONObject("provenance")
        val array = root.getJSONArray("stations")
        require(array.length() <= MAX_STATIONS)
        val stations = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            CorridorStation(row.getString("id"), row.getString("name"), StationFuel.fromCode(row.getString("fuel")),
                row.strictDouble("priceKrwPerLiter"), row.strictDouble("latitude"), row.strictDouble("longitude"),
                row.strictDouble("alongRouteMeters"), row.strictDouble("roadDistanceMeters"),
                row.strictInt("etaSeconds"), row.strictInt("detourSeconds"))
        }
        val snapshot = RouteCorridorSnapshot(root.strictLong("fetchedAtEpochSeconds"), root.strictLong("expiresAtEpochSeconds"),
            root.strictLong("stationsObservedAtEpochSeconds"), route.strictDouble("remainingDistanceMeters"),
            route.strictInt("remainingDurationSeconds"), coverage.get("verified") as Boolean, stations)
        return CorridorDocument(1, status, null, snapshot, provenance.getString("stationProvider"),
            provenance.getString("routingProvider"), provenance.getString("coordinateSystem"))
    }
    private fun JSONObject.strictDouble(name: String): Double = (get(name) as Number).toDouble().also { require(it.isFinite()) }
    private fun JSONObject.strictLong(name: String): Long {
        val value = get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun JSONObject.strictInt(name: String): Int {
        val value = get(name)
        require(value is Int)
        return value
    }
}

class GatewayRouteCorridor(
    private val config: GatewayConfig?,
    private val transport: GatewayTransport = HttpsCorridorTransport,
    private val decoder: CorridorDecoder = JsonCorridorDecoder,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : RouteCorridorSource {
    override suspend fun measure(query: RouteCorridorQuery): RouteCorridorResult {
        fun unavailable(reason: CorridorFailure) = RouteCorridorResult.Unavailable(reason)
        if (!query.isValid()) return unavailable(CorridorFailure.INVALID_QUERY)
        val endpoint = config?.routeEndpoint ?: return unavailable(CorridorFailure.UNCONFIGURED)
        // Validated finite numbers and an enum code need no caller-controlled JSON string escaping.
        val body = """{"origin":{"latitude":${query.originLatitude},"longitude":${query.originLongitude}},""" +
            """"destination":{"latitude":${query.destinationLatitude},"longitude":${query.destinationLongitude}},""" +
            """"fuel":"${query.fuel.providerCode}"}"""
        try {
            val response = withTimeout(16_000) { runInterruptible(Dispatchers.IO) { transport.post(endpoint, body) } }
            if (response.status == 429) return unavailable(CorridorFailure.QUOTA_EXHAUSTED)
            if (response.body.toByteArray(Charsets.UTF_8).size > 262144) return unavailable(CorridorFailure.INVALID_RESPONSE)
            if (response.status != 200 && response.status != 503) return unavailable(CorridorFailure.NETWORK)
            val doc = decoder.decode(response.body)
            if (doc.schemaVersion != 1) return unavailable(CorridorFailure.INVALID_RESPONSE)
            if (doc.status == "unavailable") return unavailable(when (doc.reason) {
                "ROUTING_UNCONFIGURED", "PROVIDER_UNCONFIGURED" -> CorridorFailure.UNCONFIGURED
                "QUOTA_EXHAUSTED" -> CorridorFailure.QUOTA_EXHAUSTED
                "ROUTE_NOT_FOUND", "ROUTE_TOO_LONG" -> CorridorFailure.ROUTE_UNAVAILABLE
                else -> CorridorFailure.NETWORK
            })
            require(response.status == 200 && doc.status == "available")
            require(doc.stationProvider == "OPINET" && doc.routingProvider == "KAKAO_MOBILITY" && doc.coordinateSystem == "WGS84")
            val snapshot = requireNotNull(doc.snapshot)
            val now = epochSeconds()
            if (now >= snapshot.expiresAtEpochSeconds) return unavailable(CorridorFailure.STALE)
            require(snapshot.fetchedAtEpochSeconds in 0..now && snapshot.expiresAtEpochSeconds > snapshot.fetchedAtEpochSeconds &&
                snapshot.expiresAtEpochSeconds - snapshot.fetchedAtEpochSeconds <= MAX_SNAPSHOT_SECONDS)
            require(snapshot.stationsObservedAtEpochSeconds in 0..snapshot.fetchedAtEpochSeconds &&
                snapshot.fetchedAtEpochSeconds - snapshot.stationsObservedAtEpochSeconds <= MAX_OBSERVATION_AGE_SECONDS)
            require(snapshot.remainingDistanceMeters >= 0 && snapshot.remainingDurationSeconds >= 0)
            require(snapshot.stations.size <= MAX_STATIONS && snapshot.stations.map { it.id }.distinct().size == snapshot.stations.size)
            snapshot.stations.forEach { row ->
                require(row.id.isNotBlank() && row.id.length <= 64 && row.name.isNotBlank() && row.name.length <= 200)
                require(row.fuel == query.fuel && row.priceKrwPerLiter in 1.0..100000.0)
                require(row.latitude in KOREA_LATITUDE && row.longitude in KOREA_LONGITUDE)
                require(row.alongRouteMeters >= 0 && row.alongRouteMeters <= snapshot.remainingDistanceMeters)
                require(row.roadDistanceMeters >= row.alongRouteMeters && row.etaSeconds >= 0 && row.detourSeconds >= 0)
            }
            return RouteCorridorResult.Available(snapshot.copy(stations = snapshot.stations.toList()))
        } catch (_: TimeoutCancellationException) {
            return unavailable(CorridorFailure.NETWORK)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return unavailable(CorridorFailure.INVALID_RESPONSE)
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_SECONDS = 30L
        /** Corridor (10 min) plus provider sample cache (5 min). */
        const val MAX_OBSERVATION_AGE_SECONDS = 900L
    }
}

private const val MAX_STATIONS = 50
private val KOREA_LATITUDE = 32.5..39.0
private val KOREA_LONGITUDE = 124.0..132.0
