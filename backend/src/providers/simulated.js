// Simulated provider. Produces deterministic, realistic-looking quotes so the whole
// stack (API, app, comparison logic) works without scraping anyone's private API.
// Swap for real adapters in providers/index.js (see README -> "Going live").
const { distanceKm } = require('../geo');

const PROFILES = {
  swiggy:   { name: 'Swiggy',   markup: 1.08, baseFee: 20, perKm: 6, platformFee: 6, speedKmh: 20, handoff: 5, color: '#FC8019',
              coupon: { code: 'SWIGGYIT', pct: 0.15, cap: 100, min: 299 }, pkg: 'in.swiggy.android', scheme: 'swiggy://' },
  zomato:   { name: 'Zomato',   markup: 1.10, baseFee: 25, perKm: 5, platformFee: 7, speedKmh: 19, handoff: 6, color: '#E23744',
              coupon: { code: 'ZOMATOGOLD', pct: 0.20, cap: 120, min: 399 }, pkg: 'com.application.zomato', scheme: 'zomato://' },
  magicpin: { name: 'Magicpin', markup: 1.02, baseFee: 30, perKm: 4, platformFee: 0, speedKmh: 17, handoff: 8, color: '#7B3FE4',
              coupon: { code: 'MAGIC10', pct: 0.10, cap: 80, min: 200 }, pkg: 'com.magicpin.android', scheme: 'magicpin://' },
  ondc:     { name: 'ONDC (Ola/Paytm/Magicpin buyer apps)', markup: 0.95, baseFee: 25, perKm: 6, platformFee: 0, speedKmh: 18, handoff: 9, color: '#1B8A5A',
              coupon: null, pkg: null, scheme: null },
};

function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967295; // 0..1
}

async function quote(platform, { restaurant, lines, location }) {
  const p = PROFILES[platform];
  if (!restaurant.platforms.includes(platform)) {
    return { platform, name: p.name, color: p.color, available: false, source: 'simulated', reason: 'Restaurant not listed on this app' };
  }
  const km = distanceKm(location, restaurant);
  if (km > 15) {
    return { platform, name: p.name, color: p.color, available: false, source: 'simulated', reason: 'Outside delivery range (' + km.toFixed(1) + ' km)' };
  }

  // Per-item menu price differs by app (menu markup is common on aggregators).
  const items = lines.map(({ item, qty }) => {
    const jitter = 0.96 + hash(platform + item.id) * 0.08; // 0.96..1.04
    const unit = Math.round(item.price * p.markup * jitter);
    return { id: item.id, name: item.name, qty, unitPrice: unit, lineTotal: unit * qty };
  });
  const itemsTotal = items.reduce((s, i) => s + i.lineTotal, 0);

  const surge = hash(platform + restaurant.id + 'surge') > 0.8 ? 15 : 0; // occasional rain/peak surcharge
  const deliveryFee = Math.round(p.baseFee + p.perKm * Math.max(0, km - 1) + surge);
  const platformFee = p.platformFee;

  let discount = 0;
  let couponCode = null;
  if (p.coupon && itemsTotal >= p.coupon.min) {
    discount = Math.min(p.coupon.cap, Math.round(itemsTotal * p.coupon.pct));
    couponCode = p.coupon.code;
  }
  const taxes = Math.round((itemsTotal - discount) * 0.05 + platformFee * 0.18);
  const total = itemsTotal + deliveryFee + platformFee + taxes - discount;

  const travel = (km / p.speedKmh) * 60;
  const eta = Math.round(restaurant.prepMinutes + p.handoff + travel + hash(platform + restaurant.id + 'eta') * 6);

  return {
    platform, name: p.name, color: p.color, available: true, source: 'simulated',
    items, itemsTotal, deliveryFee, platformFee, taxes, discount, couponCode, total,
    etaMinutes: eta, distanceKm: Math.round(km * 10) / 10,
    deepLink: { scheme: p.scheme, androidPackage: p.pkg },
  };
}

module.exports = { quote, PROFILES };
