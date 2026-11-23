const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const app = require('../src/app');

const listen = () => new Promise((res) => {
  const s = http.createServer(app).listen(0, '127.0.0.1', () => res({ s, url: `http://127.0.0.1:${s.address().port}` }));
});

test('health reports ok and the deployed commit', async () => {
  process.env.RENDER_GIT_COMMIT = ''; // module reads it at load; just check the shape
  const { s, url } = await listen();
  try {
    const body = await (await fetch(`${url}/health`)).json();
    assert.strictEqual(body.ok, true);
    assert.ok('commit' in body);
  } finally { s.close(); }
});

test('nearby validates its input before touching the network', async () => {
  const { s, url } = await listen();
  try {
    for (const q of ['', '?lat=abc&lng=1', '?lat=12.9&lng=77.6&types=bank']) {
      const res = await fetch(`${url}/api/nearby${q}`);
      assert.strictEqual(res.status, 400, q);
      assert.ok((await res.json()).error);
    }
  } finally { s.close(); }
});

test('removed price-comparison routes are gone', async () => {
  const { s, url } = await listen();
  try {
    for (const p of ['/api/restaurants', '/api/compare', '/on_search']) {
      assert.strictEqual((await fetch(`${url}${p}`)).status, 404, p);
    }
  } finally { s.close(); }
});
