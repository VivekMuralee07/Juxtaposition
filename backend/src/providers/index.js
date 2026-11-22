// Provider registry. Each provider exposes: async quote(platform, ctx) => Quote.
//
// Swiggy and Zomato do NOT offer public consumer price/menu APIs, and scraping their
// apps violates their terms. The legitimate routes to live data are:
//   * ONDC (Open Network for Digital Commerce) - open protocol, real prices from many apps
//   * Partner/affiliate APIs you have a contract for
// Implement a module with the same `quote` signature and register it below.
const simulated = require('./simulated');

const mode = process.env.PROVIDER_MODE || 'simulated';

const providers = {
  simulated,
  // live: require('./live'),   // <- your ONDC / partner adapter
};

const provider = providers[mode];
if (!provider) throw new Error(`Unknown PROVIDER_MODE "${mode}"`);

module.exports = { provider, mode, PROFILES: simulated.PROFILES };
