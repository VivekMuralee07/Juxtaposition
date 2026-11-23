package com.juxtaposition.app

import java.time.LocalDateTime

/** Everything the screens show is derived from ONE fetched list, so changing a chip never needs the network. */
object PlaceFilters {
    /** Re-measure distance, bearing and direction from the user's current position (places keep their own lat/lng). */
    fun relocate(places: List<Place>, lat: Double, lng: Double): List<Place> = places.map { p ->
        val km = Geo.distanceKm(lat, lng, p.lat, p.lng)
        val bearing = Geo.bearingDeg(lat, lng, p.lat, p.lng)
        p.copy(distanceKm = Math.round(km * 100) / 100.0, bearingDeg = bearing, direction = Geo.compass(bearing))
    }

    fun visible(
        places: List<Place>, radiusKm: Double, type: String?, query: String, vegOnly: Boolean, openNow: Boolean,
        now: LocalDateTime = LocalDateTime.now(), cuisine: String? = null,
    ): List<Place> {
        val q = query.trim().lowercase()
        return places.filter { p ->
            p.distanceKm <= radiusKm &&
                (type == null || p.type == type) &&
                (cuisine == null || p.category()?.label == cuisine) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.cuisine?.lowercase()?.contains(q) == true) &&
                (!vegOnly || p.vegetarian != null) &&
                // most OSM places list no hours, so "open now" only hides places KNOWN to be closed
                (!openNow || p.hoursStatus(now) !is HoursStatus.Closed)
        }.sortedBy { it.distanceKm }
    }
}

enum class SortMode(val label: String) { NEAREST("Nearest"), OPEN_FIRST("Open first"), NAME("A–Z") }

object PlaceSorting {
    /** NEAREST is the list's natural (banded) order; the other modes give a flat list. */
    fun sort(places: List<Place>, mode: SortMode, now: LocalDateTime = LocalDateTime.now()): List<Place> = when (mode) {
        SortMode.NEAREST -> places.sortedBy { it.distanceKm }
        SortMode.NAME -> places.sortedWith(compareBy({ it.name.lowercase() }, { it.distanceKm }))
        SortMode.OPEN_FIRST -> places.sortedWith(compareBy({ rank(it.hoursStatus(now)) }, { it.distanceKm }))
    }

    // open (soonest closing last) -> hours unknown -> closed
    private fun rank(s: HoursStatus) = when (s) {
        is HoursStatus.Open -> if (s.closingSoon) 1 else 0
        HoursStatus.Unknown -> 2
        is HoursStatus.Closed -> 3
    }
}

/** "Pick for me": one suggestion at a time, never a place known to be closed, and no repeats until everything has been offered. */
object PickForMe {
    fun next(visible: List<Place>, history: Set<String>, random: kotlin.random.Random, now: LocalDateTime = LocalDateTime.now()): Pair<Place?, Set<String>> {
        val candidates = visible.filter { it.hoursStatus(now) !is HoursStatus.Closed }
        if (candidates.isEmpty()) return null to history
        var pool = candidates.filter { it.id !in history }
        var hist = history
        if (pool.isEmpty()) { pool = candidates; hist = emptySet() } // everything has been offered: start over
        // places confirmed open (and not about to close) come before ones with unknown hours
        val verified = pool.filter { (it.hoursStatus(now) as? HoursStatus.Open)?.closingSoon == false }
        val pick = (verified.ifEmpty { pool }).let { it[random.nextInt(it.size)] }
        return pick to (hist + pick.id)
    }
}

/** Saved places. They are stored in full (not just ids) so they still show when they are far away or not in the current list. */
object Favorites {
    const val MAX = 200

    /** Prefer the freshly fetched copy of a saved place (its hours may have changed); keep the saved copy otherwise. */
    fun merge(saved: List<Place>, fresh: List<Place>): List<Place> = saved.map { s -> fresh.firstOrNull { it.id == s.id } ?: s }

    fun toggle(saved: List<Place>, place: Place): List<Place> =
        if (saved.any { it.id == place.id }) saved.filterNot { it.id == place.id } else (listOf(place) + saved).take(MAX)
}

/** Rules for following the user as they move. */
object LiveUpdates {
    /** The list is fetched for a 1 km circle; beyond this the circle around the user is no longer fully covered. */
    const val MAX_RADIUS_M = 1000
    const val REFETCH_DISTANCE_M = 200.0
    const val MIN_REFETCH_INTERVAL_MS = 20_000L
    const val MAX_ACCURACY_M = 150f

    /** Ignore very rough fixes (network location can be off by kilometres): they would make places jump around. */
    fun accurateEnough(accuracyM: Float?) = accuracyM == null || accuracyM <= MAX_ACCURACY_M

    fun shouldRefetch(
        fetchedAt: Pair<Double, Double>?, now: Pair<Double, Double>, lastAttemptMs: Long, nowMs: Long, busy: Boolean,
    ): Boolean {
        if (busy || nowMs - lastAttemptMs < MIN_REFETCH_INTERVAL_MS) return false
        if (fetchedAt == null) return true
        return Geo.distanceKm(fetchedAt.first, fetchedAt.second, now.first, now.second) * 1000 > REFETCH_DISTANCE_M
    }
}
