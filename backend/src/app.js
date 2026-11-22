const express = require('express');
const { compare, searchRestaurants, getMenu, parseLocation, BadRequest } = require('./compare');
const { mode } = require('./providers');

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '20kb' }));

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

app.get('/api/restaurants', (req, res) => {
  res.json({ mode, restaurants: searchRestaurants(req.query.q, parseLocation(req.query.lat, req.query.lng)) });
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
