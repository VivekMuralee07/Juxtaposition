// Beckn/ONDC buyer-app client: signed /search and /select requests, plus collection of the
// asynchronous /on_search and /on_select callbacks that arrive on our public endpoints.
const crypto = require('crypto');
const { edPrivateKey, edPublicKey, signHeader, parseAuthHeader, verifyAuth } = require('./crypto');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class OndcClient {
  constructor(cfg, { fetchImpl = globalThis.fetch } = {}) {
    this.cfg = cfg;
    this.fetch = fetchImpl;
    this.privateKey = edPrivateKey(cfg.signingKey);
    this.pending = new Map(); // transaction_id -> { kind, bodies, notify }
    this.keyCache = new Map(); // "subscriber|ukId" -> { key, exp }
  }

  context(action, city, extra = {}) {
    return {
      domain: this.cfg.domain, country: 'IND', city, action, core_version: this.cfg.coreVersion,
      bap_id: this.cfg.subscriberId, bap_uri: this.cfg.bapUri,
      message_id: crypto.randomUUID(), timestamp: new Date().toISOString(), ttl: 'PT30S', ...extra,
    };
  }

  async post(url, payload) {
    const body = JSON.stringify(payload);
    const res = await this.fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: signHeader({ body, subscriberId: this.cfg.subscriberId, ukId: this.cfg.ukId, privateKey: this.privateKey }),
      },
      body,
      signal: AbortSignal.timeout(10_000),
    });
    const text = await res.text();
    let json = {};
    try { json = JSON.parse(text); } catch { /* non-JSON error page */ }
    if (!res.ok || json?.message?.ack?.status === 'NACK') {
      throw new Error(`ONDC ${url} rejected: ${json?.error?.message || res.status}`);
    }
  }

  // Wait for callbacks on a transaction. Resolves after `maxMs`, or `quietMs` after the last
  // arrival (once at least `min` have arrived).
  _collect(txn, { maxMs, quietMs, min = 1 }) {
    return new Promise((resolve) => {
      const p = this.pending.get(txn);
      let quiet = null;
      const done = () => { clearTimeout(hard); clearTimeout(quiet); this.pending.delete(txn); resolve(p.bodies); };
      const hard = setTimeout(done, maxMs);
      p.notify = () => {
        clearTimeout(quiet);
        if (p.bodies.length >= min) quiet = setTimeout(done, quietMs);
      };
    });
  }

  async search({ cityCode, gps, areaCode, providerName }) {
    const txn = crypto.randomUUID();
    this.pending.set(txn, { kind: 'search', bodies: [], notify: () => {} });
    const payload = {
      context: this.context('search', cityCode, { transaction_id: txn }),
      message: {
        intent: {
          provider: { descriptor: { name: providerName } },
          fulfillment: { type: 'Delivery', end: { location: { gps, address: { area_code: areaCode } } } },
          payment: {
            '@ondc/org/buyer_app_finder_fee_type': 'percent',
            '@ondc/org/buyer_app_finder_fee_amount': this.cfg.finderFeePct,
          },
        },
      },
    };
    try { await this.post(`${this.cfg.gatewayUrl}/search`, payload); } catch (e) { this.pending.delete(txn); throw e; }
    const bodies = await this._collect(txn, { maxMs: this.cfg.searchWaitMs, quietMs: 1500 });
    return bodies.flatMap((b) => {
      const { bpp_id: bppId, bpp_uri: bppUri } = b.context || {};
      return (b.message?.catalog?.['bpp/providers'] || []).map((provider) => ({ bppId, bppUri, provider }));
    });
  }

  async select({ cityCode, bppId, bppUri, providerId, locationId, items, gps, areaCode }) {
    const txn = crypto.randomUUID();
    this.pending.set(txn, { kind: 'select', bodies: [], notify: () => {} });
    const payload = {
      context: this.context('select', cityCode, { transaction_id: txn, bpp_id: bppId, bpp_uri: bppUri }),
      message: {
        order: {
          provider: { id: providerId, locations: [{ id: locationId }] },
          items: items.map((i) => ({ id: i.id, location_id: locationId, quantity: { count: i.qty } })),
          fulfillments: [{ end: { location: { gps, address: { area_code: areaCode } } } }],
        },
      },
    };
    try { await this.post(`${bppUri.replace(/\/$/, '')}/select`, payload); } catch (e) { this.pending.delete(txn); throw e; }
    const bodies = await this._collect(txn, { maxMs: this.cfg.selectWaitMs, quietMs: 0 });
    return bodies[0] || null;
  }

  // ---- incoming callbacks ----
  expects(kind, txn) {
    const p = this.pending.get(txn);
    return !!p && p.kind === kind;
  }
  accept(kind, body) {
    const p = this.pending.get(body?.context?.transaction_id);
    if (!p || p.kind !== kind) return false;
    p.bodies.push(body);
    p.notify();
    return true;
  }

  async lookupKey(subscriberId, ukId) {
    const k = `${subscriberId}|${ukId}`;
    const hit = this.keyCache.get(k);
    if (hit && hit.exp > Date.now()) return hit.key;
    const res = await this.fetch(this.cfg.registryUrl, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subscriber_id: subscriberId, ukId, country: 'IND', domain: this.cfg.domain }),
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) throw new Error(`registry lookup failed (${res.status})`);
    const entries = await res.json();
    const entry = (Array.isArray(entries) ? entries : []).find((e) => e.unique_key_id === ukId && e.signing_public_key);
    if (!entry) throw new Error('subscriber key not found in registry');
    const key = edPublicKey(entry.signing_public_key);
    this.keyCache.set(k, { key, exp: Date.now() + 10 * 60_000 });
    return key;
  }

  async verifyIncoming(authHeader, rawBody) {
    if (!this.cfg.verifyCallbacks) return true;
    const p = parseAuthHeader(authHeader);
    if (!p) return false;
    try {
      return verifyAuth({ header: authHeader, body: rawBody, publicKey: await this.lookupKey(p.subscriberId, p.ukId) });
    } catch {
      return false;
    }
  }
}

module.exports = { OndcClient, sleep };
