// Demo catalogue (Bengaluru). In production this comes from a restaurant directory
// (e.g. ONDC catalogue / your own DB). `platforms` lists the apps the restaurant is on.
const ALL = ['swiggy', 'zomato', 'magicpin', 'ondc'];

const bengaluruLocal = [
  {
    id: 'meghana', name: "Meghana Foods", cuisine: 'Biryani, Andhra', rating: 4.5,
    lat: 12.9352, lng: 77.6245, area: 'Koramangala', prepMinutes: 18, platforms: ALL,
    menu: [
      { id: 'mf1', name: 'Chicken Boneless Biryani', price: 320, veg: false },
      { id: 'mf2', name: 'Paneer Biryani', price: 280, veg: true },
      { id: 'mf3', name: 'Chicken 65', price: 260, veg: false },
      { id: 'mf4', name: 'Gulab Jamun (2 pc)', price: 90, veg: true },
    ],
  },
  {
    id: 'truffles', name: 'Truffles', cuisine: 'Burgers, American', rating: 4.4,
    lat: 12.9345, lng: 77.6101, area: 'St. Marks Road', prepMinutes: 20,
    platforms: ['swiggy', 'zomato', 'magicpin'],
    menu: [
      { id: 'tr1', name: 'All American Cheese Burger', price: 295, veg: false },
      { id: 'tr2', name: 'Veg Supreme Burger', price: 245, veg: true },
      { id: 'tr3', name: 'Peri Peri Fries', price: 165, veg: true },
      { id: 'tr4', name: 'Oreo Shake', price: 185, veg: true },
    ],
  },
  {
    id: 'empire', name: 'Empire Restaurant', cuisine: 'North Indian, Kebabs', rating: 4.2,
    lat: 12.9719, lng: 77.6412, area: 'Indiranagar', prepMinutes: 22,
    platforms: ['swiggy', 'zomato', 'ondc'],
    menu: [
      { id: 'em1', name: 'Butter Chicken', price: 310, veg: false },
      { id: 'em2', name: 'Ghee Rice', price: 150, veg: true },
      { id: 'em3', name: 'Kerala Parotta (2 pc)', price: 70, veg: true },
      { id: 'em4', name: 'Chicken Kebab', price: 270, veg: false },
    ],
  },
  {
    id: 'dominos', name: "Domino's Pizza", cuisine: 'Pizza, Fast Food', rating: 4.1,
    lat: 12.9279, lng: 77.6271, area: 'Koramangala', prepMinutes: 15,
    platforms: ALL,
    menu: [
      { id: 'dm1', name: 'Margherita (Medium)', price: 259, veg: true },
      { id: 'dm2', name: 'Peppy Paneer (Medium)', price: 399, veg: true },
      { id: 'dm3', name: 'Chicken Dominator (Medium)', price: 549, veg: false },
      { id: 'dm4', name: 'Garlic Breadsticks', price: 129, veg: true },
    ],
  },
  {
    id: 'saravana', name: 'Sri Saravana Bhavan', cuisine: 'South Indian, Tiffin', rating: 4.3,
    lat: 12.9611, lng: 77.6387, area: 'Domlur', prepMinutes: 12,
    platforms: ['swiggy', 'zomato', 'magicpin', 'ondc'],
    menu: [
      { id: 'sb1', name: 'Masala Dosa', price: 110, veg: true },
      { id: 'sb2', name: 'Idli Vada Combo', price: 95, veg: true },
      { id: 'sb3', name: 'Mini Tiffin', price: 190, veg: true },
      { id: 'sb4', name: 'Filter Coffee', price: 45, veg: true },
    ],
  },
  {
    id: 'biryanibluez', name: 'Biryani Blues', cuisine: 'Biryani, Hyderabadi', rating: 4.0,
    lat: 12.9784, lng: 77.6408, area: 'Indiranagar', prepMinutes: 20,
    platforms: ['swiggy', 'zomato', 'magicpin'],
    menu: [
      { id: 'bb1', name: 'Hyderabadi Chicken Dum Biryani', price: 349, veg: false },
      { id: 'bb2', name: 'Veg Dum Biryani', price: 289, veg: true },
      { id: 'bb3', name: 'Mirchi ka Salan', price: 80, veg: true },
      { id: 'bb4', name: 'Double ka Meetha', price: 120, veg: true },
    ],
  },
];



// ---- National chains, several outlets per city (illustrative demo data) ----
const CITIES = {
  Bengaluru: { std: 'std:080', pincode: '560001', lat: 12.9716, lng: 77.5946, areas: ['Koramangala', 'Indiranagar', 'HSR Layout', 'Whitefield', 'Jayanagar', 'MG Road'] },
  Mumbai:    { std: 'std:022', pincode: '400001', lat: 19.0760, lng: 72.8777, areas: ['Bandra', 'Andheri', 'Powai', 'Lower Parel', 'Colaba', 'Thane'] },
  Delhi:     { std: 'std:011', pincode: '110001', lat: 28.6139, lng: 77.2090, areas: ['Connaught Place', 'Saket', 'Hauz Khas', 'Dwarka', 'Rajouri Garden', 'Noida'] },
  Hyderabad: { std: 'std:040', pincode: '500001', lat: 17.3850, lng: 78.4867, areas: ['Banjara Hills', 'Jubilee Hills', 'Hitech City', 'Madhapur', 'Gachibowli', 'Secunderabad'] },
  Chennai:   { std: 'std:044', pincode: '600001', lat: 13.0827, lng: 80.2707, areas: ['T Nagar', 'Adyar', 'Anna Nagar', 'Velachery', 'OMR', 'Mylapore'] },
  Pune:      { std: 'std:020', pincode: '411001', lat: 18.5204, lng: 73.8567, areas: ['Koregaon Park', 'Kothrud', 'Viman Nagar', 'Hinjewadi', 'Baner', 'Camp'] },
  Kolkata:   { std: 'std:033', pincode: '700001', lat: 22.5726, lng: 88.3639, areas: ['Park Street', 'Salt Lake', 'New Town', 'Gariahat', 'Ballygunge', 'Howrah'] },
};

const m = (id, name, price, veg) => ({ id, name, price, veg });
const CHAINS = [
  { key: 'dominos', name: "Domino's Pizza", cuisine: 'Pizza, Fast Food', rating: 4.1, prep: 15, platforms: ALL, menu: [
    m('1', 'Margherita (Medium)', 259, true), m('2', 'Peppy Paneer (Medium)', 399, true), m('3', 'Chicken Dominator (Medium)', 549, false), m('4', 'Garlic Breadsticks', 129, true)] },
  { key: 'pizzahut', name: 'Pizza Hut', cuisine: 'Pizza, Italian', rating: 4.0, prep: 18, platforms: ['swiggy', 'zomato', 'magicpin'], menu: [
    m('1', 'Veg Supreme (Medium)', 449, true), m('2', 'Chicken Tikka Pizza (Medium)', 489, false), m('3', 'Cheesy Garlic Bread', 179, true), m('4', 'Choco Lava Cake', 109, true)] },
  { key: 'mcd', name: "McDonald's", cuisine: 'Burgers, Fast Food', rating: 4.2, prep: 12, platforms: ALL, menu: [
    m('1', 'McAloo Tikki Burger', 69, true), m('2', 'McChicken Burger', 159, false), m('3', 'Large Fries', 119, true), m('4', 'Chicken Maharaja Mac', 239, false), m('5', 'Coke (Medium)', 99, true)] },
  { key: 'kfc', name: 'KFC', cuisine: 'Fried Chicken, Burgers', rating: 4.1, prep: 15, platforms: ALL, menu: [
    m('1', 'Zinger Burger', 199, false), m('2', 'Hot & Crispy Chicken (4 pc)', 349, false), m('3', 'Veg Zinger', 179, true), m('4', 'Popcorn Chicken', 169, false)] },
  { key: 'bk', name: 'Burger King', cuisine: 'Burgers, Fast Food', rating: 4.1, prep: 14, platforms: ALL, menu: [
    m('1', 'Whopper', 229, false), m('2', 'Crispy Veg Burger', 109, true), m('3', 'Chicken Fries', 149, false), m('4', 'Veg Whopper', 199, true)] },
  { key: 'subway', name: 'Subway', cuisine: 'Sandwiches, Healthy', rating: 4.0, prep: 10, platforms: ['swiggy', 'zomato', 'magicpin'], menu: [
    m('1', 'Paneer Tikka Sub (6")', 219, true), m('2', 'Chicken Tikka Sub (6")', 249, false), m('3', 'Veg Delite Sub (6")', 189, true), m('4', 'Cookies (3 pc)', 99, true)] },
  { key: 'behrouz', name: 'Behrouz Biryani', cuisine: 'Biryani, Mughlai', rating: 4.2, prep: 25, platforms: ['swiggy', 'zomato', 'ondc'], menu: [
    m('1', 'Murgh Biryani', 429, false), m('2', 'Subz-e-Biryani', 359, true), m('3', 'Murgh Tikka Kebab', 349, false), m('4', 'Phirni', 129, true)] },
  { key: 'bbk', name: 'Biryani By Kilo', cuisine: 'Biryani, Hyderabadi', rating: 4.1, prep: 25, platforms: ['swiggy', 'zomato', 'magicpin'], menu: [
    m('1', 'Chicken Biryani (Handi)', 449, false), m('2', 'Paneer Biryani (Handi)', 399, true), m('3', 'Gulab Jamun', 99, true)] },
  { key: 'faasos', name: 'Faasos', cuisine: 'Wraps, Rolls', rating: 4.0, prep: 16, platforms: ['swiggy', 'zomato', 'ondc'], menu: [
    m('1', 'Chicken Tikka Wrap', 179, false), m('2', 'Paneer Wrap', 169, true), m('3', 'Egg Wrap', 139, false), m('4', 'Masala Fries', 99, true)] },
  { key: 'wowmomo', name: 'Wow! Momo', cuisine: 'Momos, Chinese', rating: 4.1, prep: 15, platforms: ALL, menu: [
    m('1', 'Veg Steamed Momos (8 pc)', 119, true), m('2', 'Chicken Steamed Momos (8 pc)', 139, false), m('3', 'Chicken Fried Momos (8 pc)', 159, false), m('4', 'Veg Thukpa', 149, true)] },
  { key: 'haldirams', name: "Haldiram's", cuisine: 'North Indian, Sweets, Snacks', rating: 4.3, prep: 15, platforms: ALL, menu: [
    m('1', 'Chole Bhature', 189, true), m('2', 'Raj Kachori', 129, true), m('3', 'Paneer Thali', 329, true), m('4', 'Kaju Katli (250 g)', 299, true)] },
  { key: 'starbucks', name: 'Starbucks', cuisine: 'Cafe, Coffee, Beverages', rating: 4.3, prep: 10, platforms: ['swiggy', 'zomato'], menu: [
    m('1', 'Caffe Latte (Tall)', 255, true), m('2', 'Cold Coffee (Tall)', 285, true), m('3', 'Chicken Sandwich', 265, false), m('4', 'Blueberry Muffin', 185, true)] },
  { key: 'bbq', name: 'Barbeque Nation', cuisine: 'BBQ, North Indian', rating: 4.4, prep: 28, platforms: ['swiggy', 'zomato'], menu: [
    m('1', 'Chicken Tikka Platter', 449, false), m('2', 'Paneer Tikka Platter', 399, true), m('3', 'Dal Makhani', 279, true), m('4', 'Butter Naan (2 pc)', 89, true)] },
  { key: 'pizzaexp', name: 'La Pino\'z Pizza', cuisine: 'Pizza, Fast Food', rating: 4.0, prep: 18, platforms: ['swiggy', 'zomato', 'ondc'], menu: [
    m('1', 'Cheese Burst Pizza (7")', 199, true), m('2', 'Chicken Supreme Pizza (7")', 249, false), m('3', 'Pasta Alfredo', 179, true)] },
  { key: 'bikanervala', name: 'Bikanervala', cuisine: 'North Indian, Sweets', rating: 4.2, prep: 16, platforms: ['swiggy', 'zomato', 'magicpin'], menu: [
    m('1', 'Pav Bhaji', 179, true), m('2', 'Samosa (2 pc)', 60, true), m('3', 'Special Thali', 299, true), m('4', 'Rasmalai (2 pc)', 119, true)] },
  { key: 'chaipoint', name: 'Chai Point', cuisine: 'Tea, Snacks, Beverages', rating: 4.1, prep: 8, platforms: ['swiggy', 'zomato', 'magicpin'], menu: [
    m('1', 'Masala Chai', 69, true), m('2', 'Bun Maska', 79, true), m('3', 'Veg Sandwich', 129, true), m('4', 'Chicken Puff', 89, false)] },
];

function h01(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967295;
}

const OUTLETS_PER_CITY = 3;
const generated = [];
for (const [city, c] of Object.entries(CITIES)) {
  for (const chain of CHAINS) {
    for (let n = 0; n < OUTLETS_PER_CITY; n++) {
      const seed = `${chain.key}|${city}|${n}`;
      const area = c.areas[Math.floor(h01(seed + 'a') * c.areas.length)];
      generated.push({
        id: `${chain.key}-${city.toLowerCase()}-${n + 1}`,
        name: chain.name, cuisine: chain.cuisine, area: `${area}, ${city}`,
        rating: Math.round((chain.rating + (h01(seed + 'r') - 0.5) * 0.4) * 10) / 10,
        lat: c.lat + (h01(seed + 'y') - 0.5) * 0.14, lng: c.lng + (h01(seed + 'x') - 0.5) * 0.14,
        prepMinutes: chain.prep, platforms: chain.platforms,
        menu: chain.menu.map((it) => ({ ...it, id: `${chain.key}${it.id}` })),
      });
    }
  }
}

// Bring your own real catalogue: set CATALOG_FILE to a JSON array of restaurants
// (same shape as above). It replaces the demo data entirely.
let restaurants = [...bengaluruLocal, ...generated.filter((r) => r.id.indexOf('dominos-bengaluru') !== 0)];
if (process.env.CATALOG_FILE) {
  restaurants = JSON.parse(require('fs').readFileSync(process.env.CATALOG_FILE, 'utf8'));
}

module.exports = { restaurants, PLATFORM_IDS: ALL, CITIES };
