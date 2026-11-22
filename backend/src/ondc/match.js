// Fuzzy matching between our catalogue names and ONDC seller catalogue names.
const STOP = new Set(['the', 'and', 'of', 'with', 'a', 'pc', 'pcs']);

function tokens(s) {
  return new Set(
    String(s || '').toLowerCase().replace(/['’]/g, '').replace(/[^a-z0-9]+/g, ' ').split(' ').filter((t) => t && !STOP.has(t))
  );
}
// Share of the smaller token set found in the other (handles "Domino's Pizza - Koramangala").
function restaurantScore(ours, theirs) {
  const a = tokens(ours), b = tokens(theirs);
  if (!a.size || !b.size) return 0;
  let n = 0;
  for (const t of a) if (b.has(t)) n++;
  return n / Math.min(a.size, b.size);
}
// Share of OUR item's tokens covered by their item name.
function itemScore(ours, theirs) {
  const a = tokens(ours), b = tokens(theirs);
  if (!a.size) return 0;
  let n = 0;
  for (const t of a) if (b.has(t)) n++;
  return n / a.size;
}
function parseGps(gps) {
  const [lat, lng] = String(gps || '').split(',').map(Number);
  return Number.isFinite(lat) && Number.isFinite(lng) ? { lat, lng } : null;
}
// ISO-8601 duration (PT45M, PT1H30M, P0DT0H45M) -> minutes
function durationMinutes(iso) {
  const m = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(iso || '');
  if (!m) return null;
  const mins = (+m[1] || 0) * 1440 + (+m[2] || 0) * 60 + (+m[3] || 0) + Math.ceil((+m[4] || 0) / 60);
  return mins || null;
}
module.exports = { restaurantScore, itemScore, parseGps, durationMinutes };
