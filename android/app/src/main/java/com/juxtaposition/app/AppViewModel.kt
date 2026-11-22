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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ViewMode { LIST, RADAR_2D, RADAR_3D }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiUrl by mutableStateOf(prefs.getString("apiUrl", BuildConfig.DEFAULT_API_URL)!!); private set
    var origin by mutableStateOf<Pair<Double, Double>?>(null); private set
    var radiusM by mutableIntStateOf(1000); private set
    var typeFilter by mutableStateOf<String?>(null); private set
    var places by mutableStateOf<List<Place>>(emptyList()); private set
    var selected by mutableStateOf<Place?>(null); private set
    var viewMode by mutableStateOf(ViewMode.RADAR_2D); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var locationDenied by mutableStateOf(false); private set

    val visiblePlaces get() = places.filter { typeFilter == null || it.type == typeFilter }

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

    fun load() {
        val (lat, lng) = origin ?: return
        viewModelScope.launch {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { ApiClient(apiUrl).nearby(lat, lng, radiusM) } }
                .onSuccess { places = it.places; selected = null }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    private fun friendly(t: Throwable) = when (t) {
        is ApiException -> t.message ?: "Server error"
        is java.net.SocketTimeoutException -> "The server took too long to respond. Try again in a moment."
        is java.io.IOException -> "Can't reach the server at $apiUrl. Check your connection or the server URL in settings."
        else -> t.message ?: "Something went wrong"
    }
}
