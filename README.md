# Nearby Eats (Juxtaposition)

Shows the restaurants, fast-food places and cafes around your **current location**, with the **distance and direction** to each, as a **list** or a **radar map**, with whether each place is **open right now** (and when it closes or next opens). The list is grouped into 500 m bands (Within 500 m, 500 m – 1 km, 1 – 1.5 km, … up to 5 km). Search by name or cuisine, filter by open now / veg / type ("Open now" hides only places known to be closed; places with no hours listed stay, labelled "Hours not listed"), and tap a place for details, directions, call and website.

```
android/   Kotlin + Jetpack Compose app  (-> APK)
backend/   Node/Express API              (-> Render)
dist/      Prebuilt APK (Juxtaposition-1.0.0.apk, v1.1.0, Android 8+)
render.yaml  Render blueprint (starter plan)
```

## How it works
1. The app asks for location permission and reads your position.
2. It asks **OpenStreetMap** (Overpass API; free, no key) in two ways at once, and the first good answer wins: directly from the phone (its own connection is rarely throttled), and via `GET /api/nearby?lat=&lng=&radius=` on your server (which caches for 5 minutes and is shared by all your users). Several OpenStreetMap mirrors are tried with staggered starts.
3. Wide searches load in steps (1 km, then 2 km, then the full radius): the nearest places appear straight away and each wider step swaps in when it arrives. If a wider step fails, the closer results stay on screen with a note instead of an error.
   Distance (straight-line) and compass bearing are computed per place; results are sorted by distance.
   The phone saves the last good list for each area, so if both routes fail it shows the saved copy with a notice instead of nothing.
4. The app plots them: you at the centre, distance rings around you, N marker; places closed right now are faded.
5. Open/closed status is worked out on the phone from OpenStreetMap's `opening_hours` tag (common formats like `Mo-Sa 11:00-23:00; Su off`, overnight hours and 24/7). Public holidays are ignored, anything more exotic shows no status, and many places have no hours at all, so treat it as a guide.

**Data caveats:** OpenStreetMap is community-maintained, so coverage is good in big cities and patchier elsewhere, and it has **no ratings or menu prices**. Distances are straight-line (the detail card shows an approximate walking time). Map data (c) OpenStreetMap contributors. The public Overpass servers have fair-use limits; the server caches and identifies itself, but for heavy use run your own Overpass instance (`OVERPASS_URLS`).

## API
| Method | Path | Notes |
|---|---|---|
| GET | `/health` | liveness; includes the deployed commit (`commit`) |
| GET | `/api/diag` | tests whether this server can reach each OpenStreetMap mirror (open in a browser to debug "data source is busy") |
| GET | `/api/nearby` | `lat`, `lng` (required), `radius` metres 200-5000 (default 1500), `types` = `restaurant,fast_food,cafe` |

## Deploy the backend on Render (starter plan)
Render dashboard -> **New + -> Blueprint** -> select this repo (Root Directory `backend`, Build `npm ci`, Start `npm start`). Then in the app: **Settings (gear) -> Server URL** -> your `https://<service>.onrender.com`. Check `<url>/health` first.

Local dev: `cd backend && npm i && npm start`; in the emulator use `http://10.0.2.2:3000`.

## Build the APK
JDK 17+, Android SDK (platform 34, build-tools 34.0.0):
```
cd android && ./gradlew testDebugUnitTest assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```
Signed with the debug key for sideloading; use your own keystore for the Play Store.

## Tests
`cd backend && npm test` and `cd android && ./gradlew testDebugUnitTest`.

---
## Earlier feature: price comparison (still in the backend, not used by the app)
### How it worked
1. Pick a restaurant (search by name/cuisine; optionally use your GPS location).
2. Pick the meal (items + quantities).
3. The app calls `POST /api/compare`; the backend asks every provider for a quote and returns, per app: item prices, delivery fee, platform fee, taxes, coupon discount, **total**, **ETA**, and badges (Cheapest / Fastest / Best value).
4. "Open app" launches the delivery app (or its Play Store page) to order.

ETA = restaurant prep time + courier hand-off + distance / courier speed. Best value = cheapest, unless a much faster app costs <= Rs.20 more.

## Important: data source
**Swiggy and Zomato have no public consumer price API, and scraping their apps breaches their terms**, so this repo does not do that. Out of the box the backend runs in `PROVIDER_MODE=simulated`: a deterministic demo catalogue (Bengaluru) with realistic fee/markup/ETA logic. The app shows a "Demo mode" notice in that case.

Real prices come from **ONDC** (open network) via the integration below (`PROVIDER_MODE=hybrid`); Swiggy and Zomato quotes stay simulated "Estimate"s unless you have a partner API agreement (add one as another provider in `providers/index.js`).

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
| GET | `/health` | liveness; includes the deployed commit (`commit`) |
| GET | `/api/diag` | tests whether this server can reach each OpenStreetMap mirror (open in a browser to debug "data source is busy") |
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

## ONDC integration (real prices)
`PROVIDER_MODE=hybrid` makes the **ONDC** quote real; Swiggy/Zomato/Magicpin stay estimates. In the app each card is tagged **Live ONDC price** or **Estimate**.

How a quote is produced (`backend/src/ondc/`, `providers/ondc.js`): signed Beckn `/search` to the ONDC gateway (restaurant name + your area) -> sellers reply asynchronously to our `/on_search` -> we fuzzy-match the restaurant (same brand within 3 km) and your items -> signed `/select` to that seller -> `/on_select` returns the real quote (item prices, delivery, packing, taxes, discounts, total, delivery TAT). If ONDC times out or errors, that card falls back to an Estimate. Callbacks are signature-verified against the ONDC registry; forged ones get 401.

**You must register as an ONDC buyer app first - credentials can't be generated for you.**
1. `cd backend && node scripts/gen-ondc-keys.js` -> keep the private keys secret, give ONDC the public keys.
2. Register as a *buyer app* (domain `ONDC:RET11`, food & beverage) with ONDC, starting in **staging**, then pre-prod, then prod. ONDC onboarding normally needs a registered business and a domain you control for the subscriber id (a custom domain on your Render service); confirm current requirements with ONDC.
3. Set the env vars in `render.yaml` on Render (`ONDC_SUBSCRIBER_ID`, `ONDC_UK_ID`, `ONDC_SIGNING_PRIVATE_KEY`, `ONDC_ENCRYPTION_PRIVATE_KEY`, `ONDC_REGISTRY_ENC_PUBLIC_KEY`, `ONDC_REQUEST_ID`) and `PROVIDER_MODE=hybrid`. The service exposes `/ondc-site-verification.html`, `/on_subscribe`, `/on_search`, `/on_select`.
4. Gateway/registry URLs default per `ONDC_ENV` (`src/ondc/config.js`); verify them against ONDC's current participant docs and override with `ONDC_GATEWAY_URL` / `ONDC_REGISTRY_URL` if needed.

Known limits: delivery pincode is approximated from the nearest supported city centre (no reverse geocoding yet); only restaurants already in the catalogue are priced on ONDC (listing ONDC-only restaurants is a next step); the protocol code is tested against a mock network, not yet against live ONDC.
