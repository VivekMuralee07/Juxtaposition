// Demo catalogue (Bengaluru). In production this comes from a restaurant directory
// (e.g. ONDC catalogue / your own DB). `platforms` lists the apps the restaurant is on.
const ALL = ['swiggy', 'zomato', 'magicpin', 'ondc'];

const restaurants = [
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

module.exports = { restaurants, PLATFORM_IDS: ALL };
