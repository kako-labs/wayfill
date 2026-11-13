"""Route corridor: stations along the active route, with metrics from real routing.

Kakao Mobility routes origin -> destination (geometry and per-road durations) and origin -> station ->
destination for each candidate (detour). Opinet supplies stations from searches centred every
SAMPLE_SPACING_M along the route. Coverage is proven, not assumed: every point of the route lies within
SEARCH_RADIUS_M - MAX_LATERAL_M of some search centre, so every station within MAX_LATERAL_M of the route
was inside at least one search.
A corridor is built once per destination and reused while the vehicle stays on that route; each request
projects the current origin onto it. Provider straight-line distance is never used as a road metric.
Both keys stay on the server.
"""
import bisect
from concurrent.futures import ThreadPoolExecutor
import json
import math
import threading
import urllib.parse
import urllib.request

from server import katec
from server.gateway import FUELS, NoRedirect, ProviderFailure

KAKAO_DIRECTIONS = 'https://apis-navi.kakaomobility.com/v1/directions'
KAKAO_MAX_BYTES = 2 * 1024 * 1024
SEARCH_RADIUS_M = 5000
MAX_LATERAL_M = 1000
CHECK_STEP_M = 200
# Any route point is within CHECK_STEP_M / 2 of a checked point, hence within
# SEARCH_RADIUS_M - MAX_LATERAL_M of a centre.
COVERAGE_RADIUS_M = SEARCH_RADIUS_M - MAX_LATERAL_M - CHECK_STEP_M // 2
SNAP_M = 1000
# Half the spacing plus the worst snap offset (SNAP_M / sqrt 2) stays inside COVERAGE_RADIUS_M, so a
# straight route needs no extra centres; bends only bring points closer to their centres.
SAMPLE_SPACING_M = 6000
MAX_SAMPLES = 40
GRID_M = 1000
MAX_CANDIDATES = 10
CORRIDOR_TTL_S = 600
OFF_ROUTE_M = 300
SNAPSHOT_TTL_S = 30
SAMPLE_CACHE_TTL_S = 300
MIN_AHEAD_M = 50
EARTH_M = 6371008.8


def kakao_fetch(url, api_key, timeout, limit):
    opener = urllib.request.build_opener(NoRedirect())
    request = urllib.request.Request(url, headers={'Authorization': 'KakaoAK ' + api_key})
    with opener.open(request, timeout=timeout) as response:
        if response.status != 200:
            raise ProviderFailure('ROUTING_UNAVAILABLE')
        body = response.read(limit + 1)
        if len(body) > limit:
            raise ProviderFailure('ROUTING_SCHEMA_INVALID')
        return body


def _num(value, low, high):
    if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError('invalid number')
    return float(value)


def haversine(a_lat, a_lon, b_lat, b_lon):
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dp, dl = p2 - p1, math.radians(b_lon - a_lon)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_M * math.asin(min(1.0, math.sqrt(h)))


class Route:
    """Polyline as (lat, lon, cumulative metres, cumulative seconds), with a local planar frame and a grid
    index so projecting hundreds of stations onto thousands of vertices stays fast."""
    def __init__(self, distance, duration, points):
        self.distance, self.duration, self.points = distance, duration, points
        self._alongs = [p[2] for p in points]
        if points:
            lat0 = sum(p[0] for p in points) / len(points)
            self._kx = math.cos(math.radians(lat0)) * EARTH_M * math.pi / 180
            self._ky = EARTH_M * math.pi / 180
            self._lon0, self._lat0 = points[0][1], lat0
            self._xy = [self._plane(p[0], p[1]) for p in points]
            self._grid = {}
            for i in range(len(points) - 1):
                (ax, ay), (bx, by) = self._xy[i], self._xy[i + 1]
                for cx in range(self._cell(min(ax, bx) - MAX_LATERAL_M), self._cell(max(ax, bx) + MAX_LATERAL_M) + 1):
                    for cy in range(self._cell(min(ay, by) - MAX_LATERAL_M), self._cell(max(ay, by) + MAX_LATERAL_M) + 1):
                        self._grid.setdefault((cx, cy), []).append(i)

    def _plane(self, lat, lon):
        return (lon - self._lon0) * self._kx, (lat - self._lat0) * self._ky

    @staticmethod
    def _cell(value):
        return math.floor(value / GRID_M)

    def project(self, lat, lon):
        """(along-route metres, lateral metres) of the closest route point; lateral is inf beyond
        MAX_LATERAL_M of the route."""
        px, py = self._plane(lat, lon)
        best = (math.inf, 0.0)
        for i in self._grid.get((self._cell(px), self._cell(py)), ()):
            (ax, ay), (bx, by) = self._xy[i], self._xy[i + 1]
            dx, dy = bx - ax, by - ay
            length2 = dx * dx + dy * dy
            t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
            lateral = math.hypot(ax + t * dx - px, ay + t * dy - py)
            if lateral < best[0]:
                a, b = self.points[i], self.points[i + 1]
                best = (lateral, a[2] + t * (b[2] - a[2]))
        # The cell may also list farther segments; only the indexed radius is exact.
        if best[0] > MAX_LATERAL_M:
            return 0.0, math.inf
        return best[1], best[0]

    def _at(self, along, index):
        if along <= 0:
            return self.points[0][index]
        if along >= self._alongs[-1]:
            return self.points[-1][index]
        i = max(1, bisect.bisect_left(self._alongs, along))
        a, b = self.points[i - 1], self.points[i]
        t = 0.0 if b[2] == a[2] else (along - a[2]) / (b[2] - a[2])
        return a[index] + t * (b[index] - a[index])

    def time_at(self, along):
        return self._at(along, 3)

    def point_at(self, along):
        return self._at(along, 0), self._at(along, 1)

    def densify(self, step):
        """Points every `step` metres along the route, plus every vertex."""
        out = [self.points[0][:2]]
        for a, b in zip(self.points, self.points[1:]):
            length = b[2] - a[2]
            for k in range(1, int(length // step) + 1):
                t = k * step / length
                out.append((a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])))
            out.append(b[:2])
        return out


class KakaoDirections:
    def __init__(self, api_key, fetch=kakao_fetch):
        self._key, self._fetch = api_key, fetch

    @property
    def configured(self):
        return bool(self._key and self._key.strip())

    def route(self, origin, destination, waypoint=None, geometry=False):
        params = dict(origin=f'{origin[1]},{origin[0]}', destination=f'{destination[1]},{destination[0]}',
                      priority='RECOMMEND', summary='false' if geometry else 'true')
        if waypoint is not None:
            params['waypoints'] = f'{waypoint[1]},{waypoint[0]}'
        url = KAKAO_DIRECTIONS + '?' + urllib.parse.urlencode(params)
        try:
            body = self._fetch(url, self._key, 4, KAKAO_MAX_BYTES)
        except ProviderFailure:
            raise
        except Exception:
            raise ProviderFailure('ROUTING_UNAVAILABLE') from None
        try:
            route = json.loads(body)['routes'][0]
            if route.get('result_code') != 0:
                raise ProviderFailure('ROUTE_NOT_FOUND')
            summary = route['summary']
            distance = _num(summary['distance'], 0, 3_000_000)
            duration = _num(summary['duration'], 0, 360_000)
            if not geometry:
                return Route(distance, duration, [])
            return Route(distance, duration, self._points(route['sections'], duration))
        except ProviderFailure:
            raise
        except (ValueError, KeyError, IndexError, TypeError):
            raise ProviderFailure('ROUTING_SCHEMA_INVALID') from None

    @staticmethod
    def _points(sections, duration):
        points, cum_m, cum_s = [], 0.0, 0.0
        for section in sections:
            for road in section['roads']:
                v = road['vertexes']
                if len(v) < 4 or len(v) % 2:
                    raise ValueError('vertexes')
                pts = [(_num(v[i + 1], -90, 90), _num(v[i], -180, 180)) for i in range(0, len(v), 2)]
                if not all(katec.in_korea(lat, lon) for lat, lon in pts):
                    raise ValueError('outside')
                length = sum(haversine(*pts[i], *pts[i + 1]) for i in range(len(pts) - 1))
                road_s = _num(road['duration'], 0, 360_000)
                for lat, lon in pts:
                    if not points:
                        points.append((lat, lon, 0.0, 0.0))
                        continue
                    step = haversine(points[-1][0], points[-1][1], lat, lon)
                    if step < 0.01:
                        continue
                    cum_m += step
                    cum_s += road_s * step / length if length > 0 else 0.0
                    points.append((lat, lon, cum_m, cum_s))
        if len(points) < 2:
            raise ValueError('geometry')
        scale = duration / cum_s if cum_s > 0 else 0.0
        return [(lat, lon, m, s * scale) for lat, lon, m, s in points]


def sample_centres(route):
    """KATEC search centres every SAMPLE_SPACING_M (snapped to SNAP_M cells so caches are reused), plus
    extra centres until every checked route point is within COVERAGE_RADIUS_M. Raises past MAX_SAMPLES."""
    alongs = [float(a) for a in range(0, int(route.distance) + 1, SAMPLE_SPACING_M)] + [route.distance]
    centres = []
    for along in alongs:
        x, y = katec.from_wgs84(*route.point_at(along))
        centre = ((math.floor(x / SNAP_M) + 0.5) * SNAP_M, (math.floor(y / SNAP_M) + 0.5) * SNAP_M)
        if centre not in centres:
            centres.append(centre)
    for lat, lon in route.densify(CHECK_STEP_M):
        x, y = katec.from_wgs84(lat, lon)
        if min(math.hypot(x - cx, y - cy) for cx, cy in centres) > COVERAGE_RADIUS_M:
            centres.append((x, y))
            if len(centres) > MAX_SAMPLES:
                raise ProviderFailure('ROUTE_TOO_LONG')
    if len(centres) > MAX_SAMPLES:
        raise ProviderFailure('ROUTE_TOO_LONG')
    return centres


class RouteCorridor:
    def __init__(self, routing, opinet, quota, clock=None, workers=4):
        import time
        self.routing, self.opinet, self.quota = routing, opinet, quota
        self.clock = clock or time.time
        self.workers = workers
        self._corridors, self._samples = {}, {}
        self._lock = threading.Lock()
        self._key_locks = {}

    def measure(self, query):
        def error(code, reason):
            return code, dict(schemaVersion=1, status='unavailable', reason=reason)
        try:
            origin, destination, fuel = self._validate(query)
        except (ValueError, TypeError, KeyError):
            return error(400, 'INVALID_QUERY')
        if not self.routing.configured:
            return error(503, 'ROUTING_UNCONFIGURED')
        if not self.opinet.configured:
            return error(503, 'PROVIDER_UNCONFIGURED')
        now = int(self.clock())
        key = (round(destination[0], 4), round(destination[1], 4), fuel)
        with self._lock:
            for stale in [k for k, c in self._corridors.items() if now - c['created'] >= CORRIDOR_TTL_S]:
                del self._corridors[stale]
            if len(self._key_locks) > 256:
                # A dropped lock can at worst let one duplicate build run; correctness is unaffected.
                self._key_locks = {k: v for k, v in self._key_locks.items() if k in self._corridors}
            key_lock = self._key_locks.setdefault(key, threading.Lock())
        try:
            with key_lock:
                with self._lock:
                    corridor = self._corridors.get(key)
                if (corridor is None or now - corridor['created'] >= CORRIDOR_TTL_S or
                        corridor['route'].project(*origin)[1] > OFF_ROUTE_M):
                    corridor = self._build(origin, destination, fuel, now)
                    with self._lock:
                        self._corridors[key] = corridor
                        while len(self._corridors) > 64:
                            self._corridors.pop(next(iter(self._corridors)))
        except ProviderFailure as failure:
            return error(429 if failure.reason == 'QUOTA_EXHAUSTED' else 503, failure.reason)
        except Exception:
            return error(503, 'ROUTING_UNAVAILABLE')
        return 200, self._snapshot(corridor, origin, now)

    @staticmethod
    def _validate(query):
        if not isinstance(query, dict) or set(query) != {'origin', 'destination', 'fuel'} or query['fuel'] not in FUELS:
            raise ValueError('query')
        points = []
        for name in ('origin', 'destination'):
            point = query[name]
            if not isinstance(point, dict) or set(point) != {'latitude', 'longitude'}:
                raise ValueError('point')
            lat, lon = _num(point['latitude'], -90, 90), _num(point['longitude'], -180, 180)
            if not katec.in_korea(lat, lon):
                raise ValueError('outside Korea')
            points.append((lat, lon))
        return points[0], points[1], query['fuel']

    def _build(self, origin, destination, fuel, now):
        base = self.routing.route(origin, destination, geometry=True)
        centres = sample_centres(base)
        with ThreadPoolExecutor(max_workers=self.workers) as pool:
            samples = list(pool.map(lambda c: self._sample(c, fuel, now), centres))
        batches = [rows for _, rows in samples]
        if not any(batches):
            # Every sample empty is indistinguishable from a rejected key: fail closed.
            raise ProviderFailure('PROVIDER_SCHEMA_INVALID')
        stations = {}
        for rows in batches:
            for row in rows:
                stations.setdefault(row['id'], row)
        near = []
        for row in stations.values():
            lat, lon = katec.to_wgs84(row['coordinates']['x'], row['coordinates']['y'])
            along, lateral = base.project(lat, lon)
            if lateral <= MAX_LATERAL_M and MIN_AHEAD_M <= along <= base.distance:
                near.append(dict(row, latitude=lat, longitude=lon, along0=along, lateral=lateral))
        chosen = []
        bucket = base.distance / MAX_CANDIDATES
        for index in range(MAX_CANDIDATES):
            inside = [s for s in near if index * bucket <= s['along0'] < (index + 1) * bucket]
            if inside:
                chosen.append(min(inside, key=lambda s: (s['lateral'], s['priceKrwPerLiter'])))

        def via(station):
            routed = self.routing.route(origin, destination, waypoint=(station['latitude'], station['longitude']))
            return dict(station, detour_s=max(0.0, routed.duration - base.duration),
                        detour_m=max(0.0, routed.distance - base.distance))
        with ThreadPoolExecutor(max_workers=self.workers) as pool:
            measured = list(pool.map(via, chosen))
        return dict(created=now, observed=min(at for at, _ in samples), route=base, candidates=measured)

    def _sample(self, centre, fuel, now):
        key = (round(centre[0], 1), round(centre[1], 1), fuel)
        with self._lock:
            cached = self._samples.get(key)
            if cached and 0 <= now - cached[0] < SAMPLE_CACHE_TTL_S:
                return cached
        if not self.quota.consume(now):
            raise ProviderFailure('QUOTA_EXHAUSTED')
        rows = self.opinet.discover(dict(coordinateSystem='KATEC', x=centre[0], y=centre[1],
                                         radiusMeters=SEARCH_RADIUS_M, fuel=fuel), allow_empty=True)
        with self._lock:
            self._samples[key] = (now, rows)
            while len(self._samples) > 512:
                self._samples.pop(next(iter(self._samples)))
        return now, rows

    @staticmethod
    def _snapshot(corridor, origin, now):
        route = corridor['route']
        progress, _ = route.project(*origin)
        progress_s = route.time_at(progress)
        stations = []
        for c in corridor['candidates']:
            ahead = c['along0'] - progress
            if ahead < MIN_AHEAD_M:
                continue
            stations.append(dict(
                id=c['id'], name=c['name'], fuel=c['fuel'], priceKrwPerLiter=c['priceKrwPerLiter'],
                latitude=round(c['latitude'], 7), longitude=round(c['longitude'], 7),
                alongRouteMeters=round(ahead, 1),
                # Approach and return split the routed detour evenly; both come from the via-station route.
                roadDistanceMeters=round(ahead + c['detour_m'] / 2, 1),
                etaSeconds=int(round(route.time_at(c['along0']) - progress_s + c['detour_s'] / 2)),
                detourSeconds=int(round(c['detour_s'])),
            ))
        return dict(
            schemaVersion=1, status='available',
            fetchedAtEpochSeconds=now, expiresAtEpochSeconds=now + SNAPSHOT_TTL_S,
            stationsObservedAtEpochSeconds=corridor['observed'],
            route=dict(remainingDistanceMeters=round(max(0.0, route.distance - progress), 1),
                       remainingDurationSeconds=int(round(max(0.0, route.duration - progress_s)))),
            coverage=dict(verified=True, sampleSpacingMeters=SAMPLE_SPACING_M, searchRadiusMeters=SEARCH_RADIUS_M),
            provenance=dict(stationProvider='OPINET', stationEndpoint='aroundAll',
                            routingProvider='KAKAO_MOBILITY', coordinateSystem='WGS84'),
            stations=stations,
        )
