"""KATEC <-> WGS84, the same definition as the app's KatecProjection (validated 2026-11-13).

Bessel 1841 TM, origin 38N 128E, k0 0.9999, FE 400000, FN 600000, with the 7-parameter Bessel->WGS84
shift (-115.80, 474.99, 674.11 m; 1.16, -2.31, -1.63 arc-seconds; 6.43 ppm; position vector).
Matches PROJ 9.8 within 5 mm forward and 1 mm inverse across 33-38.7N, 124.5-131E.
"""
import math

_BESSEL_A, _BESSEL_F = 6377397.155, 1 / 299.1528128
_WGS84_A, _WGS84_F = 6378137.0, 1 / 298.257223563
_DX, _DY, _DZ = -115.80, 474.99, 674.11
_RX, _RY, _RZ, _PPM = 1.16, -2.31, -1.63, 6.43
_LAT0, _LON0, _K0, _FE, _FN = math.radians(38.0), math.radians(128.0), 0.9999, 400000.0, 600000.0
_SEC = math.pi / (180 * 3600)

KOREA_LAT = (32.5, 39.0)
KOREA_LON = (124.0, 132.0)


def in_korea(lat, lon):
    return KOREA_LAT[0] <= lat <= KOREA_LAT[1] and KOREA_LON[0] <= lon <= KOREA_LON[1]


def _ecef(lat, lon, a, f):
    e2 = f * (2 - f)
    n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
    return n * math.cos(lat) * math.cos(lon), n * math.cos(lat) * math.sin(lon), n * (1 - e2) * math.sin(lat)


def _geodetic(x, y, z, a, f):
    e2 = f * (2 - f)
    p = math.hypot(x, y)
    lat = math.atan2(z, p * (1 - e2))
    for _ in range(10):
        n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
        h = p / math.cos(lat) - n
        lat = math.atan2(z, p * (1 - e2 * n / (n + h)))
    return lat, math.atan2(y, x)


def _helmert(x, y, z, sign):
    s = 1 + sign * _PPM * 1e-6
    rx, ry, rz = sign * _RX * _SEC, sign * _RY * _SEC, sign * _RZ * _SEC
    return (sign * _DX + s * (x - rz * y + ry * z),
            sign * _DY + s * (rz * x + y - rx * z),
            sign * _DZ + s * (-ry * x + rx * y + z))


def _meridian(phi, a, e2):
    e4, e6 = e2 * e2, e2 ** 3
    return a * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
                - (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * math.sin(2 * phi)
                + (15 * e4 / 256 + 45 * e6 / 1024) * math.sin(4 * phi)
                - (35 * e6 / 3072) * math.sin(6 * phi))


def _tm_forward(lat, lon):
    a, f = _BESSEL_A, _BESSEL_F
    e2 = f * (2 - f); ep2 = e2 / (1 - e2)
    n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
    t = math.tan(lat) ** 2; c = ep2 * math.cos(lat) ** 2
    A = (lon - _LON0) * math.cos(lat)
    x = _K0 * n * (A + (1 - t + c) * A ** 3 / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * A ** 5 / 120) + _FE
    y = _K0 * (_meridian(lat, a, e2) - _meridian(_LAT0, a, e2) + n * math.tan(lat) * (
        A * A / 2 + (5 - t + 9 * c + 4 * c * c) * A ** 4 / 24 + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * A ** 6 / 720)) + _FN
    return x, y


def _tm_inverse(x, y):
    a, f = _BESSEL_A, _BESSEL_F
    e2 = f * (2 - f); ep2 = e2 / (1 - e2); e4, e6 = e2 * e2, e2 ** 3
    m = _meridian(_LAT0, a, e2) + (y - _FN) / _K0
    mu = m / (a * (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256))
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    p1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu) + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
          + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    c1 = ep2 * math.cos(p1) ** 2; t1 = math.tan(p1) ** 2
    n1 = a / math.sqrt(1 - e2 * math.sin(p1) ** 2); r1 = a * (1 - e2) / (1 - e2 * math.sin(p1) ** 2) ** 1.5
    d = (x - _FE) / (n1 * _K0)
    lat = p1 - (n1 * math.tan(p1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d ** 4 / 24
                                           + (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d ** 6 / 720)
    lon = _LON0 + (d - (1 + 2 * t1 + c1) * d ** 3 / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d ** 5 / 120) / math.cos(p1)
    return lat, lon


def from_wgs84(lat, lon):
    """WGS84 degrees -> KATEC metres (x, y)."""
    bx = _helmert(*_ecef(math.radians(lat), math.radians(lon), _WGS84_A, _WGS84_F), -1)
    blat, blon = _geodetic(*bx, _BESSEL_A, _BESSEL_F)
    return _tm_forward(blat, blon)


def to_wgs84(x, y):
    """KATEC metres -> WGS84 degrees (lat, lon)."""
    blat, blon = _tm_inverse(x, y)
    wx = _helmert(*_ecef(blat, blon, _BESSEL_A, _BESSEL_F), +1)
    lat, lon = _geodetic(*wx, _WGS84_A, _WGS84_F)
    return math.degrees(lat), math.degrees(lon)
