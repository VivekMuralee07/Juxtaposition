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
        now: LocalDateTime = LocalDateTime.now(),
    ): List<Place> {
        val q = query.trim().lowercase()
        return places.filter { p ->
            p.distanceKm <= radiusKm &&
                (type == null || p.type == type) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.cuisine?.lowercase()?.contains(q) == true) &&
                (!vegOnly || p.vegetarian != null) &&
                // most OSM places list no hours, so "open now" only hides places KNOWN to be closed
                (!openNow || p.hoursStatus(now) !is HoursStatus.Closed)
        }.sortedBy { it.distanceKm }
    }
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
