# Juxtaposition

Compare the price **and** delivery time of the same meal across Swiggy, Zomato, Magicpin and ONDC, and get pointed to the best deal.

```
android/   Kotlin + Jetpack Compose app  (-> APK)
backend/   Node/Express API              (-> Render)
dist/      Prebuilt APK (Juxtaposition-1.0.0.apk, minSdk 26 / Android 8+)
render.yaml  Render blueprint (starter plan)
```

## How it works
1. Pick a restaurant (search by name/cuisine; optionally use your GPS location).
2. Pick the meal (items + quantities).
3. The app calls `POST /api/compare`; the backend asks every provider for a quote and returns, per app: item prices, delivery fee, platform fee, taxes, coupon discount, **total**, **ETA**, and badges (Cheapest / Fastest / Best value).
4. "Open app" launches the delivery app (or its Play Store page) to order.

ETA = restaurant prep time + courier hand-off + distance / courier speed. Best value = cheapest, unless a much faster app costs <= Rs.20 more.

## Important: data source
**Swiggy and Zomato have no public consumer price API, and scraping their apps breaches their terms**, so this repo does not do that. Out of the box the backend runs in `PROVIDER_MODE=simulated`: a deterministic demo catalogue (Bengaluru) with realistic fee/markup/ETA logic. The app shows a "Demo mode" notice in that case.

To get real prices, implement `backend/src/providers/live.js` with the same `quote(platform, ctx)` signature and register it in `providers/index.js`, then set `PROVIDER_MODE=live`. Legitimate sources: **ONDC** (open protocol; Magicpin/Paytm/Ola etc. are buyer apps on it) or partner/affiliate APIs you have an agreement with.

## Deploy the backend on Render (starter plan)
1. Render dashboard -> **New + -> Blueprint** -> select this repo. `render.yaml` creates the `juxtaposition-api` web service (plan: starter, root dir `backend`).
2. After deploy, open `https://<your-service>.onrender.com/health` -> `{"ok":true,...}`.
3. Point the app at it, either:
   - in the app: **Settings (gear) -> Server URL**, or
   - at build time: `cd android && ./gradlew assembleRelease -PapiUrl=https://<your-service>.onrender.com`
   - or set the GitHub repo variable `API_URL` so CI builds an APK with it baked in.

Local dev: `cd backend && npm i && npm start` (port 3000); in the emulator use `http://10.0.2.2:3000` as the Server URL.

## API
| Method | Path | Notes |
|---|---|---|
| GET | `/health` | liveness |
| GET | `/api/restaurants?q=&lat=&lng=` | sorted by distance |
| GET | `/api/restaurants/:id/menu` | |
| POST | `/api/compare` | `{restaurantId, items:[{menuItemId, qty}], lat?, lng?}` |

## Build the APK
Needs JDK 17+ and the Android SDK (platform 34, build-tools 34.0.0).
```
cd android
./gradlew testDebugUnitTest assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```
The release APK is signed with the debug key so it can be sideloaded (enable "install unknown apps"). For Play Store, add your own keystore in `app/build.gradle.kts`.

## Tests
`cd backend && npm test` (pricing/ETA logic) and `cd android && ./gradlew testDebugUnitTest` (response parsing).

## Catalogue
The demo catalogue has the local Bengaluru restaurants plus 16 national chains (Domino's, McDonald's, KFC, Behrouz, Starbucks, ...) with 3 outlets each in Bengaluru, Mumbai, Delhi, Hyderabad, Chennai, Pune and Kolkata. Search shows restaurants within 20 km of you; outside those cities the app falls back to Bengaluru.

This is illustrative data, not Swiggy/Zomato's real listings. To use your own, set `CATALOG_FILE=/path/to/restaurants.json` (array of objects shaped like those in `backend/src/data.js`).
