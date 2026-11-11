"""Key-isolated Opinet catalog gateway. No route safety claims or CRS guesses."""
from collections import OrderedDict
from contextlib import closing
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import math
import os
from pathlib import Path
import sqlite3
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

MAX_BYTES = 262144
MAX_STATIONS = 200
CACHE_TTL_SECONDS = 300
FUELS = {'B027', 'D047', 'B034', 'C004', 'K015'}


class ProviderFailure(Exception):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


def number(value, low, high):
    if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError('invalid number')
    return value


def validate_query(query):
    if not isinstance(query, dict) or set(query) != {'coordinateSystem', 'x', 'y', 'radiusMeters', 'fuel'}:
        raise ValueError('invalid query')
    system = query['coordinateSystem']
    if system not in ('KATEC', 'WGS84') or query['fuel'] not in FUELS:
        raise ValueError('invalid query')
    if type(query['radiusMeters']) is not int:
        raise ValueError('invalid radius')
    number(query['radiusMeters'], 1, 5000)
    if system == 'KATEC':
        number(query['x'], 0, 2000000)
        number(query['y'], 0, 2000000)
    else:
        number(query['x'], -180, 180)
        number(query['y'], -90, 90)
    return query


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward certkey to another URL/host, including provider redirects.
        raise ProviderFailure('PROVIDER_UNAVAILABLE')


def https_fetch(url, timeout, limit):
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(url, timeout=timeout) as response:
        if response.status != 200:
            raise ProviderFailure('PROVIDER_UNAVAILABLE')
        body = response.read(limit + 1)
        if len(body) > limit:
            raise ProviderFailure('PROVIDER_SCHEMA_INVALID')
        return body


class OpinetProvider:
    def __init__(self, api_key, fetch=https_fetch):
        self._api_key = api_key
        self._fetch = fetch

    @property
    def configured(self):
        return bool(self._api_key and self._api_key.strip())

    def discover(self, query):
        params = dict(certkey=self._api_key, out='xml', x=query['x'], y=query['y'],
                      radius=query['radiusMeters'], prodcd=query['fuel'], sort=2)
        url = 'https://www.opinet.co.kr/api/aroundAll.do?' + urllib.parse.urlencode(params)
        try:
            body = self._fetch(url, 4, MAX_BYTES)
        except Exception:
            raise ProviderFailure('PROVIDER_UNAVAILABLE') from None
        try:
            if len(body) > MAX_BYTES or b'<!' in body:
                raise ValueError('size or XML declaration')
            root = ET.fromstring(body)
            rows = list(root)
            if root.tag != 'RESULT' or not 1 <= len(rows) <= MAX_STATIONS or any(row.tag != 'OIL' for row in rows):
                raise ValueError('schema')
            stations = []
            ids = set()
            for row in rows:
                def field(name):
                    values = row.findall(name)
                    if len(values) != 1 or not values[0].text:
                        raise ValueError('missing field')
                    return values[0].text.strip()
                station_id, name = field('UNI_ID'), field('OS_NM')
                if not station_id or len(station_id) > 64 or station_id in ids or not name or len(name) > 200:
                    raise ValueError('identity')
                ids.add(station_id)
                price = number(float(field('PRICE')), 1, 100000)
                distance = number(float(field('DISTANCE')), 0, 100000)
                x = number(float(field('GIS_X_COOR')), 0, 2000000)
                y = number(float(field('GIS_Y_COOR')), 0, 2000000)
                stations.append(dict(id=station_id, name=name, fuel=query['fuel'], priceKrwPerLiter=price,
                                     coordinates=dict(system='KATEC', x=x, y=y), providerDistanceMeters=distance,
                                     priceObservedAtEpochSeconds=None))
            return stations
        except (ValueError, TypeError, ET.ParseError, OverflowError):
            raise ProviderFailure('PROVIDER_SCHEMA_INVALID') from None


class SqliteQuota:
    """Persistent global quota shared across restarts/processes; attempts count too."""
    def __init__(self, path, daily_limit=300):
        if type(daily_limit) is not int or not 1 <= daily_limit <= 300:
            raise ValueError('daily_limit must be 1..300')
        self.path, self.limit = str(path), daily_limit
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute('CREATE TABLE IF NOT EXISTS quota (id INTEGER PRIMARY KEY CHECK(id=1), day TEXT NOT NULL, calls INTEGER NOT NULL)')

    def consume(self, now):
        day = datetime.fromtimestamp(now, timezone(timedelta(hours=9))).date().isoformat()
        with closing(sqlite3.connect(self.path, timeout=2)) as db, db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT day,calls FROM quota WHERE id=1').fetchone()
            if row and (day < row[0] or (day == row[0] and row[1] >= self.limit)):
                return False
            count = row[1] + 1 if row and row[0] == day else 1
            db.execute('INSERT OR REPLACE INTO quota VALUES (1,?,?)', (day, count))
            return True


class Gateway:
    def __init__(self, provider, quota, clock=time.time):
        self.provider, self.quota, self.clock = provider, quota, clock
        self._cache = OrderedDict()
        self._lock = threading.Lock()

    def discover(self, query):
        def error(code, reason):
            return code, dict(schemaVersion=1, status='unavailable', reason=reason)
        try:
            validate_query(query)
        except (ValueError, TypeError, OverflowError):
            return error(400, 'INVALID_QUERY')
        if query['coordinateSystem'] != 'KATEC':
            return error(503, 'COORDINATE_TRANSFORM_UNVERIFIED')
        if not self.provider.configured:
            return error(503, 'PROVIDER_UNCONFIGURED')
        key = json.dumps(query, sort_keys=True, allow_nan=False)
        with self._lock:
            now = int(self.clock())
            cached = self._cache.get(key)
            if cached and 0 <= now - cached['fetchedAtEpochSeconds'] < CACHE_TTL_SECONDS:
                result = dict(cached, provenance=dict(cached['provenance'], cacheHit=True))
                return 200, result
            self._cache.pop(key, None)
            try:
                if not self.quota.consume(now):
                    return error(429, 'QUOTA_EXHAUSTED')
                stations = self.provider.discover(query)
            except ProviderFailure as failure:
                return error(503, failure.reason)
            except Exception:
                return error(503, 'PROVIDER_UNAVAILABLE')
            result = dict(schemaVersion=1, status='available', fetchedAtEpochSeconds=now,
                          expiresAtEpochSeconds=now + CACHE_TTL_SECONDS, stations=stations,
                          provenance=dict(provider='OPINET', endpoint='aroundAll', coordinateSystem='KATEC',
                                          roadMetricsVerified=False, cacheHit=False))
            self._cache[key] = result
            while len(self._cache) > 128:
                self._cache.popitem(last=False)
            return 200, result


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate field')
        result[key] = value
    return result


def handler_for(gateway):
    class Handler(BaseHTTPRequestHandler):
        # Bind behind a TLS reverse proxy; suppress access logs/location/credential details.
        def log_message(self, format, *args):
            pass

        def do_POST(self):
            self.connection.settimeout(5)
            if self.path != '/v1/stations/discover':
                self.send_error(404)
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 2048 or self.headers.get('Transfer-Encoding') is not None:
                    raise ValueError('length')
                if self.headers.get('Content-Type', '').split(';')[0].strip() != 'application/json':
                    raise ValueError('content type')
                body = self.rfile.read(length)
                if len(body) != length:
                    raise ValueError('truncated')
                query = json.loads(body, object_pairs_hook=strict_object)
                code, result = gateway.discover(query)
            except (ValueError, UnicodeError, TimeoutError):
                code, result = 400, dict(schemaVersion=1, status='unavailable', reason='INVALID_QUERY')
            payload = json.dumps(result, allow_nan=False).encode('utf-8')
            self.send_response(code)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(payload)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(payload)
    return Handler


def main():
    # Only the server reads this environment variable; never put it in app config.
    provider = OpinetProvider(os.environ.get('OPINET_API_KEY', ''))
    quota = SqliteQuota(Path(os.environ.get('FUEL_PILOT_QUOTA_DB', 'opinet-quota.sqlite')))
    HTTPServer(('127.0.0.1', 8787), handler_for(Gateway(provider, quota))).serve_forever()


if __name__ == '__main__':
    main()
