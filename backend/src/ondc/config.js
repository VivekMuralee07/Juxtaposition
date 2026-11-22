// ONDC buyer-app (BAP) configuration from environment variables.
// Returns null when not configured, so the server runs in simulated mode.
//
// Endpoint defaults per ONDC_ENV are ONDC's published participant endpoints at the time of
// writing - confirm them against the current ONDC participant docs and override via
// ONDC_GATEWAY_URL / ONDC_REGISTRY_URL if they differ.
const ENDPOINTS = {
  staging: { gateway: 'https://staging.gateway.proteantech.in', registry: 'https://staging.registry.ondc.org/v2.0/lookup' },
  preprod: { gateway: 'https://preprod.gateway.ondc.org', registry: 'https://preprod.registry.ondc.org/ondc/lookup' },
  prod: { gateway: 'https://prod.gateway.ondc.org', registry: 'https://prod.registry.ondc.org/v2.0/lookup' },
};

function fromEnv(env = process.env) {
  const { ONDC_SUBSCRIBER_ID: subscriberId, ONDC_UK_ID: ukId, ONDC_SIGNING_PRIVATE_KEY: signingKey } = env;
  if (!subscriberId || !ukId || !signingKey) return null;
  const e = ENDPOINTS[env.ONDC_ENV || 'staging'];
  if (!e) throw new Error(`ONDC_ENV must be one of ${Object.keys(ENDPOINTS).join(', ')}`);
  return {
    subscriberId, ukId, signingKey,
    bapUri: (env.ONDC_BAP_URI || `https://${subscriberId}`).replace(/\/$/, ''),
    encryptionKey: env.ONDC_ENCRYPTION_PRIVATE_KEY || null,
    registryEncPublicKey: env.ONDC_REGISTRY_ENC_PUBLIC_KEY || null,
    requestId: env.ONDC_REQUEST_ID || null,
    gatewayUrl: (env.ONDC_GATEWAY_URL || e.gateway).replace(/\/$/, ''),
    registryUrl: env.ONDC_REGISTRY_URL || e.registry,
    domain: env.ONDC_DOMAIN || 'ONDC:RET11',
    coreVersion: env.ONDC_CORE_VERSION || '1.2.0',
    finderFeePct: env.ONDC_FINDER_FEE_PCT || '3',
    searchWaitMs: Number(env.ONDC_SEARCH_WAIT_MS || 6000),
    selectWaitMs: Number(env.ONDC_SELECT_WAIT_MS || 8000),
    verifyCallbacks: env.ONDC_VERIFY_CALLBACKS !== 'false',
  };
}
module.exports = { fromEnv, ENDPOINTS };
