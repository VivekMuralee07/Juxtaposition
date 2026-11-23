package com.juxtaposition.app

import org.json.JSONArray
import org.json.JSONObject

data class Place(
    val id: String, val name: String, val type: String, val cuisine: String?,
    val lat: Double, val lng: Double, val distanceKm: Double, val bearingDeg: Int, val direction: String,
    val address: String?, val openingHours: String?, val phone: String?, val website: String?,
    val vegetarian: String?,
) {
    val typeLabel get() = when (type) { "fast_food" -> "Fast food"; "cafe" -> "Cafe"; else -> "Restaurant" }
    /** Rough walking time: straight-line distance x1.3 for streets, at 5 km/h. */
    val walkMinutes get() = Walking.minutes(distanceKm)
}

/** `via` = which source answered: an OpenStreetMap mirror's host name (asked from the phone) or "server". Null for saved copies. */
data class NearbyResult(val radiusM: Int, val places: List<Place>, val via: String? = null)

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

fun parseNearby(json: String): NearbyResult {
    val root = JSONObject(json)
    return NearbyResult(
        root.optInt("radiusM"),
        root.getJSONArray("places").objects().map {
            Place(
                id = it.getString("id"), name = it.getString("name"), type = it.optString("type", "restaurant"),
                cuisine = it.str("cuisine"), lat = it.getDouble("lat"), lng = it.getDouble("lng"),
                distanceKm = it.getDouble("distanceKm"), bearingDeg = it.optInt("bearingDeg"),
                direction = it.optString("direction"), address = it.str("address"),
                openingHours = it.str("openingHours"), phone = it.str("phone"), website = it.str("website"),
                vegetarian = it.str("vegetarian"),
            )
        },
    )
}
