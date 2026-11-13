package dev.fuelpilot.data.poi.gateway

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot

class KatecProjectionTest {
    // Reference values from PROJ 9.8.1 (pyproj 3.8.0) with the definition documented on KatecProjection.
    private val vectors = listOf(
        Triple(37.5027, 127.0364, 314996.879 to 544938.297),
        Triple(35.1796, 129.0756, 498164.716 to 287275.006),
        Triple(33.4996, 126.5312, 263722.204 to 101365.130),
        Triple(38.2070, 128.5918, 452020.876 to 622835.378),
        Triple(37.4563, 126.7052, 285641.885 to 540139.606),
    )

    @Test fun forwardMatchesProjWithinOneCentimetre() {
        for ((lat, lon, expected) in vectors) {
            val (x, y) = KatecProjection.fromWgs84(lat, lon)
            assertEquals("x for $lat,$lon", expected.first, x, 0.01)
            assertEquals("y for $lat,$lon", expected.second, y, 0.01)
        }
    }

    @Test fun inverseMatchesProjWithinOneCentimetre() {
        for ((lat, lon, katec) in vectors) {
            val (bLat, bLon) = KatecProjection.toWgs84(katec.first, katec.second)
            assertEquals(0.0, metres(lat, lon, bLat, bLon), 0.01)
        }
    }

    @Test fun liveOpinetStationLandsOnItsKnownLocation() {
        // Opinet A0009974 (GS Tower energy hub, Gangnam): KATEC(314996.159, 544938.508).
        val (lat, lon) = KatecProjection.toWgs84(314996.159, 544938.508)
        assertEquals(0.0, metres(37.502702, 127.036392, lat, lon), 1.0)
    }

    private fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        hypot((lat1 - lat2) * 111_320.0, (lon1 - lon2) * 111_320.0 * cos(Math.toRadians(lat1)))
}
