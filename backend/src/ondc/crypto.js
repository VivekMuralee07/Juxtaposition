// ONDC / Beckn cryptography: ed25519 request signing, BLAKE-512 digest,
// X25519 + AES-256-ECB challenge decryption (registry /on_subscribe).
const crypto = require('crypto');

const ED_PKCS8 = Buffer.from('302e020100300506032b657004220420', 'hex');
const ED_SPKI = Buffer.from('302a300506032b6570032100', 'hex');
const X_PKCS8 = Buffer.from('302e020100300506032b656e04220420', 'hex');
const X_SPKI = Buffer.from('302a300506032b656e032100', 'hex');

function edPrivateKey(b64) {
  const raw = Buffer.from(b64, 'base64');
  if (raw.length === 48) return crypto.createPrivateKey({ key: raw, format: 'der', type: 'pkcs8' });
  if (raw.length !== 32 && raw.length !== 64) throw new Error('ed25519 private key must be 32 or 64 bytes (base64)');
  return crypto.createPrivateKey({ key: Buffer.concat([ED_PKCS8, raw.subarray(0, 32)]), format: 'der', type: 'pkcs8' });
}
function edPublicKey(b64) {
  const raw = Buffer.from(b64, 'base64');
  if (raw.length === 44) return crypto.createPublicKey({ key: raw, format: 'der', type: 'spki' });
  if (raw.length !== 32) throw new Error('ed25519 public key must be 32 raw bytes or SPKI DER (base64)');
  return crypto.createPublicKey({ key: Buffer.concat([ED_SPKI, raw]), format: 'der', type: 'spki' });
}
function xPrivateKey(b64) {
  const raw = Buffer.from(b64, 'base64');
  if (raw.length === 48) return crypto.createPrivateKey({ key: raw, format: 'der', type: 'pkcs8' });
  if (raw.length !== 32) throw new Error('x25519 private key must be 32 raw bytes or PKCS8 DER (base64)');
  return crypto.createPrivateKey({ key: Buffer.concat([X_PKCS8, raw]), format: 'der', type: 'pkcs8' });
}
function xPublicKey(b64) {
  const raw = Buffer.from(b64, 'base64');
  if (raw.length === 44) return crypto.createPublicKey({ key: raw, format: 'der', type: 'spki' });
  if (raw.length !== 32) throw new Error('x25519 public key must be 32 raw bytes or SPKI DER (base64)');
  return crypto.createPublicKey({ key: Buffer.concat([X_SPKI, raw]), format: 'der', type: 'spki' });
}

const digestOf = (body) => 'BLAKE-512=' + crypto.createHash('blake2b512').update(body).digest('base64');

function signHeader({ body, subscriberId, ukId, privateKey, now = Date.now(), ttlSec = 300 }) {
  const created = Math.floor(now / 1000);
  const expires = created + ttlSec;
  const signingString = `(created): ${created}\n(expires): ${expires}\ndigest: ${digestOf(body)}`;
  const signature = crypto.sign(null, Buffer.from(signingString), privateKey).toString('base64');
  return (
    `Signing keyId="${subscriberId}|${ukId}|ed25519",algorithm="ed25519",created="${created}",` +
    `expires="${expires}",headers="(created) (expires) digest",signature="${signature}"`
  );
}

function parseAuthHeader(header) {
  if (!header || !/^Signing\s/i.test(header)) return null;
  const out = {};
  for (const m of header.replace(/^Signing\s+/i, '').matchAll(/(\w+)="([^"]*)"/g)) out[m[1]] = m[2];
  const [subscriberId, ukId] = (out.keyId || '').split('|');
  if (!subscriberId || !ukId || !out.signature) return null;
  return { ...out, subscriberId, ukId };
}

function verifyAuth({ header, body, publicKey, now = Date.now(), skewSec = 60 }) {
  const p = parseAuthHeader(header);
  if (!p) return false;
  const nowSec = Math.floor(now / 1000);
  if (Number(p.created) > nowSec + skewSec || nowSec > Number(p.expires) + skewSec) return false;
  const signingString = `(created): ${p.created}\n(expires): ${p.expires}\ndigest: ${digestOf(body)}`;
  try {
    return crypto.verify(null, Buffer.from(signingString), publicKey, Buffer.from(p.signature, 'base64'));
  } catch {
    return false;
  }
}

function sharedSecret(ownPrivateB64, otherPublicB64) {
  return crypto.diffieHellman({ privateKey: xPrivateKey(ownPrivateB64), publicKey: xPublicKey(otherPublicB64) });
}
function decryptChallenge(challengeB64, ownPrivateB64, registryPublicB64) {
  const d = crypto.createDecipheriv('aes-256-ecb', sharedSecret(ownPrivateB64, registryPublicB64), null);
  return Buffer.concat([d.update(Buffer.from(challengeB64, 'base64')), d.final()]).toString('utf8');
}
function encryptChallenge(plain, ownPrivateB64, otherPublicB64) { // used by tests / registry simulators
  const c = crypto.createCipheriv('aes-256-ecb', sharedSecret(ownPrivateB64, otherPublicB64), null);
  return Buffer.concat([c.update(plain, 'utf8'), c.final()]).toString('base64');
}
function signText(text, privateKey) {
  return crypto.sign(null, Buffer.from(text), privateKey).toString('base64');
}

// Fresh key material in the formats ONDC registration expects.
function generateKeys() {
  const ed = crypto.generateKeyPairSync('ed25519');
  const seed = ed.privateKey.export({ format: 'der', type: 'pkcs8' }).subarray(-32);
  const edPub = ed.publicKey.export({ format: 'der', type: 'spki' }).subarray(-32);
  const x = crypto.generateKeyPairSync('x25519');
  return {
    signing_private_key: Buffer.concat([seed, edPub]).toString('base64'),
    signing_public_key: edPub.toString('base64'),
    encryption_private_key: x.privateKey.export({ format: 'der', type: 'pkcs8' }).subarray(-32).toString('base64'),
    encryption_public_key: x.publicKey.export({ format: 'der', type: 'spki' }).toString('base64'),
  };
}

module.exports = {
  edPrivateKey, edPublicKey, xPrivateKey, xPublicKey, digestOf, signHeader, parseAuthHeader,
  verifyAuth, decryptChallenge, encryptChallenge, signText, generateKeys,
};
