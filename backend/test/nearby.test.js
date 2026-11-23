const test = require('node:test');
const assert = require('node:assert');
process.env.OVERPASS_HEDGE_MS = '25'; // tests don't wait seconds before asking the next mirror
process.env.OVERPASS_TIMEOUT_MS = '400';
const { nearby, normalise, bearingDeg, compass, BadRequest, UpstreamError, _cache } = require('../src/nearby');

const ORIGIN = { lat: 12.9716, lng: 77.5946 };
const overpass = {
  elements: [
    { type: 'node', id: 1, lat: 12.9806, lon: 77.5946, tags: { name: 'North Cafe', amenity: 'cafe', cuisine: 'coffee_shop;dessert', 'addr:street': 'MG Road', phone: '+91 80 1' } },
    { type: 'way', id: 2, center: { lat: 12.9716, lon: 77.6036 }, tags: { name: 'East Biryani', amenity: 'restaurant', 'diet:vegetarian': 'yes', opening_hours: 'Mo-Su 10:00-23:00' } },
    { type: 'node', id: 3, lat: 12.9716, lon: 77.6036, tags: { name: 'East Biryani', amenity: 'restaurant' } }, // near-duplicate position+name
    { type: 'node', id: 4, lat: 12.97, lon: 77.59, tags: { amenity: 'restaurant' } }, // unnamed -> skipped
    { type: 'node', id: 5, lat: 12.99, lon: 77.60, tags: { name: 'Far Place', amenity: 'fast_food' } },
  ],
};
const mockFetch = (calls = []) => async (url, opts) => { calls.push({ url, opts }); return { ok: true, json: async () => overpass }; };

test('bearing and compass', () => {
  assert.strictEqual(bearingDeg(ORIGIN, { lat: 13.0, lng: 77.5946 }), 0);
  assert.strictEqual(bearingDeg(ORIGIN, { lat: 12.9716, lng: 77.7 }), 90);
  assert.strictEqual(compass(0), 'N'); assert.strictEqual(compass(95), 'E'); assert.strictEqual(compass(350), 'N'); assert.strictEqual(compass(225), 'SW');
});

test('normalise: sorts by distance, drops unnamed + duplicates, maps tags', () => {
  const out = normalise(overpass.elements, ORIGIN);
  assert.deepStrictEqual(out.map((p) => p.name), ['East Biryani', 'North Cafe', 'Far Place']);
  const north = out.find((p) => p.name === 'North Cafe');
  assert.strictEqual(north.direction, 'N');
  assert.strictEqual(north.cuisine, 'coffee shop, dessert');
  assert.ok(north.distanceKm > 0.9 && north.distanceKm < 1.1, String(north.distanceKm));
  assert.strictEqual(out[0].vegetarian, 'yes');
  assert.strictEqual(out[0].id, 'osm:way/2');
});

test('nearby: validates, queries Overpass, caches', async () => {
  _cache.clear();
  await assert.rejects(nearby({}), BadRequest);
  await assert.rejects(nearby({ lat: 'x', lng: '1' }), BadRequest);
  await assert.rejects(nearby({ lat: 12.97, lng: 77.59, types: 'bank' }), BadRequest);
  const calls = [];
  const r1 = await nearby({ lat: 12.9716, lng: 77.5946, radius: 99999 }, { fetchImpl: mockFetch(calls) });
  assert.strictEqual(r1.radiusM, 5000); // clamped
  assert.strictEqual(r1.places.length, 3);
  assert.match(decodeURIComponent(calls[0].opts.body), /around:5000,12.9716,77.5946/);
  assert.ok(calls[0].opts.headers['User-Agent']);
  await nearby({ lat: 12.97163, lng: 77.59461, radius: 99999 }, { fetchImpl: mockFetch(calls) });
  assert.strictEqual(calls.length, 1, 'second nearby request served from cache');
});

test('nearby: falls back to the next Overpass mirror', async () => {
  _cache.clear();
  let n = 0;
  const flaky = async () => { n++; if (n === 1) throw new Error('boom'); return { ok: true, json: async () => overpass }; };
  const r = await nearby({ lat: 19.07, lng: 72.87 }, { fetchImpl: flaky });
  assert.strictEqual(n, 2);
  assert.ok(r.places.length > 0);
});

const REMARK = { elements: [], remark: 'runtime error: Query timed out in "query" at line 1 after 26 seconds.' };

test('nearby: Overpass "remark" timeout is a failure, never cached, and the error names every mirror', async () => {
  _cache.clear();
  let n = 0;
  const busy = async () => { n++; return { ok: true, json: async () => (n <= 4 ? REMARK : overpass) }; }; // all 4 mirrors time out, then recover
  await assert.rejects(nearby({ lat: 28.61, lng: 77.2 }, { fetchImpl: busy }), (e) => {
    assert.ok(e instanceof UpstreamError);
    assert.match(e.detail, /overpass-api\.de: Query timed out/);
    assert.match(e.detail, /overpass\.kumi\.systems/);
    assert.match(e.detail, /overpass\.private\.coffee/);
    assert.match(e.detail, /overpass\.openstreetmap\.fr/);
    return true;
  });
  assert.strictEqual(_cache.size, 0, 'failed lookup must not be cached');
  const ok = await nearby({ lat: 28.61, lng: 77.2 }, { fetchImpl: busy }); // next attempt succeeds
  assert.ok(ok.places.length > 0);
  // a genuine empty answer (no remark) is still a valid, cacheable result
  _cache.clear();
  const none = await nearby({ lat: 0.5, lng: 0.5 }, { fetchImpl: async () => ({ ok: true, json: async () => ({ elements: [] }) }) });
  assert.deepStrictEqual(none.places, []);
});

test('nearby: HTTP 429 from a mirror is reported with its status; a later mirror can still win', async () => {
  _cache.clear();
  let n = 0;
  const f = async () => { n++; return n === 1 ? { ok: false, status: 429 } : { ok: true, json: async () => overpass }; };
  assert.ok((await nearby({ lat: 22.57, lng: 88.36 }, { fetchImpl: f })).places.length > 0);
  _cache.clear();
  await assert.rejects(nearby({ lat: 22.58, lng: 88.37 }, { fetchImpl: async () => ({ ok: false, status: 429 }) }), /overpass-api\.de: HTTP 429/);
});

test('nearby: serves the previous list (stale) when every mirror fails', async () => {
  _cache.clear();
  let clock = 1_000_000;
  const now = () => clock;
  const first = await nearby({ lat: 18.52, lng: 73.85 }, { fetchImpl: mockFetch(), now });
  assert.strictEqual(first.stale, false);
  clock += 10 * 60_000; // cache (5 min) expired
  const down = async () => ({ ok: false, status: 504 });
  const second = await nearby({ lat: 18.52, lng: 73.85 }, { fetchImpl: down, now });
  assert.strictEqual(second.stale, true);
  assert.strictEqual(second.places.length, first.places.length);
  clock += 7 * 60 * 60_000; // too old to be useful
  await assert.rejects(nearby({ lat: 18.52, lng: 73.85 }, { fetchImpl: down, now }), UpstreamError);
});

test('buildQuery uses exact amenity matches (no regex) and the radius', () => {
  const { buildQuery } = require('../src/nearby');
  const q = buildQuery(12.9, 77.6, 1000, ['restaurant', 'cafe']);
  assert.match(q, /nwr\["amenity"="restaurant"\]\["name"\]\(around:1000,12.9,77.6\)/);
  assert.match(q, /nwr\["amenity"="cafe"\]/);
  assert.doesNotMatch(q, /~/);
  assert.doesNotMatch(q, /fast_food/);
});

test('hedged: a hanging first mirror does not block a fast second one, and the loser is cancelled', async () => {
  _cache.clear();
  const signals = [];
  const f = async (url, opts) => {
    signals.push(opts.signal);
    if (signals.length === 1) return new Promise((_, rej) => opts.signal.addEventListener('abort', () => rej(new Error('aborted')))); // hangs
    return { ok: true, json: async () => overpass };
  };
  const t0 = Date.now();
  const r = await nearby({ lat: 17.38, lng: 78.48 }, { fetchImpl: f });
  assert.ok(r.places.length > 0);
  assert.ok(Date.now() - t0 < 300, 'did not wait for the hanging mirror to time out');
  assert.strictEqual(signals.length, 2);
  assert.strictEqual(signals[0].aborted, true, 'hanging request cancelled once another won');
});

test('all mirrors hanging -> each reported as timeout, within the per-mirror limit', async () => {
  _cache.clear();
  const hang = (url, opts) => new Promise((_, rej) => opts.signal.addEventListener('abort', () => rej(opts.signal.reason || new Error('aborted'))));
  const t0 = Date.now();
  await assert.rejects(nearby({ lat: 13.08, lng: 80.27 }, { fetchImpl: hang }), (e) => {
    assert.ok(e instanceof UpstreamError);
    assert.match(e.detail, /overpass-api\.de: timeout/);
    assert.match(e.detail, /overpass\.kumi\.systems: timeout/);
    assert.match(e.detail, /overpass\.private\.coffee: timeout/);
    return true;
  });
  assert.ok(Date.now() - t0 < 1500);
  assert.strictEqual(_cache.size, 0);
});

test('diagnose reports each mirror separately (ok / HTTP error / timeout) without throwing', async () => {
  const { diagnose } = require('../src/nearby');
  let n = 0;
  const f = async (url, opts) => {
    n++;
    if (n === 1) return { ok: true, json: async () => ({ elements: [{ type: 'node', id: 1 }] }) };
    if (n === 2) return { ok: false, status: 429 };
    return new Promise((_, rej) => opts.signal.addEventListener('abort', () => rej(opts.signal.reason || new Error('aborted'))));
  };
  const out = await diagnose({ fetchImpl: f, timeoutMs: 60 });
  assert.strictEqual(out.length >= 3, true);
  assert.deepStrictEqual([out[0].ok, out[1].ok, out[2].ok], [true, false, false]);
  assert.strictEqual(out[1].error, 'HTTP 429');
  assert.strictEqual(out[2].error, 'timeout');
  assert.ok(out.every((m) => typeof m.ms === 'number' && m.host));
});
