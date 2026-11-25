package com.juxtaposition.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

data class GeoResult(val label: String, val lat: Double, val lng: Double)

/** Finds an area by name with OpenStreetMap's Nominatim. Only the typed text is sent, never where you are. */
object Geocoder {
    fun parse(json: String): List<GeoResult> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
            val name = o.optString("display_name").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(", ")
            if (name.isEmpty()) null else GeoResult(name, lat, lng)
        }
    }.getOrDefault(emptyList())

    fun url(query: String) =
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=" + URLEncoder.encode(query.trim(), "UTF-8")

    /** Blocking; call off the main thread. Throws on network errors. */
    fun search(query: String): List<GeoResult> {
        val conn = (URL(url(query)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "NearbyEats-Android") // Nominatim's usage policy requires one
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
}
