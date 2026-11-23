package com.juxtaposition.app

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class PlaceFiltersTest {
    private val NOW = LocalDateTime.of(2026, 11, 23, 12, 0) // a Monday, noon
    private fun p(id: String, lat: Double, lng: Double, type: String = "restaurant", name: String = id, cuisine: String? = null,
                  veg: String? = null, hours: String? = null, km: Double = 0.0) =
        Place(id, name, type, cuisine, lat, lng, km, 0, "N", null, hours, null, null, veg)

    // ~111 m per 0.001 degree of latitude
    private val ME = 12.9000 to 77.6000
    private val places = listOf(
        p("near", 12.9020, 77.6000, name = "Near Cafe", type = "cafe"),            // ~222 m north
        p("mid", 12.9000, 77.6070, name = "Mid Biryani", cuisine = "biryani", veg = "yes"), // ~760 m east
        p("far", 12.9100, 77.6000, name = "Far Pizza", type = "fast_food"),         // ~1.1 km north
    )

    @Test fun relocateRemeasuresFromTheNewPosition() {
        val a = PlaceFilters.relocate(places, ME.first, ME.second).associateBy { it.id }
        assertEquals(0.22, a.getValue("near").distanceKm, 0.02); assertEquals("N", a.getValue("near").direction)
        assertEquals("E", a.getValue("mid").direction)
        // walk 300 m north: "near" is now behind-ish/very close, "far" much closer
        val b = PlaceFilters.relocate(places, 12.9027, 77.6000).associateBy { it.id }
        assertTrue(b.getValue("far").distanceKm < a.getValue("far").distanceKm)
        assertEquals("S", b.getValue("near").direction)   // we have walked past it
        assertTrue(b.getValue("near").distanceKm < 0.1)
    }

    @Test fun radiusIsAFilterOverTheSameList() {
        val all = PlaceFilters.relocate(places, ME.first, ME.second)
        fun ids(km: Double) = PlaceFilters.visible(all, km, null, "", false, false, NOW).map { it.id }
        assertEquals(listOf("near"), ids(0.5))
        assertEquals(listOf("near", "mid"), ids(1.0))        // far is ~1.1 km away
        assertEquals(listOf("near", "mid", "far"), ids(2.0))
    }

    @Test fun otherFiltersCombineAndResultsAreSortedByDistance() {
        val all = PlaceFilters.relocate(places, ME.first, ME.second)
        assertEquals(listOf("mid"), PlaceFilters.visible(all, 2.0, null, "", true, false, NOW).map { it.id })          // veg only
        assertEquals(listOf("near"), PlaceFilters.visible(all, 2.0, "cafe", "", false, false, NOW).map { it.id })      // type
        assertEquals(listOf("mid"), PlaceFilters.visible(all, 2.0, null, "  BIRYANI ", false, false, NOW).map { it.id }) // search, case/space-insensitive
        assertEquals(listOf("far"), PlaceFilters.visible(all, 2.0, null, "pizza", false, false, NOW).map { it.id })
        assertEquals(listOf("near", "mid", "far"), PlaceFilters.visible(all.reversed(), 2.0, null, "", false, false, NOW).map { it.id })
    }

    @Test fun openNowHidesOnlyPlacesKnownToBeClosed() {
        val withHours = listOf(
            p("open", 12.9, 77.6, hours = "Mo-Su 10:00-23:00", km = 0.1),
            p("closed", 12.9, 77.6, hours = "Mo-Su 18:00-23:00", km = 0.2),
            p("unknown", 12.9, 77.6, km = 0.3),
        )
        assertEquals(listOf("open", "unknown"), PlaceFilters.visible(withHours, 1.0, null, "", false, true, NOW).map { it.id })
        assertEquals(3, PlaceFilters.visible(withHours, 1.0, null, "", false, false, NOW).size)
    }

    @Test fun refetchOnlyAfterRealMovementAndNotTooOften() {
        val here = 12.9000 to 77.6000
        val t0 = 1_000_000L
        val moved150m = 12.90135 to 77.6000
        val moved300m = 12.90270 to 77.6000
        assertTrue(LiveUpdates.shouldRefetch(null, here, 0, t0, false))                   // first fix
        assertFalse(LiveUpdates.shouldRefetch(here, here, 0, t0, false))                  // not moved
        assertFalse(LiveUpdates.shouldRefetch(here, moved150m, 0, t0, false))             // small move: just re-measure
        assertTrue(LiveUpdates.shouldRefetch(here, moved300m, 0, t0, false))              // moved > 200 m
        assertFalse(LiveUpdates.shouldRefetch(here, moved300m, t0 - 5_000, t0, false))    // too soon after the last try
        assertTrue(LiveUpdates.shouldRefetch(here, moved300m, t0 - 25_000, t0, false))
        assertFalse(LiveUpdates.shouldRefetch(here, moved300m, 0, t0, true))              // already fetching
        assertFalse(LiveUpdates.shouldRefetch(null, here, t0 - 1_000, t0, false))         // failed first fetch: wait before retrying
    }

    @Test fun roughLocationFixesAreIgnored() {
        assertTrue(LiveUpdates.accurateEnough(20f)); assertTrue(LiveUpdates.accurateEnough(null))
        assertTrue(LiveUpdates.accurateEnough(150f)); assertFalse(LiveUpdates.accurateEnough(1500f))
    }
}
