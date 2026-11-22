const test = require('node:test');
const assert = require('node:assert');
const { compare, searchRestaurants, getMenu, BadRequest, DEFAULT_LOCATION } = require('../src/compare');

test('search finds restaurants by cuisine and sorts by distance', () => {
  const r = searchRestaurants('biryani', DEFAULT_LOCATION);
  assert.ok(r.length >= 2);
  assert.ok(r.every((x) => /biryani/i.test(x.name + x.cuisine)));
  for (let i = 1; i < r.length; i++) assert.ok(r[i].distanceKm >= r[i - 1].distanceKm);
});

test('compare returns cheapest, fastest and sane totals', async () => {
  const out = await compare({
    restaurantId: 'meghana',
    items: [{ menuItemId: 'mf1', qty: 2 }, { menuItemId: 'mf4', qty: 1 }],
    location: DEFAULT_LOCATION,
  });
  const live = out.results.filter((r) => r.available);
  assert.ok(live.length >= 2);
  for (const q of live) {
    assert.strictEqual(q.total, q.itemsTotal + q.deliveryFee + q.platformFee + q.taxes - q.discount);
    assert.ok(q.etaMinutes > 0);
  }
  const min = Math.min(...live.map((q) => q.total));
  assert.strictEqual(live.find((q) => q.platform === out.cheapestPlatform).total, min);
  assert.strictEqual(out.maxSavings, Math.max(...live.map((q) => q.total)) - min);
  assert.ok(out.bestPlatform);
});

test('restaurant missing on an app is marked unavailable', async () => {
  const out = await compare({ restaurantId: 'truffles', items: [{ menuItemId: 'tr1', qty: 1 }], location: DEFAULT_LOCATION });
  assert.strictEqual(out.results.find((r) => r.platform === 'ondc').available, false);
});

test('far away user gets nothing deliverable', async () => {
  const out = await compare({ restaurantId: 'meghana', items: [{ menuItemId: 'mf1', qty: 1 }], location: { lat: 28.6, lng: 77.2 } });
  assert.strictEqual(out.bestPlatform, null);
});

test('is deterministic', async () => {
  const a = { restaurantId: 'dominos', items: [{ menuItemId: 'dm1', qty: 1 }], location: DEFAULT_LOCATION };
  assert.deepStrictEqual(await compare(a), await compare(a));
});

test('validation', async () => {
  await assert.rejects(compare({ restaurantId: 'nope', items: [{ menuItemId: 'x', qty: 1 }], location: DEFAULT_LOCATION }), BadRequest);
  await assert.rejects(compare({ restaurantId: 'meghana', items: [], location: DEFAULT_LOCATION }), BadRequest);
  await assert.rejects(compare({ restaurantId: 'meghana', items: [{ menuItemId: 'mf1', qty: 0 }], location: DEFAULT_LOCATION }), BadRequest);
  assert.strictEqual(getMenu('nope'), null);
});
