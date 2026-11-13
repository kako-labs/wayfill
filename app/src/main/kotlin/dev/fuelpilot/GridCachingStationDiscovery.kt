package dev.fuelpilot

import dev.fuelpilot.data.poi.gateway.CoordinateSystem
import dev.fuelpilot.data.poi.gateway.RawStationCatalog
import dev.fuelpilot.data.poi.gateway.StationCoordinates
import dev.fuelpilot.data.poi.gateway.StationDiscovery
import dev.fuelpilot.data.poi.gateway.StationDiscoveryQuery
import dev.fuelpilot.data.poi.gateway.StationDiscoveryResult
import dev.fuelpilot.data.poi.gateway.StationFuel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.floor

/**
 * Opinet allows about 300 calls a day, and the app refreshes every few seconds. A KATEC query snaps to
 * the centre of a [cellMeters] grid cell, and an unexpired catalog for the same cell, radius and fuel is
 * reused, so a parked or slow vehicle does not spend quota. Expired catalogs are never served.
 */
class GridCachingStationDiscovery(
    private val delegate: StationDiscovery,
    private val cellMeters: Double = 500.0,
    private val maxEntries: Int = 16,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : StationDiscovery {
    init { require(cellMeters > 0 && maxEntries > 0) }

    private data class Key(val column: Long, val row: Long, val radiusMeters: Int, val fuel: StationFuel)

    private val lock = Mutex()
    private val cache = LinkedHashMap<Key, RawStationCatalog>()

    override suspend fun discover(query: StationDiscoveryQuery): StationDiscoveryResult {
        val coordinates = query.coordinates
        if (coordinates.system != CoordinateSystem.KATEC || !coordinates.isValid()) return delegate.discover(query)
        val column = floor(coordinates.x / cellMeters).toLong()
        val row = floor(coordinates.y / cellMeters).toLong()
        val key = Key(column, row, query.radiusMeters, query.fuel)
        lock.withLock {
            cache[key]?.let { cached ->
                if (cached.expiresAtEpochSeconds > epochSeconds()) return StationDiscoveryResult.Available(cached)
                cache.remove(key)
            }
        }
        val centre = StationCoordinates(CoordinateSystem.KATEC, (column + 0.5) * cellMeters, (row + 0.5) * cellMeters)
        val result = delegate.discover(query.copy(coordinates = centre))
        if (result is StationDiscoveryResult.Available) lock.withLock {
            cache[key] = result.catalog
            while (cache.size > maxEntries) cache.remove(cache.keys.first())
        }
        return result
    }
}
