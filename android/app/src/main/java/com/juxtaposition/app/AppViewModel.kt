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

enum class ViewMode { LIST, RADAR }

/**
 * One list is fetched for a 1 km circle around the user. Everything on screen (500 m / 1 km, type, search,
 * open now, veg, distances, directions) is derived from that list on the phone, so changing a chip never touches
 * the network and never empties the screen. As the user walks, distances update live and the list is refreshed
 * quietly in the background after about 200 m of movement.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cache = PlacesCache(app.getSharedPreferences("places_cache", Context.MODE_PRIVATE))
    private val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var loadSeq = 0
    private var fetching = false
    private var lastAttemptMs = 0L
    private var fetchedAt: Pair<Double, Double>? = null
    private var updatesRunning = false

    var apiUrl by mutableStateOf(prefs.getString("apiUrl", BuildConfig.DEFAULT_API_URL)!!); private set
    /** The user's live position. */
    var origin by mutableStateOf<Pair<Double, Double>?>(null); private set
    var radiusM by mutableIntStateOf(LiveUpdates.MAX_RADIUS_M); private set
    var typeFilter by mutableStateOf<String?>(null); private set
    /** The fetched list; each place's distance/direction is re-measured from [origin] in [visiblePlaces]. */
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var selectedId by mutableStateOf<String?>(null)
    var viewMode by mutableStateOf(ViewMode.RADAR); private set
    var query by mutableStateOf(""); private set
    var openNow by mutableStateOf(false); private set
    var vegOnly by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var locationDenied by mutableStateOf(false); private set
    /** Shown when the list is a saved copy, or a background refresh failed. */
    var notice by mutableStateOf<String?>(null); private set

    val hasData: Boolean get() = places.isNotEmpty()

    /** What the screens show: filters applied, distances measured from where the user is right now. */
    val visiblePlaces: List<Place>
        get() {
            val here = origin ?: return emptyList()
            return PlaceFilters.visible(
                PlaceFilters.relocate(places, here.first, here.second),
                radiusM / 1000.0, typeFilter, query, vegOnly, openNow,
            )
        }

    val selected: Place? get() = selectedId?.let { id -> visiblePlaces.firstOrNull { it.id == id } }

    fun saveApiUrl(url: String) {
        apiUrl = url.trim().ifEmpty { BuildConfig.DEFAULT_API_URL }
        prefs.edit().putString("apiUrl", apiUrl).apply()
        load(userInitiated = true)
    }

    fun onPermissionResult(granted: Boolean) {
        locationDenied = !granted
        if (granted) { locate(); startLocationUpdates() }
    }

    // Local-only changes: no network, so the list can never disappear because of them.
    fun setRadius(m: Int) { radiusM = m.coerceAtMost(LiveUpdates.MAX_RADIUS_M); selectedId = null }
    fun updateTypeFilter(t: String?) { typeFilter = t; selectedId = null }
    fun updateViewMode(m: ViewMode) { viewMode = m }
    fun updateQuery(q: String) { query = q; selectedId = null }
    fun updateOpenNow(on: Boolean) { openNow = on; selectedId = null }
    fun updateVegOnly(on: Boolean) { vegOnly = on; selectedId = null }
    fun select(p: Place?) { selectedId = p?.id }
    fun dismissError() { error = null }

    // ---- location ----

    /** One quick fix (used on launch and by the refresh button); also forces a fresh fetch. */
    @SuppressLint("MissingPermission")
    fun locate() {
        val ctx = getApplication<Application>()
        loading = true; error = null
        fun fallback() {
            val last = locationManager.getProviders(true).mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (last != null) { origin = last.latitude to last.longitude; load(userInitiated = true) }
            else { loading = false; error = "Couldn't get your location. Turn on location services and tap Refresh." }
        }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider != null && android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching {
                locationManager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                    if (loc != null) { origin = loc.latitude to loc.longitude; load(userInitiated = true) } else fallback()
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

    override fun onCleared() { stopLocationUpdates() }

    private fun onLiveFix(loc: Location) {
        if (!LiveUpdates.accurateEnough(if (loc.hasAccuracy()) loc.accuracy else null)) return
        val here = loc.latitude to loc.longitude
        origin = here // distances and directions on screen re-measure from this immediately
        if (LiveUpdates.shouldRefetch(fetchedAt, here, lastAttemptMs, System.currentTimeMillis(), fetching)) load(userInitiated = false)
    }

    // ---- data ----

    /**
     * Asks OpenStreetMap straight from the phone AND your server at the same time; the first good answer wins.
     * (Shared cloud servers are often throttled by the public OpenStreetMap servers; a phone usually is not.)
     */
    private suspend fun fetchFirst(lat: Double, lng: Double): Pair<NearbyResult?, List<String>> {
        val ch = Channel<Pair<String, Result<NearbyResult>>>(Channel.UNLIMITED)
        val jobs = listOf(
            viewModelScope.launch { ch.send("OpenStreetMap" to runCatching { OverpassClient.nearby(lat, lng, LiveUpdates.MAX_RADIUS_M) }) },
            viewModelScope.launch { ch.send("server" to runCatching { withContext(Dispatchers.IO) { ApiClient(apiUrl).nearby(lat, lng, LiveUpdates.MAX_RADIUS_M) } }) },
        )
        val problems = mutableListOf<String>()
        repeat(2) {
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
            val (fresh, problems) = fetchFirst(lat, lng)
            if (mySeq != loadSeq) return@launch // a newer request has started; ignore this one
            val key = cache.key(lat, lng, LiveUpdates.MAX_RADIUS_M)
            when {
                fresh != null -> {
                    places = fresh.places; fetchedAt = lat to lng; notice = null
                    cache.save(key, fresh)
                }
                else -> {
                    val saved = cache.load(key)
                    when {
                        places.isNotEmpty() -> notice = "Couldn't refresh the list (${problems.firstOrNull()?.take(100).orEmpty()}). Showing the previous one."
                        saved != null -> {
                            places = saved.result.places; fetchedAt = lat to lng
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
        is ApiException -> t.message ?: "Server error"
        is java.net.SocketTimeoutException -> "The server took too long to respond. Try again in a moment."
        is java.io.IOException -> "Can't reach the server at $apiUrl. Check your connection or the server URL in settings."
        else -> t.message ?: "Something went wrong"
    }
}
