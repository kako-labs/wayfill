import http.client
import json
import math
import random
import tempfile
import threading
import unittest
from http.server import HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from server import katec
from server.corridor import (COVERAGE_RADIUS_M, MAX_LATERAL_M, SEARCH_RADIUS_M, KakaoDirections, Route,
                             RouteCorridor, haversine, sample_centres)
from server.gateway import Gateway, OpinetProvider, ProviderFailure, SqliteQuota, handler_for

M_PER_DEG_LAT = 111195.0
SPEED = 15.0
ORIGIN = (37.40, 127.10)
DEST = (37.13, 127.10)


def offset(along_m, lateral_m, start=ORIGIN):
    """A point `along_m` south of `start` and `lateral_m` east of the meridian through it."""
    lat = start[0] - along_m / M_PER_DEG_LAT
    return lat, start[1] + lateral_m / (math.cos(math.radians(lat)) * M_PER_DEG_LAT)


STATIONS = {
    'S1': offset(10000, 100),    # chosen in its bucket (closest to the route)
    'S2': offset(10400, 600),    # same bucket, farther from the route
    'S3': offset(20000, 3000),   # beyond MAX_LATERAL_M
    'S4': offset(-2000, 0),      # behind the origin
    'S5': offset(25000, 0),
}
PRICES = {'S1': 1700, 'S2': 1600, 'S3': 1500, 'S4': 1400, 'S5': 1750}


def straight_route_json(origin, destination, waypoint, geometry, extra_m=1000, extra_s=120, code=0, base_extra_m=0):
    distance = haversine(*origin, *destination) + base_extra_m
    duration = distance / SPEED
    if waypoint is not None:
        distance, duration = distance + extra_m, duration + extra_s
    route = dict(result_code=code, summary=dict(distance=round(distance), duration=round(duration)))
    if geometry:
        steps = max(2, int(distance // 500))
        pts = [(origin[0] + (destination[0] - origin[0]) * i / steps, origin[1] + (destination[1] - origin[1]) * i / steps)
               for i in range(steps + 1)]
        roads = []
        for i in range(0, steps, 10):
            chunk = pts[i:i + 11]
            length = sum(haversine(*chunk[k], *chunk[k + 1]) for k in range(len(chunk) - 1))
            roads.append(dict(name='road', distance=round(length), duration=round(length / SPEED),
                              vertexes=[c for lat, lon in chunk for c in (lon, lat)]))
        route['sections'] = [dict(distance=round(distance), duration=round(duration), roads=roads)]
    return json.dumps(dict(routes=[route])).encode()


class CorridorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1800000000
        self.kakao_calls, self.opinet_calls = [], []
        self.kakao_failure = None
        self.result_code = 0
        self.empty_everywhere = False
        self.recommend_extra_m = 0

        def kakao(url, key, timeout, limit):
            self.kakao_calls.append((url, key))
            if self.kakao_failure:
                raise self.kakao_failure
            q = {k: v[0] for k, v in parse_qs(urlsplit(url).query).items()}
            point = lambda text: tuple(reversed([float(v) for v in text.split(',')]))
            return straight_route_json(point(q['origin']), point(q['destination']),
                                       point(q['waypoints']) if 'waypoints' in q else None,
                                       q['summary'] == 'false', code=self.result_code,
                                       base_extra_m=self.recommend_extra_m if q['priority'] == 'RECOMMEND' else 0)

        def opinet(url, timeout, limit):
            self.opinet_calls.append(url)
            q = {k: v[0] for k, v in parse_qs(urlsplit(url).query).items()}
            cx, cy, radius = float(q['x']), float(q['y']), float(q['radius'])
            rows = []
            for sid, (lat, lon) in STATIONS.items():
                x, y = katec.from_wgs84(lat, lon)
                d = math.hypot(x - cx, y - cy)
                if d <= radius and not self.empty_everywhere:
                    rows.append(f'<OIL><UNI_ID>{sid}</UNI_ID><OS_NM>{sid} station</OS_NM><PRICE>{PRICES[sid]}</PRICE>'
                                f'<DISTANCE>{d:.1f}</DISTANCE><GIS_X_COOR>{x:.3f}</GIS_X_COOR><GIS_Y_COOR>{y:.3f}</GIS_Y_COOR></OIL>')
            return ('<RESULT>' + ''.join(rows) + '</RESULT>').encode()

        self.routing = KakaoDirections('KAKAOSECRET', kakao)
        self.opinet = OpinetProvider('OPINETSECRET', opinet)
        self.quota = SqliteQuota(Path(self.tmp.name) / 'quota.sqlite', daily_limit=300)
        self.routing_quota = SqliteQuota(Path(self.tmp.name) / 'routing.sqlite', daily_limit=1000, ceiling=10000)
        self.corridor = RouteCorridor(self.routing, self.opinet, self.quota, self.routing_quota,
                                      clock=lambda: self.now, workers=2)

    def tearDown(self):
        self.tmp.cleanup()

    def query(self, origin=ORIGIN, destination=DEST, fuel='B027', token=None, navigation_m=None):
        query = dict(origin=dict(latitude=origin[0], longitude=origin[1]),
                     destination=dict(latitude=destination[0], longitude=destination[1]), fuel=fuel)
        if token is not None:
            query['corridorToken'] = token
        if navigation_m is not None:
            query['navigationRemainingMeters'] = navigation_m
        return query

    def priorities(self):
        return [parse_qs(urlsplit(u).query)['priority'][0] for u, _ in self.kakao_calls]

    def test_route_matching_the_navigation_is_preferred(self):
        straight = haversine(*ORIGIN, *DEST)
        self.recommend_extra_m = 8000   # Kakao's recommendation takes another road than the vehicle navigation
        status, result = self.corridor.measure(self.query(navigation_m=straight))
        self.assertEqual(200, status)
        self.assertEqual('DISTANCE', result['provenance']['routingPriority'])
        self.assertAlmostEqual(straight, result['route']['remainingDistanceMeters'], delta=2)
        # Both base routes, then the via-station routes on the chosen priority only.
        self.assertEqual(['RECOMMEND', 'DISTANCE', 'DISTANCE', 'DISTANCE'], self.priorities())

    def test_unhinted_corridor_is_rebuilt_once_the_navigation_distance_disagrees(self):
        straight = haversine(*ORIGIN, *DEST)
        self.recommend_extra_m = 8000
        token = self.corridor.measure(self.query())[1]['corridorToken']
        result = self.corridor.measure(self.query(token=token, navigation_m=straight))[1]
        self.assertEqual('DISTANCE', result['provenance']['routingPriority'])
        self.assertNotEqual(token, result['corridorToken'])
        calls = len(self.kakao_calls)
        again = self.corridor.measure(self.query(token=result['corridorToken'], navigation_m=straight))[1]
        self.assertEqual(result['corridorToken'], again['corridorToken'])
        self.assertEqual(calls, len(self.kakao_calls))

    def test_recommended_route_is_kept_when_it_agrees_or_nothing_is_known(self):
        straight = haversine(*ORIGIN, *DEST)
        self.recommend_extra_m = 1500   # inside max(2 km, 15 %)
        result = self.corridor.measure(self.query(navigation_m=straight))[1]
        self.assertEqual('RECOMMEND', result['provenance']['routingPriority'])
        self.recommend_extra_m = 8000
        result = self.corridor.measure(self.query())[1]
        self.assertEqual('RECOMMEND', result['provenance']['routingPriority'])
        self.assertNotIn('DISTANCE', self.priorities())

    def test_stations_along_route_carry_routed_metrics(self):
        status, result = self.corridor.measure(self.query())
        self.assertEqual(200, status, result)
        self.assertEqual('available', result['status'])
        self.assertEqual(['S1', 'S5'], [s['id'] for s in result['stations']])
        s1 = result['stations'][0]
        self.assertAlmostEqual(10000, s1['alongRouteMeters'], delta=30)
        self.assertAlmostEqual(s1['alongRouteMeters'] + 500, s1['roadDistanceMeters'], delta=0.2)
        self.assertEqual(120, s1['detourSeconds'])
        self.assertAlmostEqual(10000 / SPEED + 60, s1['etaSeconds'], delta=4)
        self.assertEqual(1700, s1['priceKrwPerLiter'])
        self.assertAlmostEqual(STATIONS['S1'][0], s1['latitude'], delta=1e-6)
        self.assertTrue(result['coverage']['verified'])
        self.assertEqual('KAKAO_MOBILITY', result['provenance']['routingProvider'])
        self.assertEqual(self.now + 30, result['expiresAtEpochSeconds'])
        self.assertEqual(self.now, result['stationsObservedAtEpochSeconds'])
        self.assertAlmostEqual(haversine(*ORIGIN, *DEST), result['route']['remainingDistanceMeters'], delta=2)
        # One base route with geometry, one via-route per chosen station.
        self.assertEqual(3, len(self.kakao_calls))
        self.assertTrue(all(key == 'KAKAOSECRET' for _, key in self.kakao_calls))
        self.assertTrue(all('KAKAOSECRET' not in url for url, _ in self.kakao_calls))
        self.assertTrue(all(parse_qs(urlsplit(u).query)['radius'] == ['5000'] for u in self.opinet_calls))
        text = json.dumps(result)
        self.assertNotIn('SECRET', text)
        self.assertNotIn('providerDistanceMeters', text)

    def test_corridor_is_reused_with_its_token_while_on_route_and_rebuilt_when_off_route(self):
        token = self.corridor.measure(self.query())[1]['corridorToken']
        self.assertRegex(token, r'^[A-Za-z0-9_-]{22}$')
        kakao, opinet = len(self.kakao_calls), len(self.opinet_calls)
        self.now += 20
        status, result = self.corridor.measure(self.query(origin=offset(5000, 50), token=token))
        self.assertEqual(200, status)
        self.assertEqual(token, result['corridorToken'])
        self.assertEqual((kakao, opinet), (len(self.kakao_calls), len(self.opinet_calls)))
        self.assertAlmostEqual(5000, result['stations'][0]['alongRouteMeters'], delta=30)
        self.assertEqual(self.now + 30, result['expiresAtEpochSeconds'])
        status, result = self.corridor.measure(self.query(origin=offset(13000, 0), token=token))
        self.assertEqual(['S5'], [s['id'] for s in result['stations']])
        # 900 m east of the route: a new corridor (new token) from the new origin; samples come from cache.
        status, result = self.corridor.measure(self.query(origin=offset(13000, 900), token=token))
        self.assertEqual(200, status)
        self.assertNotEqual(token, result['corridorToken'])
        self.assertGreater(len(self.kakao_calls), kakao)

    def test_another_caller_cannot_reuse_or_probe_a_trip(self):
        token = self.corridor.measure(self.query())[1]['corridorToken']
        kakao = len(self.kakao_calls)
        # Same destination and an origin on that route, but no token or an unknown one: always a fresh build.
        for other in (None, 'A' * 22):
            status, result = self.corridor.measure(self.query(origin=offset(5000, 0), token=other))
            self.assertEqual(200, status)
            self.assertNotEqual(token, result['corridorToken'])
        base_routes = [u for u, _ in self.kakao_calls[kakao:] if 'summary=false' in u]
        self.assertEqual(2, len(base_routes))
        # A token only unlocks its own destination and fuel.
        self.assertNotEqual(token, self.corridor.measure(self.query(fuel='D047', token=token))[1].get('corridorToken'))

    def test_corridor_expires(self):
        token = self.corridor.measure(self.query())[1]['corridorToken']
        kakao = len(self.kakao_calls)
        self.now += 600
        self.corridor.measure(self.query(token=token))
        self.assertGreater(len(self.kakao_calls), kakao)

    def test_provider_budget_is_reserved_all_or_nothing_and_keeps_a_share_for_discover(self):
        # 300 - 60 kept for /discover leaves 240; burn 238 so a corridor needing more than 2 searches cannot start.
        self.assertTrue(self.quota.consume(self.now, units=238))
        status, result = self.corridor.measure(self.query())
        self.assertEqual((429, 'QUOTA_EXHAUSTED'), (status, result['reason']))
        self.assertEqual([], self.opinet_calls)
        self.assertTrue(self.quota.consume(self.now, units=60))  # the /discover share is still there

    def test_provider_calls_never_exceed_the_reservation_when_the_cache_is_evicted_mid_build(self):
        self.corridor.measure(self.query())
        reserved_before, calls_before = self.used(), len(self.opinet_calls)
        consume = self.quota.consume

        def consume_then_evict(*args, **kwargs):
            granted = consume(*args, **kwargs)
            self.corridor._samples.clear()  # another request evicts every cached sample right after reserving
            return granted
        self.quota.consume = consume_then_evict
        # A different origin on the same road: some samples cached, a few new ones needed.
        status, _ = self.corridor.measure(self.query(origin=offset(-3000, 0)))
        self.assertEqual(200, status)
        self.assertGreater(len(self.opinet_calls), calls_before)
        self.assertEqual(self.used() - reserved_before, len(self.opinet_calls) - calls_before)

    def used(self):
        import sqlite3
        with sqlite3.connect(self.quota.path) as db:
            row = db.execute('SELECT calls FROM quota WHERE id=1').fetchone()
        return row[0] if row else 0

    def test_routing_calls_are_metered(self):
        tight = SqliteQuota(Path(self.tmp.name) / 'tight-routing', daily_limit=2, ceiling=10000)
        corridor = RouteCorridor(self.routing, self.opinet, self.quota, tight, clock=lambda: self.now)
        # Base route fits; measuring two stations does not: refused before any via-station request.
        status, result = corridor.measure(self.query())
        self.assertEqual((429, 'ROUTING_QUOTA_EXHAUSTED'), (status, result['reason']))
        self.assertEqual(1, len(self.kakao_calls))
        # The last unit pays for one more base route; after that nothing reaches Kakao at all.
        corridor.measure(self.query())
        self.assertEqual(2, len(self.kakao_calls))
        status, result = corridor.measure(self.query())
        self.assertEqual((429, 'ROUTING_QUOTA_EXHAUSTED'), (status, result['reason']))
        self.assertEqual(2, len(self.kakao_calls))

    def test_every_sample_empty_fails_closed(self):
        self.empty_everywhere = True
        status, result = self.corridor.measure(self.query())
        self.assertEqual((503, 'PROVIDER_SCHEMA_INVALID'), (status, result['reason']))

    def test_routing_failures_are_sanitized(self):
        self.kakao_failure = RuntimeError('KAKAOSECRET https://apis-navi.kakaomobility.com')
        status, result = self.corridor.measure(self.query())
        self.assertEqual((503, 'ROUTING_UNAVAILABLE'), (status, result['reason']))
        self.assertNotIn('SECRET', json.dumps(result))
        self.assertEqual([], self.opinet_calls)
        self.kakao_failure = None
        self.result_code = 104
        self.assertEqual('ROUTE_NOT_FOUND', self.corridor.measure(self.query())[1]['reason'])

    def test_unconfigured_keys_fail_without_network(self):
        corridor = RouteCorridor(KakaoDirections('', lambda *a: self.fail('network')), self.opinet, self.quota, self.routing_quota)
        self.assertEqual((503, 'ROUTING_UNCONFIGURED'), (corridor.measure(self.query())[0], corridor.measure(self.query())[1]['reason']))
        corridor = RouteCorridor(self.routing, OpinetProvider(' '), self.quota, self.routing_quota)
        self.assertEqual('PROVIDER_UNCONFIGURED', corridor.measure(self.query())[1]['reason'])
        self.assertEqual([], self.kakao_calls)

    def test_rejects_malformed_queries_without_network(self):
        good = self.query()
        bad = [dict(good, extra=1), dict(good, fuel='X'), {k: v for k, v in good.items() if k != 'fuel'},
               dict(good, origin=dict(latitude=float('nan'), longitude=127.0)),
               dict(good, origin=dict(latitude=True, longitude=127.0)),
               dict(good, origin=dict(latitude=37.4, longitude=127.0, altitude=0)),
               dict(good, destination=dict(latitude=35.0, longitude=140.0)), [], 'x',
               dict(good, corridorToken='short'), dict(good, corridorToken=1), dict(good, corridorToken='A' * 21 + '/'),
               dict(good, navigationRemainingMeters=-1), dict(good, navigationRemainingMeters='1000'),
               dict(good, navigationRemainingMeters=float('nan')), dict(good, navigationRemainingMeters=True)]
        for query in bad:
            self.assertEqual(400, self.corridor.measure(query)[0], query)
        self.assertEqual(([], []), (self.kakao_calls, self.opinet_calls))

    def test_quota_exhaustion_is_429(self):
        corridor = RouteCorridor(self.routing, self.opinet, SqliteQuota(Path(self.tmp.name) / 'tiny', daily_limit=2),
                                 self.routing_quota, clock=lambda: self.now)
        status, result = corridor.measure(self.query())
        self.assertEqual((429, 'QUOTA_EXHAUSTED'), (status, result['reason']))

    def test_overlong_route_is_refused_before_station_queries(self):
        status, result = self.corridor.measure(self.query(destination=(34.9, 127.10)))
        self.assertEqual((503, 'ROUTE_TOO_LONG'), (status, result['reason']))
        self.assertEqual([], self.opinet_calls)

    def test_http_route_endpoint(self):
        server = HTTPServer(('127.0.0.1', 0), handler_for(Gateway(self.opinet, self.quota), self.corridor))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
            connection.request('POST', '/v1/stations/route', json.dumps(self.query()), {'Content-Type': 'application/json'})
            response = connection.getresponse()
            self.assertEqual(200, response.status)
            self.assertEqual(['S1', 'S5'], [s['id'] for s in json.loads(response.read())['stations']])
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        unrouted = handler_for(Gateway(self.opinet, self.quota))
        server = HTTPServer(('127.0.0.1', 0), unrouted)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
            connection.request('POST', '/v1/stations/route', json.dumps(self.query()), {'Content-Type': 'application/json'})
            self.assertEqual(404, connection.getresponse().status)
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


class CoverageTests(unittest.TestCase):
    def winding_route(self, seed):
        rng = random.Random(seed)
        lat, lon, heading = 37.0 + rng.random(), 127.0 + rng.random(), rng.random() * 2 * math.pi
        points, along = [(lat, lon, 0.0, 0.0)], 0.0
        for _ in range(300):
            heading += rng.uniform(-0.6, 0.6)
            step = rng.uniform(50, 900)
            lat += step * math.cos(heading) / M_PER_DEG_LAT
            lon += step * math.sin(heading) / (math.cos(math.radians(lat)) * M_PER_DEG_LAT)
            along += haversine(points[-1][0], points[-1][1], lat, lon)
            points.append((lat, lon, along, along / SPEED))
        return Route(along, along / SPEED, points)

    def test_every_station_within_lateral_limit_was_searched(self):
        for seed in range(5):
            route = self.winding_route(seed)
            centres = sample_centres(route)
            rng = random.Random(seed + 100)
            for _ in range(400):
                lat, lon = route.point_at(rng.uniform(0, route.distance))
                angle, lateral = rng.uniform(0, 2 * math.pi), rng.uniform(0, MAX_LATERAL_M)
                lat += lateral * math.cos(angle) / M_PER_DEG_LAT
                lon += lateral * math.sin(angle) / (math.cos(math.radians(lat)) * M_PER_DEG_LAT)
                x, y = katec.from_wgs84(lat, lon)
                self.assertLessEqual(min(math.hypot(x - cx, y - cy) for cx, cy in centres), SEARCH_RADIUS_M)
            for lat, lon in route.densify(200):
                x, y = katec.from_wgs84(lat, lon)
                self.assertLessEqual(min(math.hypot(x - cx, y - cy) for cx, cy in centres), COVERAGE_RADIUS_M)

    def test_projection_index_matches_brute_force(self):
        route = self.winding_route(7)
        rng = random.Random(8)
        for _ in range(300):
            lat, lon = route.point_at(rng.uniform(0, route.distance))
            lat += rng.uniform(-1500, 1500) / M_PER_DEG_LAT
            along, lateral = route.project(lat, lon)
            brute = min(haversine(lat, lon, *route.point_at(a)) for a in range(0, int(route.distance), 20))
            if brute > MAX_LATERAL_M + 30:
                self.assertEqual(math.inf, lateral)
            elif brute < MAX_LATERAL_M - 30:
                self.assertAlmostEqual(brute, lateral, delta=max(15, brute * 0.02))
                self.assertAlmostEqual(brute, haversine(lat, lon, *route.point_at(along)), delta=max(15, brute * 0.02))


class KatecTests(unittest.TestCase):
    VECTORS = [((37.5027, 127.0364), (314996.879, 544938.297)), ((35.1796, 129.0756), (498164.716, 287275.006)),
               ((33.4996, 126.5312), (263722.204, 101365.130)), ((38.2070, 128.5918), (452020.876, 622835.378)),
               ((37.4563, 126.7052), (285641.885, 540139.606))]

    def test_matches_proj_vectors_and_round_trips(self):
        for (lat, lon), (x, y) in self.VECTORS:
            fx, fy = katec.from_wgs84(lat, lon)
            self.assertAlmostEqual(x, fx, delta=0.01)
            self.assertAlmostEqual(y, fy, delta=0.01)
            back = katec.to_wgs84(x, y)
            self.assertAlmostEqual(lat, back[0], delta=1e-7)
            self.assertAlmostEqual(lon, back[1], delta=1e-7)


class KakaoParsingTests(unittest.TestCase):
    def test_rejects_malformed_or_out_of_korea_geometry(self):
        good = json.loads(straight_route_json(ORIGIN, DEST, None, True))
        variants = []
        broken = json.loads(json.dumps(good)); broken['routes'][0]['sections'][0]['roads'][0]['vertexes'] = [127.0]
        variants.append(broken)
        broken = json.loads(json.dumps(good)); broken['routes'][0]['sections'][0]['roads'][0]['vertexes'][1] = 10.0
        variants.append(broken)
        broken = json.loads(json.dumps(good)); broken['routes'][0]['summary']['duration'] = 'NaN'
        variants.append(broken)
        variants.append(dict(routes=[]))
        for body in variants:
            routing = KakaoDirections('K', lambda *a, body=body: json.dumps(body).encode())
            with self.assertRaises(ProviderFailure) as raised:
                routing.route(ORIGIN, DEST, geometry=True)
            self.assertEqual('ROUTING_SCHEMA_INVALID', raised.exception.reason)
        route = KakaoDirections('K', lambda *a: json.dumps(good).encode()).route(ORIGIN, DEST, geometry=True)
        self.assertAlmostEqual(route.duration, route.points[-1][3], delta=1e-6)
        self.assertAlmostEqual(haversine(*ORIGIN, *DEST), route.points[-1][2], delta=1)


if __name__ == '__main__':
    unittest.main()
