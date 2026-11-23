// Nearby restaurants from OpenStreetMap (Overpass API) - free, no API key.
// Data is community-maintained: coverage is good in big cities, patchier elsewhere, and OSM has no ratings.
const { distanceKm } = require('./geo');

const ENDPOINTS = (process.env.OVERPASS_URLS ||
  'https://overpass-api.de/api/interpreter,https://overpass.kumi.systems/api/interpreter,https://overpass.private.coffee/api/interpreter').split(',');
const STALE_OK_MS = 6 * 60 * 60_000; // if every mirror fails, serve a previous answer for the same area up to this old
const USER_AGENT = 'Juxtaposition-NearbyRestaurants/1.0 (self-hosted)';
const TYPES = { restaurant: 'restaurant', fast_food: 'fast_food', cafe: 'cafe' };
const CACHE_MS = 5 * 60_000;
const MAX_RESULTS = 80;

const cache = new Map(); // key -> { at, data }
const inflight = new Map();

function bearingDeg(from, to) {
  const r = (d) => (d * Math.PI) / 180;
  const y = Math.sin(r(to.lng - from.lng)) * Math.cos(r(to.lat));
  const x = Math.cos(r(from.lat)) * Math.sin(r(to.lat)) - Math.sin(r(from.lat)) * Math.cos(r(to.lat)) * Math.cos(r(to.lng - from.lng));
  return Math.round(((Math.atan2(y, x) * 180) / Math.PI + 360) % 360);
}
const COMPASS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
const compass = (deg) => COMPASS[Math.round(deg / 45) % 8];

function buildQuery(lat, lng, radiusM, types) {
  // One exact amenity=<type> clause per type: much cheaper for Overpass than a regex over all amenities.
  const around = `(around:${radiusM},${lat},${lng})`;
  const clauses = types.map((t) => `nwr["amenity"="${t}"]["name"]${around};`).join('');
  return `[out:json][timeout:25];(${clauses});out center tags ${MAX_RESULTS * 3};`;
}

function address(t) {
  return [t['addr:housenumber'], t['addr:street'], t['addr:suburb'] || t['addr:neighbourhood'], t['addr:city']].filter(Boolean).join(', ') || null;
}

function normalise(elements, origin) {
  const seen = new Set();
  const out = [];
  for (const el of elements || []) {
    const t = el.tags || {};
    const lat = el.lat ?? el.center?.lat, lng = el.lon ?? el.center?.lon;
    if (!t.name || !Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    const key = `${t.name.toLowerCase()}|${lat.toFixed(4)}|${lng.toFixed(4)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const km = distanceKm(origin, { lat, lng });
    const bearing = bearingDeg(origin, { lat, lng });
    out.push({
      id: `osm:${el.type}/${el.id}`, name: t.name, type: t.amenity, cuisine: t.cuisine ? t.cuisine.replace(/;/g, ', ').replace(/_/g, ' ') : null,
      lat, lng, distanceKm: Math.round(km * 100) / 100, bearingDeg: bearing, direction: compass(bearing),
      address: address(t), openingHours: t.opening_hours || null, phone: t.phone || t['contact:phone'] || null,
      website: t.website || t['contact:website'] || null, vegetarian: t['diet:vegetarian'] === 'only' ? 'only' : t['diet:vegetarian'] === 'yes' ? 'yes' : null,
    });
  }
  return out.sort((a, b) => a.distanceKm - b.distanceKm).slice(0, MAX_RESULTS);
}

class UpstreamError extends Error {
  constructor(detail) { super(detail); this.detail = detail; }
}
const hostOf = (u) => { try { return new URL(u).host; } catch { return u; } };
const num = (name, dflt) => (process.env[name] !== undefined ? Number(process.env[name]) : dflt);

function describe(e) {
  if (e?.name === 'AbortError' || e?.name === 'TimeoutError' || /timeout/i.test(e?.message || '')) return 'timeout';
  return e?.cause?.code || e?.message || String(e);
}

async function attempt(url, query, fetchImpl, signal) {
  const res = await fetchImpl(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': USER_AGENT },
    body: 'data=' + encodeURIComponent(query),
    signal,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  // Overpass reports timeouts / out-of-memory as HTTP 200 with an empty list and a "remark".
  // That is a failure, not "no restaurants here": never accept (or cache) it.
  if (json.remark && /error|timed out|out of memory|too busy/i.test(json.remark) && !(json.elements || []).length) {
    throw new Error(String(json.remark).replace(/^runtime error:\s*/i, '').slice(0, 80));
  }
  return json;
}

/**
 * Hedged requests: ask the first mirror; if it hasn't answered within HEDGE_MS (or it fails straight away)
 * ask the next one too, and so on. The first good answer wins and the rest are cancelled.
 * Worst case is roughly HEDGE_MS * (mirrors - 1) + PER_MIRROR_MS instead of mirrors * PER_MIRROR_MS.
 */
function queryOverpass(query, fetchImpl) {
  const hedgeMs = num('OVERPASS_HEDGE_MS', 4000);
  const perMirrorMs = num('OVERPASS_TIMEOUT_MS', 25_000);
  return new Promise((resolve, reject) => {
    if (!ENDPOINTS.length) return reject(new UpstreamError('no Overpass endpoint configured'));
    const errors = [];
    const controllers = [];
    const timers = [];
    let started = 0, failed = 0, settled = false;

    const finish = (fn, value) => {
      if (settled) return;
      settled = true;
      timers.forEach(clearTimeout);
      controllers.forEach((c) => c.abort());
      fn(value);
    };

    const startNext = () => {
      if (settled || started >= ENDPOINTS.length) return;
      const url = ENDPOINTS[started++];
      const ctl = new AbortController();
      controllers.push(ctl);
      const killer = setTimeout(() => ctl.abort(new Error('timeout')), perMirrorMs);
      timers.push(killer);
      attempt(url, query, fetchImpl, ctl.signal).then(
        (json) => finish(resolve, json),
        (e) => {
          if (settled) return;
          clearTimeout(killer);
          errors.push(`${hostOf(url)}: ${describe(e)}`);
          if (++failed === ENDPOINTS.length) finish(reject, new UpstreamError(errors.join('; ')));
          else startNext(); // don't wait for the hedge timer when a mirror has already failed
        },
      );
      if (started < ENDPOINTS.length) timers.push(setTimeout(startNext, hedgeMs));
    };
    startNext();
  });
}

class BadRequest extends Error {}

function parseParams({ lat, lng, radius, types }) {
  const la = Number(lat), ln = Number(lng);
  if (!Number.isFinite(la) || !Number.isFinite(ln) || Math.abs(la) > 90 || Math.abs(ln) > 180 || lat === undefined || lng === undefined || lat === '' || lng === '') {
    throw new BadRequest('lat and lng are required');
  }
  const r = Math.min(5000, Math.max(200, Math.round(Number(radius) || 1500)));
  const wanted = String(types || 'restaurant,fast_food,cafe').split(',').map((s) => s.trim()).filter((s) => TYPES[s]);
  if (!wanted.length) throw new BadRequest('types must include restaurant, fast_food or cafe');
  return { lat: la, lng: ln, radiusM: r, types: wanted };
}

async function nearby(params, { fetchImpl = globalThis.fetch, now = Date.now } = {}) {
  const { lat, lng, radiusM, types } = parseParams(params);
  // Round to ~110 m so people standing in roughly the same place share a cached result.
  const key = `${lat.toFixed(3)},${lng.toFixed(3)}|${radiusM}|${types.sort().join()}`;
  const hit = cache.get(key);
  let places;
  let stale = false;
  if (hit && now() - hit.at < CACHE_MS) places = hit.elements;
  else {
    if (!inflight.has(key)) {
      inflight.set(key, queryOverpass(buildQuery(lat, lng, radiusM, types), fetchImpl)
        .then((j) => { cache.set(key, { at: now(), elements: j.elements || [] }); if (cache.size > 200) cache.delete(cache.keys().next().value); return j.elements || []; })
        .finally(() => inflight.delete(key)));
    }
    try {
      places = await inflight.get(key);
    } catch (e) {
      // Every mirror failed: better to show a slightly old list than nothing.
      if (hit && now() - hit.at < STALE_OK_MS) { places = hit.elements; stale = true; } else throw e;
    }
  }
  const origin = { lat, lng };
  return { origin, radiusM, source: 'OpenStreetMap', stale, places: normalise(places, origin) };
}

module.exports = { nearby, normalise, bearingDeg, compass, buildQuery, BadRequest, UpstreamError, _cache: cache };
