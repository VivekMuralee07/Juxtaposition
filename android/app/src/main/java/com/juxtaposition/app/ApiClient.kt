package com.juxtaposition.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class ApiException(message: String) : Exception(message)

/** Minimal HTTP client for the Juxtaposition backend (blocking: call from Dispatchers.IO). */
class ApiClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    // Render's starter plan keeps the service awake, but free instances cold-start (~50s), so be generous.
    private fun request(path: String, body: String? = null): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getString("error") }.getOrNull()
                throw ApiException(msg ?: "Server error ($code)")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun loc(lat: Double?, lng: Double?) = if (lat != null && lng != null) "&lat=$lat&lng=$lng" else ""

    fun restaurants(query: String, lat: Double?, lng: Double?): Pair<List<Restaurant>, String?> {
        val body = request("/api/restaurants?q=${URLEncoder.encode(query, "UTF-8")}${loc(lat, lng)}")
        return parseRestaurants(body) to parseLocationNote(body)
    }

    fun menu(restaurantId: String): List<MenuItem> =
        parseMenu(request("/api/restaurants/${URLEncoder.encode(restaurantId, "UTF-8")}/menu"))

    fun compare(restaurantId: String, cart: Map<String, Int>, lat: Double?, lng: Double?): Comparison {
        val items = JSONArray()
        cart.forEach { (id, qty) -> items.put(JSONObject().put("menuItemId", id).put("qty", qty)) }
        val body = JSONObject().put("restaurantId", restaurantId).put("items", items)
        if (lat != null && lng != null) body.put("lat", lat).put("lng", lng)
        return parseComparison(request("/api/compare", body.toString()))
    }
}
