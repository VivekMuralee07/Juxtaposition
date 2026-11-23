const express = require('express');
const nearbyApi = require('./nearby');

const app = express();
app.disable('x-powered-by');

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

// RENDER_GIT_COMMIT is set by Render, so /health shows exactly which commit is live.
const commit = (process.env.RENDER_GIT_COMMIT || '').slice(0, 7) || null;
app.get('/health', (_req, res) => res.json({ ok: true, commit }));

// Open this in a browser to see whether the server can reach the OpenStreetMap servers at all.
app.get('/api/diag', async (_req, res) => {
  const mirrors = await nearbyApi.diagnose();
  res.json({ commit, mirrors, anyOk: mirrors.some((m) => m.ok) });
});

app.get('/api/nearby', async (req, res) => {
  try {
    res.json(await nearbyApi.nearby(req.query));
  } catch (e) {
    if (e instanceof nearbyApi.BadRequest) return res.status(400).json({ error: e.message });
    console.error('nearby failed:', e.message);
    const why = e instanceof nearbyApi.UpstreamError ? ` (${e.detail})` : '';
    res.status(502).json({ error: `Restaurant data source is busy${why}. Please try again in a moment.` });
  }
});

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: 'Internal error' });
});

module.exports = app;
