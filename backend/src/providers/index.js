// Provider registry. Each provider exposes: async quote(platform, ctx) => Quote.
//
// Swiggy and Zomato do NOT offer public consumer price/menu APIs, and scraping their apps
// violates their terms, so they stay simulated. Real prices come from ONDC (open network):
// set the ONDC_* variables (see README) and PROVIDER_MODE=hybrid.
const simulated = require('./simulated');
const { fromEnv } = require('../ondc/config');
const { OndcClient } = require('../ondc/client');
const { makeProvider } = require('./ondc');

const requested = process.env.PROVIDER_MODE || 'simulated';
if (!['simulated', 'hybrid'].includes(requested)) throw new Error(`Unknown PROVIDER_MODE "${requested}"`);

let ondcClient = null;
if (requested === 'hybrid') {
  const cfg = fromEnv();
  if (cfg) ondcClient = new OndcClient(cfg);
  else console.warn('PROVIDER_MODE=hybrid but ONDC_SUBSCRIBER_ID / ONDC_UK_ID / ONDC_SIGNING_PRIVATE_KEY are not set; using simulated prices.');
}

const provider = ondcClient ? makeProvider(ondcClient) : simulated;
const mode = ondcClient ? 'hybrid' : 'simulated';

module.exports = { provider, mode, ondcClient, PROFILES: simulated.PROFILES };
