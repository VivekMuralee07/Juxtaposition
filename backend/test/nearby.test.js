const test = require('node:test');
const assert = require('node:assert');
const { nearby, normalise, bearingDeg, compass, BadRequest, _cache } = require('../src/nearby');

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

test('nearby: Overpass "remark" timeout is a failure, tried on next mirror, and never cached', async () => {
  _cache.clear();
  let n = 0;
  const busy = async () => { n++; return { ok: true, json: async () => (n <= 2 ? { elements: [], remark: 'runtime error: Query timed out in "query" at line 1 after 26 seconds.' } : overpass) }; };
  await assert.rejects(nearby({ lat: 28.61, lng: 77.2 }, { fetchImpl: busy }), /timed out/); // both mirrors busy
  assert.strictEqual(_cache.size, 0, 'failed lookup must not be cached');
  const ok = await nearby({ lat: 28.61, lng: 77.2 }, { fetchImpl: busy });             // next attempt succeeds
  assert.ok(ok.places.length > 0);
  // a genuine empty answer (no remark) is still a valid, cacheable result
  _cache.clear();
  const none = await nearby({ lat: 0.5, lng: 0.5 }, { fetchImpl: async () => ({ ok: true, json: async () => ({ elements: [] }) }) });
  assert.deepStrictEqual(none.places, []);
});
