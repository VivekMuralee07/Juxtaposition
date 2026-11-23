package com.juxtaposition.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cache = PlacesCache(app.getSharedPreferences("places_cache", Context.MODE_PRIVATE))
    private var loadSeq = 0

    var apiUrl by mutableStateOf(prefs.getString("apiUrl", BuildConfig.DEFAULT_API_URL)!!); private set
    var origin by mutableStateOf<Pair<Double, Double>?>(null); private set
    var radiusM by mutableIntStateOf(5000); private set
    var typeFilter by mutableStateOf<String?>(null); private set
    var places by mutableStateOf<List<Place>>(emptyList()); private set
    var selected by mutableStateOf<Place?>(null); private set
    var viewMode by mutableStateOf(ViewMode.RADAR); private set
    var query by mutableStateOf(""); private set
    var openNow by mutableStateOf(false); private set
    var vegOnly by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var locationDenied by mutableStateOf(false); private set
    /** Shown when the list comes from the phone's saved copy because live data was unavailable. */
    var notice by mutableStateOf<String?>(null); private set

    /** Places after the type / search / open-now / veg filters. */
    val visiblePlaces: List<Place>
        get() {
            val q = query.trim().lowercase()
            val now = java.time.LocalDateTime.now()
            return places.filter { p ->
                (typeFilter == null || p.type == typeFilter) &&
                    (q.isEmpty() || p.name.lowercase().contains(q) || (p.cuisine?.lowercase()?.contains(q) == true)) &&
                    (!vegOnly || p.vegetarian != null) &&
                    (!openNow || p.hoursStatus(now) !is HoursStatus.Closed) // most OSM places list no hours, so "open now" keeps them (they are labelled "Hours not listed")
            }
        }

    fun saveApiUrl(url: String) {
        apiUrl = url.trim().ifEmpty { BuildConfig.DEFAULT_API_URL }
        prefs.edit().putString("apiUrl", apiUrl).apply()
        load()
    }

    fun onPermissionResult(granted: Boolean) {
        locationDenied = !granted
        if (granted) locate()
    }

    fun setRadius(m: Int) { radiusM = m; load() }
    fun updateTypeFilter(t: String?) { typeFilter = t; selected = null }
    fun updateViewMode(m: ViewMode) { viewMode = m }
    fun updateQuery(q: String) { query = q; selected = null }
    fun updateOpenNow(on: Boolean) { openNow = on; selected = null }
    fun updateVegOnly(on: Boolean) { vegOnly = on; selected = null }
    fun select(p: Place?) { selected = p }
    fun dismissError() { error = null }

    @SuppressLint("MissingPermission")
    fun locate() {
        val ctx = getApplication<Application>()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        loading = true; error = null
        fun fallback() {
            val last = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (last != null) onLocation(last)
            else { loading = false; error = "Couldn't get your location. Turn on location services and tap Refresh." }
        }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider != null && Build.VERSION.SDK_INT >= 30) {
            runCatching {
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { loc -> if (loc != null) onLocation(loc) else fallback() }
            }.onFailure { fallback() }
        } else fallback()
    }

    private fun onLocation(loc: Location) {
        origin = loc.latitude to loc.longitude
        load()
    }

    /**
     * Asks OpenStreetMap straight from the phone AND your server at the same time; the first good answer wins.
     * (Shared cloud servers are often throttled by the public OpenStreetMap servers; a phone usually is not.)
     */
    private suspend fun fetchFirst(lat: Double, lng: Double, radius: Int): Pair<NearbyResult?, List<String>> {
        val ch = Channel<Pair<String, Result<NearbyResult>>>(Channel.UNLIMITED)
        val jobs = listOf(
            viewModelScope.launch { ch.send("OpenStreetMap" to runCatching { OverpassClient.nearby(lat, lng, radius) }) },
            viewModelScope.launch { ch.send("server" to runCatching { withContext(Dispatchers.IO) { ApiClient(apiUrl).nearby(lat, lng, radius) } }) },
        )
        val problems = mutableListOf<String>()
        repeat(2) {
            val (source, result) = ch.receive()
            result.onSuccess { value -> jobs.forEach { j -> j.cancel() }; return value to emptyList() }
            result.onFailure { e -> problems += "$source: ${friendly(e)}" }
        }
        return null to problems
    }

    fun load() {
        val (lat, lng) = origin ?: return
        val radius = radiusM
        val mySeq = ++loadSeq
        viewModelScope.launch {
            loading = true; error = null; notice = null
            val outcome = ProgressiveLoad.run(
                radius,
                fetch = { r -> fetchFirst(lat, lng, r) },
                onStep = { r, res, isFinal ->
                    if (mySeq == loadSeq) {
                        places = res.places
                        selected = selected?.let { s -> res.places.firstOrNull { it.id == s.id } }
                        cache.save(cache.key(lat, lng, r), res)
                        notice = if (isFinal) null else "Showing the nearest ${km(r)} while the wider area loads…"
                    }
                },
            )
            if (mySeq != loadSeq) return@launch // a newer request has started; ignore this one
            if (!outcome.complete(radius)) {
                val reached = outcome.reachedRadiusM
                // best saved copy of the full radius (or the largest smaller one) from an earlier visit
                val saved = ProgressiveLoad.steps(radius).reversed().firstNotNullOfOrNull { cache.load(cache.key(lat, lng, it)) }
                val why = outcome.problems.firstOrNull()?.take(140).orEmpty()
                when {
                    reached != null && (saved == null || saved.result.radiusM <= reached) ->
                        notice = "Showing the nearest ${km(reached)}. The wider area couldn't load ($why). Tap the location button to retry."
                    saved != null -> {
                        places = saved.result.places; selected = null
                        notice = "Live data unavailable. Showing results saved ${agoText(System.currentTimeMillis() - saved.savedAt)} (${km(saved.result.radiusM)})."
                    }
                    else -> error = "Couldn't load restaurants. " + outcome.problems.joinToString(" • ")
                }
            }
            loading = false
        }
    }

    private fun km(m: Int) = if (m < 1000) "$m m" else (m / 1000.0).let { if (it == Math.floor(it)) "%.0f km".format(it) else "%.1f km".format(it) }

    private fun friendly(t: Throwable) = when (t) {
        is ApiException -> t.message ?: "Server error"
        is java.net.SocketTimeoutException -> "The server took too long to respond. Try again in a moment."
        is java.io.IOException -> "Can't reach the server at $apiUrl. Check your connection or the server URL in settings."
        else -> t.message ?: "Something went wrong"
    }
}
