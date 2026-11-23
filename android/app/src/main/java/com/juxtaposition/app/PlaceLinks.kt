package com.juxtaposition.app

import java.net.URLEncoder
import java.util.Locale

/** Links and text for a place: walking directions, sharing, and the place's OpenStreetMap page. Pure strings, so it is testable. */
object PlaceLinks {
    private fun coord(x: Double) = String.format(Locale.US, "%.6f", x)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun ll(p: Place) = "${coord(p.lat)},${coord(p.lng)}"

    /** Starts walking navigation in Google Maps. Tried first; other map apps do not handle it. */
    fun walkingNavigation(p: Place) = "google.navigation:q=${ll(p)}&mode=w"
    /** Any map app can show this pin; the label is the place's name. */
    fun mapPin(p: Place) = "geo:${ll(p)}?q=${ll(p)}(${enc(p.name)})"
    /** Last resort: opens in the browser if there is no map app at all. */
    fun mapsWebDirections(p: Place) = "https://www.google.com/maps/dir/?api=1&destination=${ll(p)}&travelmode=walking"
    fun mapsWebPlace(p: Place) = "https://www.google.com/maps/search/?api=1&query=${ll(p)}"

    /** "osm:node/123" -> ("node", 123), or null for anything else. */
    fun osmRef(p: Place): Pair<String, Long>? =
        Regex("^osm:(node|way|relation)/(\\d+)$").matchEntire(p.id)?.let { it.groupValues[1] to it.groupValues[2].toLong() }

    fun osmPage(p: Place) = osmRef(p)?.let { (t, id) -> "https://www.openstreetmap.org/$t/$id" }
    /** Opens OpenStreetMap's editor on the place (the person needs a free OpenStreetMap account to save changes). */
    fun osmEdit(p: Place) = osmRef(p)?.let { (t, id) -> "https://www.openstreetmap.org/edit?$t=$id" }

    /** What gets shared. It deliberately says nothing about where the SENDER is. */
    fun shareText(p: Place): String = listOfNotNull(
        p.name,
        listOfNotNull(p.typeLabel, p.cuisine).joinToString(" • "),
        p.address,
        p.openingHours?.let { "Hours: $it" },
        p.phone?.let { "Phone: $it" },
        p.website?.let { "Website: $it" },
        mapsWebPlace(p),
    ).filter { it.isNotBlank() }.joinToString("\n")
}
