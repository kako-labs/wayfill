package dev.fuelpilot.data.poi.gateway

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener

/** Only a public gateway origin belongs in app config. There is deliberately no credential field. */
class GatewayConfig(origin: String) {
    val endpoint: String
    init {
        val uri = URI(origin)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/"))
        endpoint = origin.trimEnd('/') + "/v1/stations/discover"
    }
}
data class GatewayHttpResponse(val status: Int, val body: String)
fun interface GatewayTransport { fun post(endpoint: String, json: String): GatewayHttpResponse }
fun interface GatewayDecoder { fun decode(json: String): GatewayDocument }
data class GatewayDocument(
    val schemaVersion: Int,
    val status: String,
    val reason: String?,
    val fetchedAtEpochSeconds: Long?,
    val expiresAtEpochSeconds: Long?,
    val provenance: StationProvenance?,
    val stations: List<RawCatalogStation>,
)

object HttpsGatewayTransport : GatewayTransport {
    override fun post(endpoint: String, json: String): GatewayHttpResponse {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val payload = json.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            if (status != 200 && status != 503) return GatewayHttpResponse(status, "")
            require(connection.contentType?.substringBefore(';')?.trim() == "application/json")
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            val bytes = requireNotNull(stream).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 262145 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                    require(output.size() <= 262144)
                }
                output.toByteArray()
            }
            require(bytes.size <= 262144)
            return GatewayHttpResponse(status, bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}

object JsonGatewayDecoder : GatewayDecoder {
    override fun decode(json: String): GatewayDocument {
        val tokener = JSONTokener(json)
        val root = tokener.nextValue() as JSONObject
        require(tokener.nextClean() == '\u0000')
        require(root.get("schemaVersion") == 1)
        val status = root.getString("status")
        if (status == "unavailable") {
            return GatewayDocument(1, status, root.getString("reason"), null, null, null, emptyList())
        }
        require(status == "available")
        val source = root.getJSONObject("provenance")
        val provenance = StationProvenance(source.getString("provider"), source.getString("endpoint"),
            CoordinateSystem.valueOf(source.getString("coordinateSystem")),
            source.strictBoolean("roadMetricsVerified"), source.strictBoolean("cacheHit"))
        val array = root.getJSONArray("stations")
        require(array.length() <= 200)
        val stations = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val coordinates = row.getJSONObject("coordinates")
            // Fetch time is intentionally not a replacement for a missing price observation time.
            require(row.has("priceObservedAtEpochSeconds") && row.isNull("priceObservedAtEpochSeconds"))
            RawCatalogStation(row.getString("id"), row.getString("name"), StationFuel.fromCode(row.getString("fuel")),
                row.strictDouble("priceKrwPerLiter"), StationCoordinates(CoordinateSystem.valueOf(coordinates.getString("system")),
                    coordinates.strictDouble("x"), coordinates.strictDouble("y")), row.strictDouble("providerDistanceMeters"), null)
        }
        return GatewayDocument(1, status, null, root.strictLong("fetchedAtEpochSeconds"),
            root.strictLong("expiresAtEpochSeconds"), provenance, stations)
    }
    private fun JSONObject.strictBoolean(name: String): Boolean = get(name) as Boolean
    private fun JSONObject.strictDouble(name: String): Double = (get(name) as Number).toDouble().also { require(it.isFinite()) }
    private fun JSONObject.strictLong(name: String): Long {
        val value = get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
}

class GatewayStationDiscovery(
    private val config: GatewayConfig?,
    private val transport: GatewayTransport = HttpsGatewayTransport,
    private val decoder: GatewayDecoder = JsonGatewayDecoder,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : StationDiscovery {
    override suspend fun discover(query: StationDiscoveryQuery): StationDiscoveryResult {
        fun unavailable(reason: DiscoveryFailure) = StationDiscoveryResult.Unavailable(reason)
        if (!query.coordinates.isValid() || query.radiusMeters !in 1..5000) return unavailable(DiscoveryFailure.INVALID_QUERY)
        val endpoint = config?.endpoint ?: return unavailable(DiscoveryFailure.UNCONFIGURED)
        // Enum values and validated finite numbers need no caller-controlled JSON string escaping.
        val body = """{"coordinateSystem":"${query.coordinates.system}","x":${query.coordinates.x},"y":${query.coordinates.y},"radiusMeters":${query.radiusMeters},"fuel":"${query.fuel.providerCode}"}"""
        try {
            val response = withTimeout(5000) { runInterruptible(Dispatchers.IO) { transport.post(endpoint, body) } }
            if (response.status == 429) return unavailable(DiscoveryFailure.QUOTA_EXHAUSTED)
            if (response.body.toByteArray(Charsets.UTF_8).size > 262144) return unavailable(DiscoveryFailure.INVALID_RESPONSE)
            if (response.status != 200 && response.status != 503) return unavailable(DiscoveryFailure.NETWORK)
            val doc = decoder.decode(response.body)
            if (doc.schemaVersion != 1) return unavailable(DiscoveryFailure.INVALID_RESPONSE)
            if (doc.status == "unavailable") return unavailable(when (doc.reason) {
                "COORDINATE_TRANSFORM_UNVERIFIED" -> DiscoveryFailure.COORDINATE_TRANSFORM_UNVERIFIED
                "PROVIDER_UNCONFIGURED" -> DiscoveryFailure.UNCONFIGURED
                "QUOTA_EXHAUSTED" -> DiscoveryFailure.QUOTA_EXHAUSTED
                else -> DiscoveryFailure.NETWORK
            })
            require(response.status == 200 && doc.status == "available")
            val fetched = requireNotNull(doc.fetchedAtEpochSeconds)
            val expires = requireNotNull(doc.expiresAtEpochSeconds)
            val now = epochSeconds()
            if (now >= expires) return unavailable(DiscoveryFailure.STALE)
            require(fetched >= 0 && fetched <= now && expires > fetched && expires - fetched <= 300)
            val provenance = requireNotNull(doc.provenance)
            require(provenance.provider == "OPINET" && provenance.endpoint == "aroundAll" &&
                provenance.coordinateSystem == CoordinateSystem.KATEC && !provenance.roadMetricsVerified)
            require(doc.stations.size <= 200 && doc.stations.map { it.id }.distinct().size == doc.stations.size)
            doc.stations.forEach { row ->
                require(row.id.isNotBlank() && row.id.length <= 64 && row.name.isNotBlank() && row.name.length <= 200)
                require(row.fuel == query.fuel && row.coordinates.system == CoordinateSystem.KATEC && row.coordinates.isValid())
                require(row.priceKrwPerLiter.isFinite() && row.priceKrwPerLiter in 1.0..100000.0)
                require(row.providerDistanceMeters.isFinite() && row.providerDistanceMeters in 0.0..100000.0)
                require(row.priceObservedAtEpochSeconds == null)
            }
            return StationDiscoveryResult.Available(RawStationCatalog(doc.stations.toList(), fetched, expires, provenance))
        } catch (_: TimeoutCancellationException) {
            return unavailable(DiscoveryFailure.NETWORK)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return unavailable(DiscoveryFailure.INVALID_RESPONSE)
        }
    }
}
