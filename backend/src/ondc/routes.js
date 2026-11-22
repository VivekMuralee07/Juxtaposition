const express = require('express');
const { decryptChallenge, signText, edPrivateKey } = require('./crypto');

const ACK = { message: { ack: { status: 'ACK' } } };
const nack = (message) => ({ message: { ack: { status: 'NACK' } }, error: { type: 'CONTEXT-ERROR', code: '20001', message } });

// Public endpoints the ONDC network calls on a buyer app. Mount BEFORE the rate limiter.
function createOndcRouter(client) {
  const r = express.Router();
  const cfg = client.cfg;

  // Registry onboarding step 1: site verification (signed request_id in a meta tag).
  r.get('/ondc-site-verification.html', (_req, res) => {
    if (!cfg.requestId) return res.status(404).send('ONDC_REQUEST_ID not set');
    const sig = signText(cfg.requestId, edPrivateKey(cfg.signingKey));
    res.type('html').send(`<!DOCTYPE html><html><head><meta name="ondc-site-verification" content="${sig}" /></head><body>ONDC site verification</body></html>`);
  });

  // Registry onboarding step 2: answer the encrypted challenge.
  r.post('/on_subscribe', (req, res) => {
    try {
      if (!cfg.encryptionKey || !cfg.registryEncPublicKey) throw new Error('encryption keys not configured');
      res.json({ answer: decryptChallenge(req.body.challenge, cfg.encryptionKey, cfg.registryEncPublicKey) });
    } catch (e) {
      res.status(400).json({ error: { message: e.message } });
    }
  });

  for (const [path, kind] of [['/on_search', 'search'], ['/on_select', 'select']]) {
    r.post(path, async (req, res) => {
      const txn = req.body?.context?.transaction_id;
      // Cheap check first: ignore callbacks for transactions we never started (no registry lookups for spam).
      if (!client.expects(kind, txn)) return res.json(nack('Unknown or expired transaction'));
      if (!(await client.verifyIncoming(req.get('Authorization'), req.rawBody || Buffer.from(JSON.stringify(req.body))))) {
        return res.status(401).json(nack('Signature verification failed'));
      }
      client.accept(kind, req.body);
      res.json(ACK);
    });
  }
  return r;
}

module.exports = { createOndcRouter };
