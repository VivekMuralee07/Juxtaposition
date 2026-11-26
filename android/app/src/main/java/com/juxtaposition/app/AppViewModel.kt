package com.juxtaposition.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewMode { LIST, RADAR, MAP }

/**
 * One list is fetched for a 1 km circle around the user. Everything on screen (500 m / 1 km, type, search,
 * open now, veg, distances, directions) is derived from that list on the phone, so changing a chip never touches
 * the network and never empties the screen. As the user walks, distances update live and the list is refreshed
 * quietly in the background after about 200 m of movement.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cache = PlacesCache(app.getSharedPreferences("places_cache", Context.MODE_PRIVATE))
    private val favoritesStore = FavoritesStore(app.getSharedPreferences("favorites", Context.MODE_PRIVATE))
    private var pickHistory: Set<String> = emptySet()
    private val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var loadSeq = 0
    private var fetching = false
    private var lastAttemptMs = 0L
    private var fetchedAt: Pair<Double, Double>? = null
    private var updatesRunning = false
    private var foreground = false
    private var compass: CompassSensor? = null
    private var declination = 0f

    /** A searched area the list is centred on instead of the user's position (null = around the user). */
    var exploring by mutableStateOf<GeoResult?>(null); private set
    /** Results of the last area search (null = none yet) and whether one is running. */
    var geoResults by mutableStateOf<List<GeoResult>?>(null); private set
    var geoBusy by mutableStateOf(false); private set
    var geoMessage by mutableStateOf<String?>(null); private set
    /** The list's centre: the user's live position, or the searched area while [exploring]. */
    var origin by mutableStateOf<Pair<Double, Double>?>(null); private set
    var radiusM by mutableIntStateOf(LiveUpdates.MAX_RADIUS_M); private set
    var typeFilter by mutableStateOf<String?>(null); private set
    /** The fetched list; each place's distance/direction is re-measured from [origin] in [visiblePlaces]. */
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var selectedId by mutableStateOf<String?>(null)
    var viewMode by mutableStateOf(ViewMode.RADAR); private set
    /** Radar orientation: true = "up" is where you face (needs the compass); false = north is up. */
    var headingUp by mutableStateOf(prefs.getBoolean("headingUp", true)); private set
    var headingDeg by mutableStateOf<Float?>(null); private set
    var compassAvailable by mutableStateOf(true); private set
    var compassLow by mutableStateOf(false); private set
    var query by mutableStateOf(""); private set
    var openNow by mutableStateOf(false); private set
    var vegOnly by mutableStateOf(false); private set
    var cuisine by mutableStateOf<String?>(null); private set
    var savedOnly by mutableStateOf(false); private set
    var sort by mutableStateOf(SortMode.NEAREST); private set
    /** Saved places (kept in full, newest first). */
    var favorites by mutableStateOf(favoritesStore.load()); private set
    /** The place offered by "Pick for me" (so the details card can say so and offer another). */
    var pickedId by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var locationDenied by mutableStateOf(false); private set
    /** Shown when the list is a saved copy, or a background refresh failed. */
    var notice by mutableStateOf<String?>(null); private set
    private var dataInfo by mutableStateOf<DataInfo?>(null)
    private var fixInfo by mutableStateOf<String?>(null)

    /** One line for the footer: which source answered, how long it took, how fresh, and the location fix quality. */
    fun dataLine(): String? = DataLine.describe(dataInfo, fixInfo, System.currentTimeMillis())
    fun dataLineShort(): String? = DataLine.short(dataInfo, fixInfo, System.currentTimeMillis())

    val hasData: Boolean get() = places.isNotEmpty()

    // ---- app updates (the app is installed by hand, so it asks GitHub whether a newer release exists) ----
    var update by mutableStateOf<UpdateInfo?>(null); private set
    /** Result of a manual "Check for updates" (shown in Settings). */
    var updateMessage by mutableStateOf<String?>(null); private set

    fun checkForUpdates(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && !UpdateChecker.shouldCheck(prefs.getLong("lastUpdateCheck", 0L), now)) return
        if (manual) updateMessage = "Checking…"
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { UpdateChecker.fetch() } }
                .onSuccess { json ->
                    prefs.edit().putLong("lastUpdateCheck", now).apply()
                    val info = UpdateChecker.parse(json, BuildConfig.VERSION_NAME)
                    val dismissed = prefs.getString("dismissedUpdate", null) == info?.version
                    update = if (info != null && (manual || !dismissed)) info else if (info == null) null else update
                    if (manual) updateMessage = if (info != null) "Version ${info.version} is available." else "You're up to date (version ${BuildConfig.VERSION_NAME})."
                }
                .onFailure { if (manual) updateMessage = "Couldn't check for updates. Check your connection and try again." }
        }
    }

    /** In-app download of the update: null = not started, 0..100 = downloading (-1 = size unknown), 101 = ready to install. */
    var downloadPct by mutableStateOf<Int?>(null); private set
    private var downloadedApk: java.io.File? = null
    val readyApk: java.io.File? get() = if (downloadPct == DOWNLOAD_READY) downloadedApk else null

    /** Downloads the update inside the app; [onFail] is called on the main thread so the UI can fall back to the browser. */
    fun downloadUpdate(onFail: () -> Unit) {
        val info = update ?: return
        if (downloadPct != null && downloadPct != DOWNLOAD_READY) return
        val ctx = getApplication<Application>()
        if (!ApkInstaller.isTrustedUrl(info.downloadUrl)) { onFail(); return }
        downloadPct = 0
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val f = ApkInstaller.download(ctx, info.downloadUrl) { pct -> downloadPct = pct }
                    if (!ApkInstaller.looksRight(ctx, f)) { f.delete(); throw java.io.IOException("Wrong file") }
                    f
                }
            }.onSuccess { downloadedApk = it; downloadPct = DOWNLOAD_READY }
                .onFailure { downloadPct = null; error = "Couldn't download the update. Opening it in your browser instead."; onFail() }
        }
    }

    fun dismissUpdate() {
        update?.let { prefs.edit().putString("dismissedUpdate", it.version).apply() }
        update = null
    }

    fun clearUpdateMessage() { updateMessage = null }

    init { checkForUpdates(manual = false) }

    /** Places within the chosen radius, measured from where the user is now (before the other filters). */
    private fun inRadius(): List<Place> {
        val here = origin ?: return emptyList()
        return PlaceFilters.relocate(places, here.first, here.second).filter { it.distanceKm <= radiusM / 1000.0 }
    }

    /** Cuisine chips for what is actually nearby (empty when there is nothing to choose between). */
    val cuisineChips: List<CuisineChip> get() = CuisineChips.from(inRadius())

    val favoriteIds: Set<String> get() = favorites.mapTo(HashSet()) { it.id }

    /**
     * What the screens show: filters applied, distances measured from where the user is right now.
     * "Saved" shows every saved place (their fresh data when we have it), however far away.
     */
    val visiblePlaces: List<Place>
        get() {
            val here = origin ?: return emptyList()
            val base = if (savedOnly) Favorites.merge(favorites, places) else places
            val filtered = PlaceFilters.visible(
                PlaceFilters.relocate(base, here.first, here.second),
                if (savedOnly) Double.MAX_VALUE else radiusM / 1000.0, typeFilter, query, vegOnly, openNow, cuisine = cuisine,
            )
            return PlaceSorting.sort(filtered, sort)
        }

    val selected: Place? get() = selectedId?.let { id -> visiblePlaces.firstOrNull { it.id == id } }

    fun onPermissionResult(granted: Boolean) {
        locationDenied = !granted
        if (granted) { locate(); startLocationUpdates() }
    }

    // Local-only changes: no network, so the list can never disappear because of them.
    fun setRadius(m: Int) { radiusM = m.coerceAtMost(LiveUpdates.MAX_RADIUS_M); selectedId = null }
    fun updateTypeFilter(t: String?) { typeFilter = t; selectedId = null }
    fun updateViewMode(m: ViewMode) { viewMode = m; syncCompass() }
    fun toggleHeadingUp() {
        headingUp = !headingUp
        prefs.edit().putBoolean("headingUp", headingUp).apply()
        syncCompass()
    }
    fun updateQuery(q: String) { query = q; selectedId = null }
    fun updateOpenNow(on: Boolean) { openNow = on; selectedId = null }
    fun updateVegOnly(on: Boolean) { vegOnly = on; selectedId = null }
    fun select(p: Place?) { selectedId = p?.id; if (p?.id != pickedId) pickedId = null }
    fun updateCuisine(c: String?) { cuisine = if (cuisine == c) null else c; selectedId = null; pickedId = null }
    fun updateSavedOnly(on: Boolean) { savedOnly = on; selectedId = null; pickedId = null }
    fun updateSort(m: SortMode) { sort = m }

    fun toggleFavorite(p: Place) {
        favorites = Favorites.toggle(favorites, p)
        favoritesStore.save(favorites)
        if (savedOnly && favorites.isEmpty()) savedOnly = false
    }

    /** Offers one random place that is not closed, from whatever the filters currently show. Asking again gives another. */
    fun pickForMe() {
        val (pick, history) = PickForMe.next(visiblePlaces, pickHistory, kotlin.random.Random.Default)
        pickHistory = history
        if (pick == null) { pickedId = null; error = "Nothing open to pick from here. Try clearing a filter or tap the location button to refresh."; return }
        selectedId = pick.id
        pickedId = pick.id
    }

    fun dismissError() { error = null }

    fun searchArea(q: String) {
        if (q.isBlank() || geoBusy) return
        geoBusy = true; geoMessage = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { Geocoder.search(q) } }
                .onSuccess { geoResults = it; geoMessage = if (it.isEmpty()) "No match. Try a fuller name, like the area and city." else null }
                .onFailure { geoMessage = "Couldn't search. Check your connection and try again." }
            geoBusy = false
        }
    }

    fun clearAreaSearch() { geoResults = null; geoMessage = null }

    /** Centres the list on a searched area; your own position is left alone until you go back to it. */
    fun explore(spot: GeoResult) {
        exploring = spot
        selectedId = null; pickedId = null; cuisine = null; savedOnly = false; query = ""
        places = emptyList(); fetchedAt = null; notice = null; dataInfo = null; fixInfo = null
        clearAreaSearch()
        setOrigin(spot.lat, spot.lng)
        syncCompass()
        load(userInitiated = true)
    }

    private fun setOrigin(lat: Double, lng: Double) {
        origin = lat to lng
        declination = CompassSensor.declination(lat, lng) // magnetic north -> true north
        compass?.declinationDeg = declination
    }

    // ---- compass (only runs while the app is on screen, on the radar, in heading-up mode) ----

    /** Called from the activity's onStart/onStop. */
    fun onForeground() { foreground = true; syncCompass() }
    fun onBackground() { foreground = false; syncCompass() }

    private fun syncCompass() {
        val wanted = foreground && headingUp && viewMode == ViewMode.RADAR && exploring == null
        if (!wanted) { compass?.stop(); return }
        val c = compass ?: CompassSensor(getApplication()).also { compass = it }
        compassAvailable = c.available
        if (!c.available) return
        c.declinationDeg = declination
        c.onHeading = { headingDeg = it }
        c.onLowAccuracy = { compassLow = it }
        c.start()
    }

    // ---- location ----

    /** One quick fix (used on launch and by the refresh button); also forces a fresh fetch. */
    @SuppressLint("MissingPermission")
    fun locate() {
        val ctx = getApplication<Application>()
        loading = true; error = null
        if (exploring != null) { exploring = null; places = emptyList(); fetchedAt = null; notice = null; dataInfo = null; selectedId = null; pickedId = null; cuisine = null; syncCompass() }
        /** A fix that arrives after you chose an area to explore is ignored. */
        fun firstFix(lat: Double, lng: Double) { if (exploring != null) return; setOrigin(lat, lng); load(userInitiated = true) }
        fun fallback() {
            val last = locationManager.getProviders(true).mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (last != null) { fixInfo = DataLine.fix(last.provider, if (last.hasAccuracy()) last.accuracy else null); firstFix(last.latitude, last.longitude) }
            else { loading = false; error = "Couldn't get your location. Turn on location services and tap Refresh." }
        }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider != null && android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching {
                locationManager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                    if (loc != null) { fixInfo = DataLine.fix(loc.provider, if (loc.hasAccuracy()) loc.accuracy else null); firstFix(loc.latitude, loc.longitude) } else fallback()
                }
            }.onFailure { fallback() }
        } else fallback()
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLiveFix(location)
        @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** Follow the user while the app is on screen (called from onStart). */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (updatesRunning) return
        updatesRunning = true
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                if (locationManager.isProviderEnabled(p)) locationManager.requestLocationUpdates(p, 5_000L, 10f, listener, Looper.getMainLooper())
            }
        }
    }

    /** Stop following (called from onStop) so the GPS is not left running in the background. */
    fun stopLocationUpdates() {
        if (!updatesRunning) return
        updatesRunning = false
        runCatching { locationManager.removeUpdates(listener) }
    }

    override fun onCleared() { stopLocationUpdates(); compass?.stop() }

    private fun onLiveFix(loc: Location) {
        if (exploring != null) return // the list is centred on a searched area, not on you
        if (!LiveUpdates.accurateEnough(if (loc.hasAccuracy()) loc.accuracy else null)) return
        val here = loc.latitude to loc.longitude
        fixInfo = DataLine.fix(loc.provider, if (loc.hasAccuracy()) loc.accuracy else null)
        setOrigin(here.first, here.second) // distances and directions on screen re-measure from this immediately
        if (LiveUpdates.shouldRefetch(fetchedAt, here, lastAttemptMs, System.currentTimeMillis(), fetching)) load(userInitiated = false)
    }

    // ---- data ----

    /** Asks OpenStreetMap straight from the phone (several mirrors, staggered; the first good answer wins). */
    private suspend fun fetchFirst(lat: Double, lng: Double): Pair<NearbyResult?, List<String>> {
        val ch = Channel<Pair<String, Result<NearbyResult>>>(Channel.UNLIMITED)
        val jobs = mutableListOf(
            viewModelScope.launch { ch.send("OpenStreetMap" to runCatching { OverpassClient.nearby(lat, lng, LiveUpdates.MAX_RADIUS_M) }) },
        )
        val problems = mutableListOf<String>()
        repeat(jobs.size) {
            val (source, result) = ch.receive()
            result.onSuccess { value -> jobs.forEach { j -> j.cancel() }; return value to emptyList() }
            result.onFailure { e -> problems += "$source: ${friendly(e)}" }
        }
        return null to problems
    }

    /** `userInitiated` loads show errors; background refreshes (after moving) never clear or replace the list on failure. */
    fun load(userInitiated: Boolean) {
        val (lat, lng) = origin ?: return
        val mySeq = ++loadSeq
        fetching = true
        lastAttemptMs = System.currentTimeMillis()
        viewModelScope.launch {
            loading = true
            if (userInitiated) error = null
            val startedAt = System.currentTimeMillis()
            val (fresh, problems) = fetchFirst(lat, lng)
            val tookMs = System.currentTimeMillis() - startedAt
            if (mySeq != loadSeq) return@launch // a newer request has started; ignore this one
            val key = cache.key(lat, lng, LiveUpdates.MAX_RADIUS_M)
            when {
                fresh != null -> {
                    places = fresh.places; fetchedAt = lat to lng; notice = null
                    if (cuisine != null && fresh.places.none { it.category()?.label == cuisine }) cuisine = null
                    dataInfo = DataInfo(fresh.via, tookMs, System.currentTimeMillis(), saved = false)
                    cache.save(key, fresh)
                }
                else -> {
                    val saved = cache.load(key)
                    when {
                        places.isNotEmpty() -> notice = "Couldn't refresh the list (${problems.firstOrNull()?.take(100).orEmpty()}). Showing the previous one."
                        saved != null -> {
                            places = saved.result.places; fetchedAt = lat to lng
                            dataInfo = DataInfo(null, null, saved.savedAt, saved = true)
                            notice = "Live data unavailable. Showing results saved ${agoText(System.currentTimeMillis() - saved.savedAt)}."
                        }
                        else -> error = "Couldn't load restaurants. " + problems.joinToString(" • ")
                    }
                }
            }
            loading = false; fetching = false
        }
    }

    private fun friendly(t: Throwable) = when (t) {
        is ApiException -> t.message ?: "OpenStreetMap is busy"
        is java.net.SocketTimeoutException -> "OpenStreetMap took too long to respond. Try again in a moment."
        is java.io.IOException -> "Can't reach OpenStreetMap. Check your connection."
        else -> t.message ?: "Something went wrong"
    }

    companion object { const val DOWNLOAD_READY = 101 }
}
