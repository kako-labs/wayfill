package dev.fuelpilot.data.poi.gateway

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * KATEC (Opinet GIS_X_COOR/GIS_Y_COOR) <-> WGS84.
 *
 * KATEC has no EPSG code or primary-source definition. This is the definition that matched live data:
 * Bessel 1841 transverse Mercator, origin 38N 128E, k0 0.9999, false easting 400000 / northing 600000,
 * and a 7-parameter Bessel->WGS84 shift (-115.80, 474.99, 674.11 m; 1.16, -2.31, -1.63 arc-seconds;
 * 6.43 ppm, position-vector convention).
 *
 * Validation (2026-11-13): 55 live Opinet aroundAll rows near Gangnam converted with this definition sat a
 * median 6.5 m from same-brand OpenStreetMap fuel features (n=23; every same-station match within 33 m).
 * Without the datum shift the error was about 350 m. This implementation matches PROJ 9.8 within 5 mm
 * forward and 1 mm inverse across 33-38.7N, 124.5-131E. Re-validate before using it outside Korea.
 */
object KatecProjection {
    private const val BESSEL_A = 6377397.155
    private const val BESSEL_F = 1 / 299.1528128
    private const val WGS84_A = 6378137.0
    private const val WGS84_F = 1 / 298.257223563
    private const val DX = -115.80
    private const val DY = 474.99
    private const val DZ = 674.11
    private const val RX_SEC = 1.16
    private const val RY_SEC = -2.31
    private const val RZ_SEC = -1.63
    private const val SCALE_PPM = 6.43
    private const val K0 = 0.9999
    private const val FALSE_EASTING = 400000.0
    private const val FALSE_NORTHING = 600000.0
    private val LAT0 = Math.toRadians(38.0)
    private val LON0 = Math.toRadians(128.0)
    private const val ARC_SECOND = PI / (180.0 * 3600.0)

    /** Returns KATEC (x, y) in metres. */
    fun fromWgs84(latitude: Double, longitude: Double): Pair<Double, Double> {
        val (x, y, z) = toEcef(Math.toRadians(latitude), Math.toRadians(longitude), WGS84_A, WGS84_F)
        val bessel = helmert(x, y, z, sign = -1.0)
        val (lat, lon) = toGeodetic(bessel[0], bessel[1], bessel[2], BESSEL_A, BESSEL_F)
        return tmForward(lat, lon)
    }

    /** Returns WGS84 (latitude, longitude) in degrees. */
    fun toWgs84(x: Double, y: Double): Pair<Double, Double> {
        val (lat, lon) = tmInverse(x, y)
        val (bx, by, bz) = toEcef(lat, lon, BESSEL_A, BESSEL_F)
        val wgs = helmert(bx, by, bz, sign = 1.0)
        val (wLat, wLon) = toGeodetic(wgs[0], wgs[1], wgs[2], WGS84_A, WGS84_F)
        return Math.toDegrees(wLat) to Math.toDegrees(wLon)
    }

    private fun toEcef(lat: Double, lon: Double, a: Double, f: Double): Triple<Double, Double, Double> {
        val e2 = f * (2 - f)
        val n = a / sqrt(1 - e2 * sin(lat).pow(2))
        return Triple(n * cos(lat) * cos(lon), n * cos(lat) * sin(lon), n * (1 - e2) * sin(lat))
    }

    private fun toGeodetic(x: Double, y: Double, z: Double, a: Double, f: Double): Pair<Double, Double> {
        val e2 = f * (2 - f)
        val p = hypot(x, y)
        var lat = atan2(z, p * (1 - e2))
        repeat(10) {
            val n = a / sqrt(1 - e2 * sin(lat).pow(2))
            val h = p / cos(lat) - n
            lat = atan2(z, p * (1 - e2 * n / (n + h)))
        }
        return lat to atan2(y, x)
    }

    /** Position-vector Helmert; sign = +1 Bessel->WGS84, -1 the inverse (small-angle, sub-millimetre). */
    private fun helmert(x: Double, y: Double, z: Double, sign: Double): DoubleArray {
        val s = 1 + sign * SCALE_PPM * 1e-6
        val rx = sign * RX_SEC * ARC_SECOND
        val ry = sign * RY_SEC * ARC_SECOND
        val rz = sign * RZ_SEC * ARC_SECOND
        return doubleArrayOf(
            sign * DX + s * (x - rz * y + ry * z),
            sign * DY + s * (rz * x + y - rx * z),
            sign * DZ + s * (-ry * x + rx * y + z),
        )
    }

    private fun meridian(phi: Double, a: Double, e2: Double): Double {
        val e4 = e2 * e2
        val e6 = e4 * e2
        return a * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi -
            (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * phi) +
            (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * phi) -
            (35 * e6 / 3072) * sin(6 * phi))
    }

    private fun tmForward(lat: Double, lon: Double): Pair<Double, Double> {
        val e2 = BESSEL_F * (2 - BESSEL_F)
        val ep2 = e2 / (1 - e2)
        val n = BESSEL_A / sqrt(1 - e2 * sin(lat).pow(2))
        val t = tan(lat).pow(2)
        val c = ep2 * cos(lat).pow(2)
        val a = (lon - LON0) * cos(lat)
        val x = K0 * n * (a + (1 - t + c) * a.pow(3) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a.pow(5) / 120) + FALSE_EASTING
        val y = K0 * (meridian(lat, BESSEL_A, e2) - meridian(LAT0, BESSEL_A, e2) + n * tan(lat) *
            (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * a.pow(6) / 720)) +
            FALSE_NORTHING
        return x to y
    }

    private fun tmInverse(x: Double, y: Double): Pair<Double, Double> {
        val e2 = BESSEL_F * (2 - BESSEL_F)
        val ep2 = e2 / (1 - e2)
        val e4 = e2 * e2
        val e6 = e4 * e2
        val m = meridian(LAT0, BESSEL_A, e2) + (y - FALSE_NORTHING) / K0
        val mu = m / (BESSEL_A * (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256))
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val p1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) + (1097 * e1.pow(4) / 512) * sin(8 * mu)
        val c1 = ep2 * cos(p1).pow(2)
        val t1 = tan(p1).pow(2)
        val n1 = BESSEL_A / sqrt(1 - e2 * sin(p1).pow(2))
        val r1 = BESSEL_A * (1 - e2) / (1 - e2 * sin(p1).pow(2)).pow(1.5)
        val d = (x - FALSE_EASTING) / (n1 * K0)
        val lat = p1 - (n1 * tan(p1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d.pow(6) / 720)
        val lon = LON0 + (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d.pow(5) / 120) / cos(p1)
        return lat to lon
    }
}
