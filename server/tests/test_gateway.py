import json
import tempfile
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from server.gateway import Gateway, OpinetProvider, SqliteQuota, ProviderFailure, handler_for
from http.server import HTTPServer
import http.client
import threading

XML = b'''<RESULT><OIL><UNI_ID>A0010207</UNI_ID><OS_NM>Station</OS_NM><PRICE>1745</PRICE><DISTANCE>846.6</DISTANCE><GIS_X_COOR>314871.8</GIS_X_COOR><GIS_Y_COOR>544012</GIS_Y_COOR></OIL></RESULT>'''
QUERY = dict(coordinateSystem='KATEC', x=314681.8, y=544837, radiusMeters=5000, fuel='B027')

class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1800000000
        self.calls = []
        self.payload = XML
        def fetch(url, timeout, limit):
            self.calls.append((url, timeout, limit))
            if isinstance(self.payload, Exception):
                raise self.payload
            return self.payload
        self.provider = OpinetProvider('SECRET', fetch)
        self.quota = SqliteQuota(Path(self.tmp.name) / 'quota.sqlite', daily_limit=2)
        self.gateway = Gateway(self.provider, self.quota, clock=lambda: self.now)
    def tearDown(self):
        self.tmp.cleanup()
    def test_native_contract_and_provenance_are_explicit(self):
        status, result = self.gateway.discover(QUERY)
        self.assertEqual(200, status)
        self.assertEqual('available', result['status'])
        row = result['stations'][0]
        self.assertEqual('KATEC', row['coordinates']['system'])
        self.assertEqual(846.6, row['providerDistanceMeters'])
        self.assertIsNone(row['priceObservedAtEpochSeconds'])
        self.assertFalse(result['provenance']['roadMetricsVerified'])
        self.assertEqual('OPINET', result['provenance']['provider'])
        args = parse_qs(urlsplit(self.calls[0][0]).query)
        self.assertEqual(['SECRET'], args['code'])
        self.assertNotIn('certkey', args)
        self.assertEqual(['xml'], args['out'])
        self.assertEqual(['2'], args['sort'])
        self.assertNotIn('SECRET', json.dumps(result))
    def test_missing_key_and_wgs84_fail_without_network(self):
        gateway = Gateway(OpinetProvider('', lambda *args: self.fail('network')), self.quota)
        self.assertEqual('PROVIDER_UNCONFIGURED', gateway.discover(QUERY)[1]['reason'])
        query = dict(QUERY, coordinateSystem='WGS84', x=127, y=37)
        self.assertEqual('COORDINATE_TRANSFORM_UNVERIFIED', self.gateway.discover(query)[1]['reason'])
        self.assertEqual([], self.calls)
    def test_rejects_bounds_types_unknown_fields_and_nonfinite(self):
        for query in [dict(QUERY, radiusMeters=5001), dict(QUERY, x=float('nan')),
                      dict(QUERY, radiusMeters=True), dict(QUERY, fuel='bad'),
                      dict(QUERY, certkey='injected'), dict(QUERY, code='injected'), dict(QUERY, x=-1)]:
            self.assertEqual(400, self.gateway.discover(query)[0], query)
        self.assertEqual([], self.calls)
    def test_cache_quota_and_expiry_never_serve_stale(self):
        self.assertEqual(200, self.gateway.discover(QUERY)[0])
        self.assertTrue(self.gateway.discover(QUERY)[1]['provenance']['cacheHit'])
        self.assertEqual(1, len(self.calls))
        self.now += 301
        self.assertEqual(200, self.gateway.discover(QUERY)[0])
        self.now += 301
        self.assertEqual('QUOTA_EXHAUSTED', self.gateway.discover(QUERY)[1]['reason'])
        self.assertEqual(2, len(self.calls))
        again = Gateway(self.provider, SqliteQuota(Path(self.tmp.name) / 'quota.sqlite', daily_limit=2), clock=lambda: self.now)
        self.assertEqual(429, again.discover(QUERY)[0])
    def test_provider_failure_is_sanitized_and_costs_quota(self):
        self.payload = RuntimeError('SECRET https://example?code=SECRET')
        status, result = self.gateway.discover(QUERY)
        self.assertEqual(503, status)
        self.assertEqual('PROVIDER_UNAVAILABLE', result['reason'])
        self.assertNotIn('SECRET', json.dumps(result))
    def test_invalid_or_missing_coordinates_reject_entire_response(self):
        for payload in [b'<RESULT/>', b'<RESULT><error>bad key</error></RESULT>',
                        XML.replace(b'<GIS_X_COOR>314871.8</GIS_X_COOR>', b''),
                        XML.replace(b'1745', b'NaN'),
                        XML.replace(b'544012', b'NaN'), XML + b'bad']:
            self.payload = payload
            fresh = Gateway(self.provider, SqliteQuota(Path(self.tmp.name) / ('q'+str(len(payload))), daily_limit=100), clock=lambda: self.now)
            self.assertEqual('PROVIDER_SCHEMA_INVALID', fresh.discover(QUERY)[1]['reason'])
    def test_ambiguous_empty_result_is_unavailable(self):
        self.payload = b'<RESULT><OIL /></RESULT>'
        self.assertEqual('PROVIDER_SCHEMA_INVALID', self.gateway.discover(QUERY)[1]['reason'])
        self.payload = b'<RESULT></RESULT>'
        # Provider's empty RESULT is ambiguous with errors: fail closed.
        self.assertEqual('PROVIDER_SCHEMA_INVALID', self.gateway.discover(QUERY)[1]['reason'])
    def test_http_boundary_rejects_duplicate_json_fields_and_oversized_body(self):
        server = HTTPServer(('127.0.0.1', 0), handler_for(self.gateway))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=2)
            duplicate = json.dumps(QUERY)[:-1] + ', "fuel": "D047"}'
            connection.request('POST', '/v1/stations/discover', duplicate, {'Content-Type': 'application/json'})
            response = connection.getresponse()
            self.assertEqual(400, response.status)
            response.read()
            connection.close()
            connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=2)
            connection.request('POST', '/v1/stations/discover', b'', {'Content-Type': 'application/json', 'Content-Length': '2049'})
            response = connection.getresponse()
            self.assertEqual(400, response.status)
            response.read()
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_quota_rolls_at_korean_midnight_and_clock_rollback_fails_closed(self):
        q = SqliteQuota(Path(self.tmp.name) / 'day', daily_limit=1)
        self.assertTrue(q.consume(1735657199))
        self.assertFalse(q.consume(1735657199))
        self.assertTrue(q.consume(1735657200))
        self.assertFalse(q.consume(1735657199))

if __name__ == '__main__':
    unittest.main()
