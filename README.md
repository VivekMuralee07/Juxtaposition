# Nearby Eats

Shows the restaurants, fast-food places and cafes around your **current location**, with the **distance and direction** to each, as a **list** (grouped into 500 m bands) or a **radar** that can point the way you are facing. It follows you as you walk, tells you what is **open now**, and lets you filter and search.

```
android/   Kotlin + Jetpack Compose app  (-> APK)
backend/   Node/Express API              (-> Render; optional backup route + diagnostics)
dist/      NearbyEats.apk (signed release build, Android 8+)
render.yaml  Render blueprint
```

## What it does
- **500 m / 1 km** chips, **type** (restaurant / fast food / cafe), **Open now**, **Veg**, and **search** by name or cuisine. All of these filter one list on the phone, so they are instant and never empty the screen.
- **Open now** hides only places known to be closed. Most OpenStreetMap places list no opening hours; those stay and are labelled "Hours not listed".
- **Radar**: you are at the centre with distance rings. **Heading up** (default) rotates the view so the top is where you face (uses the compass; a beam shows your facing). Tap the compass button for **North up**. Closed places are faded. Phones without a compass use North up automatically. The app is portrait-only.
- **Follows you**: distances and directions re-measure live as you walk; after about 200 m the list refreshes quietly in the background (the current list stays; a failed refresh only shows a note). GPS and compass run only while the app is on screen.
- **Food-specific icons** (🍛 biryani, 🍕 pizza, 🍔 burgers, ☕ coffee, 🥟 momos, …) chosen from the cuisine tag, or the place's name when there is no cuisine.
- **Radar rings are walking minutes** ("5 min · 10 min · 1 km · 16 min"; about 5 km/h with a 1.3x street detour). **Pinch or double-tap to zoom** (up to 6x; the rings rescale), tap a place for a light haptic tick.
- List: **pull down to refresh**; items slide into place when you change a filter or walk past one; List/Radar cross-fade. Splash screen on launch and a **themed app icon** for Android 13+.
- **What are you craving?** A row of cuisine chips built from what is actually nearby (🍛 Biryani 4, ☕ Coffee 2, …), most common first. Tap one to filter; tap again to clear.
- **Pick for me**: a button that suggests one random place that is not closed (places confirmed open come first, then ones with unknown hours); "Not this one" offers another, with no repeats until everything has been offered.
- **Closing soon**: places closing within 45 minutes show "Closes in 20 min" in amber.
- **Sort** the list by Nearest (grouped in 500 m bands), Open first, or A–Z.
- **Saved places** (⭐ on the details card): kept on the phone, with a "Saved" chip that shows them wherever you are.
- Tap a place for details: distance, direction, walking time, address, hours, and **Directions** (starts walking navigation in Google Maps if installed, otherwise opens any map app's pin, otherwise the browser), **Call**, **Website** and **Share** (name, address, hours, phone, website and a map link; nothing about where *you* are). A link opens the place on **OpenStreetMap's editor** so missing hours or cuisine can be added (a free OpenStreetMap account is needed to save changes).
- **Update check**: the app is installed by hand, so once a day it asks GitHub whether a newer release exists and shows an "Update available" banner with a Download button (Settings has the version and a manual "Check for updates").
- **Screen readers**: the radar is a drawing, so it announces a spoken summary ("5 places within 1 kilometre. Nearest: Truffles, 180 metres north-east…") and points to the List view, which is fully readable.
- A short **data line** at the bottom ("Updated 3 min ago • GPS ±12 m"); tap it for the full detail: which source answered (an OpenStreetMap server via the phone, or your server, or a saved copy), how long it took, and the location accuracy.

## How the data is loaded
1. One list is fetched for a 1 km circle around you from **OpenStreetMap** (Overpass API; free, no key), in two ways at once; the first good answer wins: **directly from the phone** (its own connection is rarely throttled) and via **your server** (`/api/nearby`, which caches for 5 minutes). Several Overpass mirrors are tried with staggered starts; an overloaded mirror's "empty list + remark" reply counts as a failure, never as "no places".
2. The phone saves the last good list per area. If both routes fail it shows the saved copy with a notice instead of nothing.
3. Distances (straight-line), bearings and open/closed status are computed on the phone. Open/closed is read from OpenStreetMap's `opening_hours` tag (common formats such as `Mo-Sa 11:00-15:00,18:00-23:00; Su off`, overnight hours and 24/7; public holidays are ignored; anything fancier shows no status).

**Data caveats:** OpenStreetMap is community-maintained: good coverage in big cities, patchier elsewhere, **no ratings or menu prices**. Hours may be out of date. Map data (c) OpenStreetMap contributors. The public Overpass servers have fair-use limits.

## Install
Download `dist/NearbyEats.apk` (https://github.com/VivekMuralee07/Juxtaposition/raw/main/dist/NearbyEats.apk), allow "install unknown apps", and open it. Once signing secrets are set (below), every merge to `main` also publishes a signed APK under **Releases** (`.../releases/latest/download/NearbyEats.apk`).

## Signing (important)
Android only updates an installed app if the new APK is signed with the **same key**. Releases are signed with a private release key that is **not in this repo**.
- Keep the keystore file and its password safe (back them up somewhere private). If they are lost you cannot update installed copies, and the Play Store would not accept a replacement.
- `android/release-cert.sha256` holds the key's public fingerprint. CI fails if `dist/NearbyEats.apk` (or the CI-built APK, when signing secrets are set) is signed with a different key.
- Build locally with the key: `KEYSTORE_PATH=/path/release.jks KEYSTORE_PASSWORD=... KEY_ALIAS=nearbyeats ./gradlew assembleRelease`. Without `KEYSTORE_PATH` the build is debug-signed and prints a warning; never distribute that.
- GitHub Actions: add repository secrets `KEYSTORE_BASE64` (the keystore, base64-encoded), `KEYSTORE_PASSWORD` and `KEY_ALIAS` (`nearbyeats`). CI then builds a signed APK, uploads it as an artifact, and on `main` publishes a release `v<versionName>` (bump `versionName`/`versionCode` in `android/app/build.gradle.kts` for each release).

## Backend (optional) on Render
The app works without it (the phone asks OpenStreetMap itself); the server is a shared cache and a backup route, and Settings has a switch to stop using it. To see whether it adds anything, open `<your server URL>/api/diag`: if every mirror shows `timeout` or an error from the server, the server cannot reach OpenStreetMap either and the phone does all the work. Render dashboard -> **New + -> Blueprint** -> select this repo (Root Directory `backend`, Build `npm ci`, Start `npm start`). Then in the app: **Settings (gear) -> Server URL** -> `https://<service>.onrender.com`.

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | liveness; includes the deployed commit |
| GET | `/api/nearby` | `lat`, `lng` (required), `radius` metres 200-5000 (default 1500), `types` = `restaurant,fast_food,cafe` |
| GET | `/api/diag` | tests whether this server can reach each OpenStreetMap mirror (open in a browser to debug "data source is busy") |

Local dev: `cd backend && npm i && npm start`; in the emulator use `http://10.0.2.2:3000` as the Server URL.

## Build and test
JDK 17+, Android SDK (platform 34, build-tools 34.0.0):
```
cd android && ./gradlew testDebugUnitTest assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
cd backend && npm test
```
