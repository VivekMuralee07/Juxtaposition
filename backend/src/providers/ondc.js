// Hybrid provider: REAL ONDC quotes for the "ondc" platform (search -> select -> on_select),
// simulated estimates for everything else (Swiggy/Zomato have no public price API).
// If ONDC errors or times out, falls back to the simulated ONDC estimate (tagged source:'simulated').
const { distanceKm } = require('../geo');
const { CITIES } = require('../data');
const { restaurantScore, itemScore, parseGps, durationMinutes } = require('../ondc/match');
const simulated = require('./simulated');

const PROFILE = simulated.PROFILES.ondc;
const SEARCH_CACHE_MS = 5 * 60_000;
const NAME_THRESHOLD = 0.75;
const ITEM_THRESHOLD = 0.66;
const MAX_SELLER_KM = 3; // seller outlet must be this close to our restaurant record

const unavailable = (reason) => ({
  platform: 'ondc', name: PROFILE.name, color: PROFILE.color, available: false, source: 'ondc', reason,
});

function nearestCity(loc) {
  return Object.entries(CITIES).sort((a, b) => distanceKm(loc, a[1]) - distanceKm(loc, b[1]))[0][1];
}

function makeProvider(client) {
  const cache = new Map(); // restaurantId|city -> { at, sellers }

  async function sellersFor(restaurant, city, location) {
    const key = `${restaurant.id}|${city.std}`;
    const hit = cache.get(key);
    if (hit && Date.now() - hit.at < SEARCH_CACHE_MS) return hit.sellers;
    const sellers = await client.search({
      cityCode: city.std, gps: `${location.lat},${location.lng}`, areaCode: city.pincode, providerName: restaurant.name,
    });
    cache.set(key, { at: Date.now(), sellers });
    return sellers;
  }

  // Best (nearest) seller outlet whose name matches our restaurant.
  function pickSeller(restaurant, sellers) {
    let best = null;
    for (const s of sellers) {
      if (restaurantScore(restaurant.name, s.provider?.descriptor?.name) < NAME_THRESHOLD) continue;
      for (const loc of s.provider.locations || [{}]) {
        const g = parseGps(loc.gps);
        const km = g ? distanceKm(g, restaurant) : 0;
        if (km > MAX_SELLER_KM) continue;
        if (!best || km < best.km) best = { ...s, location: loc, km };
      }
    }
    return best;
  }

  function matchItems(lines, seller) {
    const catalog = (seller.provider.items || []).filter(
      (i) => !seller.location.id || !i.location_id || i.location_id === seller.location.id
    );
    const picked = [];
    for (const { item, qty } of lines) {
      let best = null;
      for (const c of catalog) {
        const score = itemScore(item.name, c.descriptor?.name);
        const price = Math.abs(Number(c.price?.value) - item.price) || 0;
        if (score >= ITEM_THRESHOLD && (!best || score > best.score || (score === best.score && price < best.price))) {
          best = { c, score, price };
        }
      }
      if (!best) return null;
      picked.push({ id: best.c.id, name: item.name, qty });
    }
    return picked;
  }

  function toQuote(body, picked, seller, restaurant, location) {
    const order = body?.message?.order;
    const q = order?.quote;
    if (body?.error || !q?.price?.value) return unavailable(body?.error?.message || 'Seller could not quote this order');
    const num = (v) => Math.round(Math.abs(Number(v) || 0));
    let itemsTotal = 0, deliveryFee = 0, taxes = 0, discount = 0;
    const lines = [];
    for (const b of q.breakup || []) {
      const type = b['@ondc/org/title_type'];
      const v = Number(b.price?.value) || 0;
      if (type === 'item') {
        const p = picked.find((x) => x.id === b['@ondc/org/item_id']);
        const qty = b['@ondc/org/item_quantity']?.count ?? p?.qty ?? 1;
        lines.push({ id: b['@ondc/org/item_id'], name: p?.name || b.title, qty, unitPrice: num(v / qty), lineTotal: num(v) });
        itemsTotal += num(v);
      } else if (type === 'delivery') deliveryFee += num(v);
      else if (type === 'tax') taxes += num(v);
      else if (type === 'discount' || type === 'offer') discount += num(v);
    }
    const total = num(q.price.value);
    const platformFee = total - itemsTotal - deliveryFee - taxes + discount; // packing + misc charges
    const km = distanceKm(location, restaurant);
    const tat = durationMinutes(order.fulfillments?.[0]?.['@ondc/org/TAT']);
    const eta = tat ?? Math.round(restaurant.prepMinutes + 9 + (km / PROFILE.speedKmh) * 60);
    return {
      platform: 'ondc', name: PROFILE.name, color: PROFILE.color, available: true, source: 'ondc',
      sellerApp: seller.bppId, sellerName: seller.provider.descriptor?.name,
      items: lines, itemsTotal, deliveryFee, platformFee, taxes, discount, couponCode: null, total,
      etaMinutes: eta, distanceKm: Math.round(km * 10) / 10, deepLink: { scheme: null, androidPackage: null },
    };
  }

  async function ondcQuote(ctx) {
    const { restaurant, lines, location } = ctx;
    const city = nearestCity(location);
    const sellers = await sellersFor(restaurant, city, location);
    const seller = pickSeller(restaurant, sellers);
    if (!seller) return unavailable('Not found on ONDC near you');
    const picked = matchItems(lines, seller);
    if (!picked) return unavailable('Some selected items are not on ONDC');
    const body = await client.select({
      cityCode: city.std, bppId: seller.bppId, bppUri: seller.bppUri, providerId: seller.provider.id,
      locationId: seller.location.id, items: picked, gps: `${location.lat},${location.lng}`, areaCode: city.pincode,
    });
    if (!body) throw new Error('select timed out');
    return toQuote(body, picked, seller, restaurant, location);
  }

  return {
    async quote(platform, ctx) {
      if (platform !== 'ondc') return simulated.quote(platform, ctx);
      try {
        return await ondcQuote(ctx);
      } catch (e) {
        console.warn('ONDC quote failed, using estimate:', e.message);
        return simulated.quote(platform, ctx);
      }
    },
  };
}

module.exports = { makeProvider };
