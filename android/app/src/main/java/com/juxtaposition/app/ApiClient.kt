package com.juxtaposition.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class ApiException(message: String) : Exception(message)

/** Minimal HTTP client for the backend (blocking: call from Dispatchers.IO). */
class ApiClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    private fun get(path: String): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000 // generous: free hosts cold-start, and OpenStreetMap can be slow
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw ApiException(runCatching { JSONObject(text).getString("error") }.getOrNull() ?: "Server error ($code)")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    fun nearby(lat: Double, lng: Double, radiusM: Int): NearbyResult =
        parseNearby(get("/api/nearby?lat=$lat&lng=$lng&radius=$radiusM")).copy(via = "server")
}
