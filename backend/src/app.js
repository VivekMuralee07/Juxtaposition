const express = require('express');
const { compare, searchRestaurants, getMenu, parseLocation, BadRequest } = require('./compare');
const { mode, ondcClient } = require('./providers');
const nearbyApi = require('./nearby');
const { createOndcRouter } = require('./ondc/routes');

const app = express();
app.disable('x-powered-by');
// ONDC catalogues can be large, and callback signatures are computed over the raw bytes.
app.use(express.json({ limit: '8mb', verify: (req, _res, buf) => { req.rawBody = buf; } }));
// ONDC network callbacks: public, signature-verified, so mounted before the per-IP rate limiter.
if (ondcClient) app.use(createOndcRouter(ondcClient));

// Tiny in-memory rate limiter (per IP, 60 req/min) - enough for a single Render instance.
const hits = new Map();
app.use((req, res, next) => {
  const now = Date.now();
  const key = req.ip;
  const rec = hits.get(key) || { n: 0, reset: now + 60000 };
  if (now > rec.reset) { rec.n = 0; rec.reset = now + 60000; }
  rec.n += 1;
  hits.set(key, rec);
  if (rec.n > 60) return res.status(429).json({ error: 'Too many requests' });
  next();
});
setInterval(() => { const n = Date.now(); for (const [k, v] of hits) if (n > v.reset) hits.delete(k); }, 60000).unref();

app.get('/health', (_req, res) => res.json({ ok: true, mode }));

app.get('/api/nearby', async (req, res, next) => {
  try {
    res.json(await nearbyApi.nearby(req.query));
  } catch (e) {
    if (e instanceof nearbyApi.BadRequest) return res.status(400).json({ error: e.message });
    console.error('nearby failed:', e.message);
    const why = e instanceof nearbyApi.UpstreamError ? ` (${e.detail})` : '';
    res.status(502).json({ error: `Restaurant data source is busy${why}. Please try again in a moment.` });
  }
});

app.get('/api/restaurants', (req, res) => {
  const location = parseLocation(req.query.lat, req.query.lng);
  res.json({
    mode,
    locationNote: location.snapped ? 'No restaurants near you yet - showing the Bengaluru demo catalogue' : null,
    restaurants: searchRestaurants(req.query.q, location),
  });
});

app.get('/api/restaurants/:id/menu', (req, res) => {
  const menu = getMenu(req.params.id);
  if (!menu) return res.status(404).json({ error: 'Restaurant not found' });
  res.json({ menu });
});

app.post('/api/compare', async (req, res, next) => {
  try {
    const { restaurantId, items, lat, lng } = req.body || {};
    const out = await compare({ restaurantId, items, location: parseLocation(lat, lng) });
    res.json({ mode, ...out });
  } catch (e) { next(e); }
});

app.use((err, _req, res, _next) => {
  if (err instanceof BadRequest) return res.status(400).json({ error: err.message });
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'Invalid JSON' });
  console.error(err);
  res.status(500).json({ error: 'Internal error' });
});

module.exports = app;
