const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const express = require('express');
const C = require('../src/ondc/crypto');
const { OndcClient } = require('../src/ondc/client');
const { createOndcRouter } = require('../src/ondc/routes');
const { makeProvider } = require('../src/providers/ondc');
const { fromEnv } = require('../src/ondc/config');
const { restaurantScore, itemScore, durationMinutes } = require('../src/ondc/match');
const { restaurants } = require('../src/data');
const { DEFAULT_LOCATION } = require('../src/compare');

const listen = (handler) => new Promise((res) => {
  const s = http.createServer(handler).listen(0, '127.0.0.1', () => res({ s, url: `http://127.0.0.1:${s.address().port}` }));
});
const readBody = (req) => new Promise((res) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => res(Buffer.concat(c))); });

test('crypto: sign/verify roundtrip, tamper detection, challenge decrypt, key formats', () => {
  const k = C.generateKeys();
  const body = JSON.stringify({ a: 1 });
  const header = C.signHeader({ body, subscriberId: 'x.test', ukId: 'u1', privateKey: C.edPrivateKey(k.signing_private_key) });
  const pub = C.edPublicKey(k.signing_public_key);
  assert.ok(C.verifyAuth({ header, body, publicKey: pub }));
  assert.ok(!C.verifyAuth({ header, body: body + ' ', publicKey: pub }));
  assert.ok(!C.verifyAuth({ header, body, publicKey: pub, now: Date.now() + 3600_000 })); // expired
  assert.ok(!C.verifyAuth({ header, body, publicKey: C.edPublicKey(C.generateKeys().signing_public_key) }));
  // 32-byte seed form works too
  assert.ok(C.edPrivateKey(Buffer.from(k.signing_private_key, 'base64').subarray(0, 32).toString('base64')));
  // registry challenge
  const reg = C.generateKeys();
  const enc = C.encryptChallenge('hello-challenge', reg.encryption_private_key, k.encryption_public_key);
  assert.strictEqual(C.decryptChallenge(enc, k.encryption_private_key, reg.encryption_public_key), 'hello-challenge');
});

test('match helpers', () => {
  assert.ok(restaurantScore("Domino's Pizza", 'Dominos Pizza - Koramangala') >= 0.75);
  assert.ok(restaurantScore("Domino's Pizza", 'Meghana Foods') < 0.75);
  assert.ok(itemScore('Margherita (Medium)', 'Margherita Pizza Medium') >= 0.66);
  assert.ok(itemScore('Margherita (Medium)', 'Garlic Bread') < 0.66);
  assert.strictEqual(durationMinutes('PT45M'), 45);
  assert.strictEqual(durationMinutes('PT1H15M'), 75);
  assert.strictEqual(durationMinutes('nonsense'), null);
});

test('config: null unless configured', () => {
  assert.strictEqual(fromEnv({}), null);
  const c = fromEnv({ ONDC_SUBSCRIBER_ID: 'a.b', ONDC_UK_ID: 'k', ONDC_SIGNING_PRIVATE_KEY: 'x', ONDC_ENV: 'preprod' });
  assert.strictEqual(c.bapUri, 'https://a.b');
  assert.match(c.gatewayUrl, /preprod/);
});

test('end-to-end: signed search -> on_search -> select -> on_select -> quote; fallback on failure', async () => {
  const bap = C.generateKeys(), bpp = C.generateKeys();
  const bapPriv = C.edPrivateKey(bap.signing_private_key), bppPriv = C.edPrivateKey(bpp.signing_private_key);
  const seen = { search: 0, select: 0, badCallbackRejected: false };
  const state = { bapUri: null, failSelect: false };

  const postSigned = async (url, payload, subscriberId) => {
    const body = JSON.stringify(payload);
    return fetch(url, { method: 'POST', body, headers: {
      'Content-Type': 'application/json',
      Authorization: C.signHeader({ body, subscriberId, ukId: 'bppkey', privateKey: bppPriv }),
    } });
  };

  const registry = await listen(async (req, res) => {
    await readBody(req);
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify([{ subscriber_id: 'bpp.test', unique_key_id: 'bppkey', signing_public_key: bpp.signing_public_key }]));
  });

  const bppServer = await listen(async (req, res) => {
    const raw = await readBody(req);
    assert.ok(C.verifyAuth({ header: req.headers.authorization, body: raw, publicKey: C.edPublicKey(bap.signing_public_key) }), 'seller verifies BAP signature');
    const msg = JSON.parse(raw);
    seen.select++;
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({ message: { ack: { status: 'ACK' } } }));
    if (state.failSelect) return;
    const ctx = { ...msg.context, action: 'on_select', bpp_id: 'bpp.test', bpp_uri: bppServer.url };
    assert.strictEqual(msg.message.order.items[0].id, 'item-marg');
    assert.strictEqual(msg.message.order.items[0].quantity.count, 2);
    setTimeout(() => postSigned(`${state.bapUri}/on_select`, {
      context: ctx,
      message: { order: {
        fulfillments: [{ '@ondc/org/TAT': 'PT38M' }],
        quote: {
          price: { currency: 'INR', value: '598.00' },
          breakup: [
            { title: 'Margherita Pizza Medium', '@ondc/org/item_id': 'item-marg', '@ondc/org/item_quantity': { count: 2 }, '@ondc/org/title_type': 'item', price: { value: '480.00' } },
            { title: 'Delivery charges', '@ondc/org/title_type': 'delivery', price: { value: '40.00' } },
            { title: 'Packing charges', '@ondc/org/title_type': 'packing', price: { value: '20.00' } },
            { title: 'Taxes', '@ondc/org/title_type': 'tax', price: { value: '58.00' } },
          ],
        },
      } },
    }, 'bpp.test'), 20);
  });

  const gateway = await listen(async (req, res) => {
    const raw = await readBody(req);
    assert.ok(C.verifyAuth({ header: req.headers.authorization, body: raw, publicKey: C.edPublicKey(bap.signing_public_key) }), 'gateway verifies BAP signature');
    const msg = JSON.parse(raw);
    seen.search++;
    assert.strictEqual(msg.context.action, 'search');
    assert.strictEqual(msg.context.city, 'std:080');
    assert.strictEqual(msg.message.intent.provider.descriptor.name, "Domino's Pizza");
    res.setHeader('content-type', 'application/json');
    res.end(JSON.stringify({ message: { ack: { status: 'ACK' } } }));
    setTimeout(async () => {
      // 1) forged callback (signed by wrong key) must be rejected
      const forged = await fetch(`${state.bapUri}/on_search`, { method: 'POST', body: JSON.stringify({ context: msg.context }), headers: {
        'Content-Type': 'application/json',
        Authorization: C.signHeader({ body: JSON.stringify({ context: msg.context }), subscriberId: 'bpp.test', ukId: 'bppkey', privateKey: C.edPrivateKey(C.generateKeys().signing_private_key) }),
      } });
      seen.badCallbackRejected = forged.status === 401;
      // 2) genuine catalog
      await postSigned(`${state.bapUri}/on_search`, {
        context: { ...msg.context, action: 'on_search', bpp_id: 'bpp.test', bpp_uri: bppServer.url },
        message: { catalog: { 'bpp/providers': [
          { id: 'prov-far', descriptor: { name: "Domino's Pizza - Far Outlet" }, locations: [{ id: 'loc-far', gps: '12.5000,77.0000' }], items: [] },
          { id: 'prov-1', descriptor: { name: "Domino's Pizza - Koramangala" }, locations: [{ id: 'loc-1', gps: '12.9281,77.6270' }],
            items: [
              { id: 'item-marg', location_id: 'loc-1', descriptor: { name: 'Margherita Pizza Medium' }, price: { value: '250' } },
              { id: 'item-bread', location_id: 'loc-1', descriptor: { name: 'Garlic Breadsticks' }, price: { value: '125' } },
            ] },
          { id: 'prov-x', descriptor: { name: 'Meghana Foods' }, locations: [{ id: 'l', gps: '12.93,77.62' }], items: [] },
        ] } },
      }, 'bpp.test');
    }, 20);
  });

  const cfg = {
    subscriberId: 'bap.test', ukId: 'bapkey', signingKey: bap.signing_private_key, bapUri: null,
    gatewayUrl: gateway.url, registryUrl: registry.url, domain: 'ONDC:RET11', coreVersion: '1.2.0', finderFeePct: '3',
    searchWaitMs: 3000, selectWaitMs: 3000, verifyCallbacks: true,
  };
  const client = new OndcClient(cfg);
  const app = express();
  app.use(express.json({ limit: '8mb', verify: (req, _r, buf) => { req.rawBody = buf; } }));
  app.use(createOndcRouter(client));
  const bapServer = await listen(app);
  cfg.bapUri = state.bapUri = bapServer.url;

  const provider = makeProvider(client);
  const dominos = restaurants.find((r) => r.id === 'dominos');
  const ctx = { restaurant: dominos, lines: [{ item: dominos.menu[0], qty: 2 }], location: DEFAULT_LOCATION };

  try {
    const q = await provider.quote('ondc', ctx);
    assert.ok(seen.badCallbackRejected, 'forged callback rejected with 401');
    assert.strictEqual(q.available, true);
    assert.strictEqual(q.source, 'ondc');
    assert.strictEqual(q.total, 598);
    assert.strictEqual(q.itemsTotal, 480);
    assert.strictEqual(q.deliveryFee, 40);
    assert.strictEqual(q.taxes, 58);
    assert.strictEqual(q.platformFee, 20); // packing
    assert.strictEqual(q.etaMinutes, 38);
    assert.strictEqual(q.total, q.itemsTotal + q.deliveryFee + q.platformFee + q.taxes - q.discount);
    assert.strictEqual(q.items[0].unitPrice, 240);

    // Other platforms stay simulated
    assert.strictEqual((await provider.quote('swiggy', ctx)).source, 'simulated');

    // Item not on ONDC -> real "unavailable" (no select sent)
    const before = seen.select;
    const missing = await provider.quote('ondc', { ...ctx, lines: [{ item: dominos.menu[2], qty: 1 }] });
    assert.strictEqual(missing.available, false);
    assert.strictEqual(missing.source, 'ondc');
    assert.strictEqual(seen.select, before);

    // Seller never answers select -> falls back to simulated estimate
    state.failSelect = true;
    client.cfg.selectWaitMs = 300;
    const fb = await provider.quote('ondc', ctx);
    assert.strictEqual(fb.source, 'simulated');
  } finally {
    for (const x of [registry, bppServer, gateway, bapServer]) x.s.close();
  }
});

test('on_subscribe + site verification endpoints', async () => {
  const bap = C.generateKeys(), reg = C.generateKeys();
  const client = new OndcClient({
    subscriberId: 'bap.test', ukId: 'k', signingKey: bap.signing_private_key, encryptionKey: bap.encryption_private_key,
    registryEncPublicKey: reg.encryption_public_key, requestId: 'req-123', verifyCallbacks: true,
  });
  const app = express(); app.use(express.json()); app.use(createOndcRouter(client));
  const srv = await listen(app);
  try {
    const challenge = C.encryptChallenge('plain-answer', reg.encryption_private_key, bap.encryption_public_key);
    const r = await fetch(`${srv.url}/on_subscribe`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ subscriber_id: 'bap.test', challenge }) });
    assert.deepStrictEqual(await r.json(), { answer: 'plain-answer' });
    const html = await (await fetch(`${srv.url}/ondc-site-verification.html`)).text();
    const sig = /content="([^"]+)"/.exec(html)[1];
    const { verify } = require('node:crypto');
    assert.ok(verify(null, Buffer.from('req-123'), C.edPublicKey(bap.signing_public_key), Buffer.from(sig, 'base64')));
  } finally { srv.s.close(); }
});
