package com.juxtaposition.app

/** A spoken summary of the radar for screen readers (TalkBack can't read a drawing). */
object RadarSummary {
    private val DIRECTIONS = mapOf(
        "N" to "north", "NE" to "north-east", "E" to "east", "SE" to "south-east",
        "S" to "south", "SW" to "south-west", "W" to "west", "NW" to "north-west",
    )

    fun distance(km: Double): String = if (km < 1.0) {
        val m = Math.max(10, (Math.round(km * 100) * 10).toInt()) // nearest 10 m
        "$m metres"
    } else (km).let { if (Math.abs(it - Math.round(it)) < 0.05) "${Math.round(it)} ${if (Math.round(it) == 1L) "kilometre" else "kilometres"}" else "%.1f kilometres".format(it) }

    fun describe(places: List<Place>, visibleRadiusM: Double, maxNamed: Int = 4): String {
        val inView = places.filter { it.distanceKm * 1000 <= visibleRadiusM }.sortedBy { it.distanceKm }
        val radius = distance(visibleRadiusM / 1000)
        if (inView.isEmpty()) return "Radar view. No places within $radius. Switch to the list view for more options."
        val count = "${inView.size} ${if (inView.size == 1) "place" else "places"}"
        val named = inView.take(maxNamed).joinToString(". ") {
            "${it.name}, ${distance(it.distanceKm)} ${DIRECTIONS[it.direction] ?: it.direction}"
        }
        val more = if (inView.size > maxNamed) " And ${inView.size - maxNamed} more." else ""
        return "Radar view. $count within $radius. Nearest: $named.$more Switch to the list view to browse every place."
    }
}
