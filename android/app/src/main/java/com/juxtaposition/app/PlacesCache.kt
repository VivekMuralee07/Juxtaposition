package com.juxtaposition.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the last good list for each (rounded) area so a busy moment shows saved results, not nothing. */
class PlacesCache(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    data class Hit(val result: NearbyResult, val savedAt: Long)

    fun key(lat: Double, lng: Double, radiusM: Int) = "%.3f,%.3f|%d".format(lat, lng, radiusM)

    fun save(key: String, result: NearbyResult) {
        val order = keys().filter { it != key } + key
        val keep = order.takeLast(MAX_ENTRIES)
        val e = prefs.edit()
        order.dropLast(MAX_ENTRIES).forEach { e.remove(ENTRY + it) }
        e.putString(ENTRY + key, JSONObject().put("at", now()).put("data", toJson(result)).toString())
        e.putString(KEYS, JSONArray(keep).toString())
        e.apply()
    }

    fun load(key: String, maxAgeMs: Long = 24 * 60 * 60_000L): Hit? {
        val raw = prefs.getString(ENTRY + key, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val at = o.getLong("at")
            if (now() - at > maxAgeMs) null else Hit(parseNearby(o.getString("data")), at)
        }.getOrNull()
    }

    private fun keys(): List<String> = runCatching {
        val a = JSONArray(prefs.getString(KEYS, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEYS = "cache_keys"
        private const val ENTRY = "cache_"
        private const val MAX_ENTRIES = 8

        fun toJson(r: NearbyResult): String = JSONObject()
            .put("radiusM", r.radiusM)
            .put("places", JSONArray().also { arr ->
                r.places.forEach { p ->
                    arr.put(JSONObject()
                        .put("id", p.id).put("name", p.name).put("type", p.type).put("cuisine", p.cuisine ?: JSONObject.NULL)
                        .put("lat", p.lat).put("lng", p.lng).put("distanceKm", p.distanceKm).put("bearingDeg", p.bearingDeg)
                        .put("direction", p.direction).put("address", p.address ?: JSONObject.NULL)
                        .put("openingHours", p.openingHours ?: JSONObject.NULL).put("phone", p.phone ?: JSONObject.NULL)
                        .put("website", p.website ?: JSONObject.NULL).put("vegetarian", p.vegetarian ?: JSONObject.NULL))
                }
            }).toString()
    }
}

fun agoText(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} d ago"
    }
}
