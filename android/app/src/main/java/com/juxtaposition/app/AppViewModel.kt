package com.juxtaposition.app

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { SEARCH, MENU, RESULT }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiUrl by mutableStateOf(prefs.getString("apiUrl", BuildConfig.DEFAULT_API_URL)!!)
        private set
    var screen by mutableStateOf(Screen.SEARCH); private set
    var query by mutableStateOf(""); private set
    var restaurants by mutableStateOf<List<Restaurant>>(emptyList()); private set
    var selected by mutableStateOf<Restaurant?>(null); private set
    var menu by mutableStateOf<List<MenuItem>>(emptyList()); private set
    var cart by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var comparison by mutableStateOf<Comparison?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var usingDeviceLocation by mutableStateOf(false); private set
    var locationNote by mutableStateOf<String?>(null); private set

    private var lat: Double? = null
    private var lng: Double? = null
    private var searchJob: Job? = null
    private val api get() = ApiClient(apiUrl)

    init { search("") }

    fun saveApiUrl(url: String) {
        apiUrl = url.trim().ifEmpty { BuildConfig.DEFAULT_API_URL }
        prefs.edit().putString("apiUrl", apiUrl).apply()
        search(query)
    }

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); search(q) }
    }

    @SuppressLint("MissingPermission")
    fun useDeviceLocation() {
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (best == null) {
            error = "Couldn't get your location. Turn on GPS and try again."
        } else {
            lat = best.latitude; lng = best.longitude; usingDeviceLocation = true
            search(query)
        }
    }

    fun search(q: String) {
        viewModelScope.launch {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { api.restaurants(q, lat, lng) } }
                .onSuccess { restaurants = it.first; locationNote = it.second }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    fun openRestaurant(r: Restaurant) {
        selected = r; cart = emptyMap(); menu = emptyList(); screen = Screen.MENU
        viewModelScope.launch {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { api.menu(r.id) } }
                .onSuccess { menu = it }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    fun changeQty(itemId: String, delta: Int) {
        val q = ((cart[itemId] ?: 0) + delta).coerceIn(0, 20)
        cart = if (q == 0) cart - itemId else cart + (itemId to q)
    }

    fun compare() {
        val r = selected ?: return
        if (cart.isEmpty()) return
        viewModelScope.launch {
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { api.compare(r.id, cart, lat, lng) } }
                .onSuccess { comparison = it; screen = Screen.RESULT }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    fun back(): Boolean = when (screen) {
        Screen.RESULT -> { screen = Screen.MENU; error = null; true }
        Screen.MENU -> { screen = Screen.SEARCH; error = null; true }
        Screen.SEARCH -> false
    }

    fun dismissError() { error = null }

    private fun friendly(t: Throwable) = when (t) {
        is ApiException -> t.message ?: "Server error"
        is java.net.SocketTimeoutException -> "The server took too long to respond. If it was asleep, try again in a moment."
        is java.io.IOException -> "Can't reach the server at $apiUrl. Check your connection or the server URL in settings."
        else -> t.message ?: "Something went wrong"
    }
}
