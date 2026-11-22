const { distanceKm } = require('./geo');
const { restaurants, PLATFORM_IDS } = require('./data');
const { provider } = require('./providers');

const DEFAULT_LOCATION = { lat: 12.9716, lng: 77.5946 }; // MG Road, Bengaluru

function parseLocation(lat, lng) {
  const la = Number(lat), ln = Number(lng);
  if (Number.isFinite(la) && Number.isFinite(ln) && Math.abs(la) <= 90 && Math.abs(ln) <= 180) {
    return { lat: la, lng: ln };
  }
  return DEFAULT_LOCATION;
}

function publicRestaurant(r, location) {
  return {
    id: r.id, name: r.name, cuisine: r.cuisine, rating: r.rating, area: r.area,
    platforms: r.platforms,
    distanceKm: Math.round(distanceKm(location, r) * 10) / 10,
  };
}

function searchRestaurants(q, location) {
  const needle = (q || '').trim().toLowerCase();
  return restaurants
    .filter((r) => !needle || `${r.name} ${r.cuisine} ${r.area}`.toLowerCase().includes(needle))
    .map((r) => publicRestaurant(r, location))
    .sort((a, b) => a.distanceKm - b.distanceKm);
}

function getMenu(id) {
  const r = restaurants.find((x) => x.id === id);
  if (!r) return null;
  return r.menu.map(({ id, name, price, veg }) => ({ id, name, basePrice: price, veg }));
}

class BadRequest extends Error {}

async function compare({ restaurantId, items, location }) {
  const restaurant = restaurants.find((r) => r.id === restaurantId);
  if (!restaurant) throw new BadRequest('Unknown restaurant');
  if (!Array.isArray(items) || items.length === 0) throw new BadRequest('Select at least one item');

  const lines = items.map((sel) => {
    const item = restaurant.menu.find((m) => m.id === sel.menuItemId);
    const qty = Math.floor(Number(sel.qty));
    if (!item) throw new BadRequest(`Unknown menu item ${sel.menuItemId}`);
    if (!(qty >= 1 && qty <= 20)) throw new BadRequest('Quantity must be 1-20');
    return { item, qty };
  });

  const quotes = await Promise.all(
    PLATFORM_IDS.map((p) =>
      provider.quote(p, { restaurant, lines, location }).catch((e) => ({
        platform: p, name: p, available: false, reason: 'Provider error: ' + e.message,
      }))
    )
  );

  const live = quotes.filter((q) => q.available);
  const cheapest = live.slice().sort((a, b) => a.total - b.total || a.etaMinutes - b.etaMinutes)[0];
  const fastest = live.slice().sort((a, b) => a.etaMinutes - b.etaMinutes || a.total - b.total)[0];
  // "Best value": cheapest unless a much faster option costs <= Rs.20 more.
  let bestValue = cheapest;
  if (cheapest && fastest && fastest.platform !== cheapest.platform) {
    if (fastest.total - cheapest.total <= 20 && cheapest.etaMinutes - fastest.etaMinutes >= 8) bestValue = fastest;
  }

  const maxTotal = live.length ? Math.max(...live.map((q) => q.total)) : 0;
  const results = quotes
    .map((q) => ({
      ...q,
      isCheapest: !!cheapest && q.platform === cheapest.platform,
      isFastest: !!fastest && q.platform === fastest.platform,
      isBestValue: !!bestValue && q.platform === bestValue.platform,
      savingsVsWorst: q.available ? maxTotal - q.total : 0,
    }))
    .sort((a, b) => (b.available - a.available) || (a.total || 0) - (b.total || 0));

  return {
    restaurant: publicRestaurant(restaurant, location),
    results,
    bestPlatform: bestValue ? bestValue.platform : null,
    cheapestPlatform: cheapest ? cheapest.platform : null,
    fastestPlatform: fastest ? fastest.platform : null,
    maxSavings: cheapest ? maxTotal - cheapest.total : 0,
  };
}

module.exports = { compare, searchRestaurants, getMenu, parseLocation, BadRequest, DEFAULT_LOCATION };
