package com.juxtaposition.app

import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class FeaturesTest {
    private val NOW = LocalDateTime.of(2026, 11, 23, 12, 0) // a Monday, noon
    private val OPEN = "Mo-Su 10:00-23:00"
    private val CLOSED = "Mo-Su 18:00-23:00"
    private val SOON = "Mo-Su 08:00-12:30"   // 30 minutes left at noon

    private fun p(id: String, name: String = id, cuisine: String? = null, km: Double = 0.3, hours: String? = null, type: String = "restaurant",
                  lat: Double = 12.9, lng: Double = 77.6, veg: String? = null) =
        Place(id, name, type, cuisine, lat, lng, km, 0, "N", null, hours, null, null, veg)

    // ---------------- cuisine categories and chips ----------------
    @Test fun categoryComesFromCuisineThenName() {
        assertEquals(CuisineCategory("Biryani", "🍛"), CuisineIcon.category("biryani, andhra"))
        assertEquals(CuisineCategory("Pizza", "🍕"), CuisineIcon.category(null, "Domino's Pizza"))
        assertEquals(CuisineCategory("Burgers", "🍔"), CuisineIcon.category("burger", "Pizza Palace"))   // cuisine wins
        assertNull(CuisineIcon.category(null, "Sri Krishna Bhavan")); assertNull(CuisineIcon.category("substitute", "Subhash"))
        assertEquals("Kebabs & BBQ", CuisineIcon.category("north indian, kebab")!!.label)                 // a dish beats the generic cuisine
    }

    @Test fun chipsListWhatIsNearbyMostCommonFirst() {
        val ps = listOf(p("1", cuisine = "biryani"), p("2", cuisine = "biryani"), p("3", cuisine = "pizza"), p("4", name = "Third Wave Coffee"), p("5", name = "Nobody"))
        assertEquals(listOf(CuisineChip("Biryani", "🍛", 2), CuisineChip("Coffee", "☕", 1), CuisineChip("Pizza", "🍕", 1)), CuisineChips.from(ps))
        assertEquals(2, CuisineChips.from(ps, max = 2).size)
    }

    @Test fun noChipsWhenThereIsNothingToChooseBetween() {
        assertTrue(CuisineChips.from(emptyList()).isEmpty())
        assertTrue(CuisineChips.from(listOf(p("1", cuisine = "biryani"), p("2", cuisine = "biryani"))).isEmpty())   // one category only
        assertTrue(CuisineChips.from(listOf(p("1"), p("2"))).isEmpty())
    }

    @Test fun cuisineFilterKeepsOnlyThatCategory() {
        val ps = listOf(p("a", cuisine = "biryani", km = 0.1), p("b", cuisine = "pizza", km = 0.2), p("c", name = "Hyderabad House", cuisine = "hyderabadi", km = 0.3), p("d", cuisine = "biryani", km = 0.4))
        assertEquals(listOf("a", "d"), PlaceFilters.visible(ps, 1.0, null, "", false, false, NOW, cuisine = "Biryani").map { it.id })
        assertEquals(4, PlaceFilters.visible(ps, 1.0, null, "", false, false, NOW, cuisine = null).size)
        assertEquals(listOf("a"), PlaceFilters.visible(ps, 0.15, null, "", false, false, NOW, cuisine = "Biryani").map { it.id })   // radius still applies
    }

    // ---------------- closing soon ----------------
    @Test fun closingSoonIsExposedToTheScreens() {
        assertTrue((p("x", hours = SOON).hoursStatus(NOW) as HoursStatus.Open).closingSoon)
        assertEquals("Closes in 30 min", (p("x", hours = SOON).hoursStatus(NOW) as HoursStatus.Open).label)
        assertFalse((p("x", hours = OPEN).hoursStatus(NOW) as HoursStatus.Open).closingSoon)
    }

    // ---------------- sorting ----------------
    @Test fun sortModes() {
        val ps = listOf(
            p("closed", name = "Zed", km = 0.1, hours = CLOSED), p("soon", name = "Mango", km = 0.2, hours = SOON),
            p("open-far", name = "alpha", km = 0.9, hours = OPEN), p("unknown", name = "Beta", km = 0.15),
            p("open-near", name = "Gamma", km = 0.3, hours = OPEN),
        )
        assertEquals(listOf("closed", "unknown", "soon", "open-near", "open-far"), PlaceSorting.sort(ps, SortMode.NEAREST, NOW).map { it.id })
        assertEquals(listOf("open-near", "open-far", "soon", "unknown", "closed"), PlaceSorting.sort(ps, SortMode.OPEN_FIRST, NOW).map { it.id })
        assertEquals(listOf("open-far", "unknown", "open-near", "soon", "closed"), PlaceSorting.sort(ps, SortMode.NAME, NOW).map { it.id })   // alpha, Beta, Gamma, Mango, Zed (case-insensitive)
    }

    // ---------------- pick for me ----------------
    @Test fun neverPicksAClosedPlaceOrNothing() {
        val ps = listOf(p("c1", hours = CLOSED), p("c2", hours = CLOSED))
        assertNull(PickForMe.next(ps, emptySet(), Random(1), NOW).first)
        assertNull(PickForMe.next(emptyList(), emptySet(), Random(1), NOW).first)
        for (seed in 0 until 50) assertEquals("ok", PickForMe.next(ps + p("ok", hours = OPEN), emptySet(), Random(seed), NOW).first!!.id)   // the only place that isn't closed
    }

    @Test fun prefersConfirmedOpenThenUnknownAndSkipsAlmostClosed() {
        val ps = listOf(p("open", hours = OPEN), p("unknown1"), p("unknown2"), p("soon", hours = SOON), p("closed", hours = CLOSED))
        for (seed in 0 until 30) assertEquals("open", PickForMe.next(ps, emptySet(), Random(seed), NOW).first!!.id)          // the verified one first
        val (second, h2) = PickForMe.next(ps, setOf("open"), Random(3), NOW)
        assertTrue(second!!.id in setOf("unknown1", "unknown2", "soon")); assertEquals(setOf("open", second.id), h2)
    }

    @Test fun noRepeatsUntilEverythingHasBeenOffered() {
        val ps = (1..5).map { p("p$it", hours = OPEN) }
        var history = emptySet<String>(); val seen = mutableListOf<String>()
        val random = Random(42)
        repeat(5) { val (pick, h) = PickForMe.next(ps, history, random, NOW); seen += pick!!.id; history = h }
        assertEquals(5, seen.toSet().size)                                 // five different ones in a row
        val (again, h) = PickForMe.next(ps, history, random, NOW)          // all offered: starts over
        assertNotNull(again); assertEquals(setOf(again!!.id), h)
    }

    // ---------------- favourites ----------------
    @Test fun toggleAddsAtTheFrontAndRemoves() {
        val a = p("a"); val b = p("b")
        val one = Favorites.toggle(emptyList(), a)
        assertEquals(listOf("a"), one.map { it.id })
        assertEquals(listOf("b", "a"), Favorites.toggle(one, b).map { it.id })
        assertEquals(listOf("b"), Favorites.toggle(listOf(b, a), a).map { it.id })
        assertTrue(Favorites.toggle(listOf(a), a).isEmpty())
    }

    @Test fun favouritesAreCapped() {
        var saved = emptyList<Place>()
        for (i in 1..(Favorites.MAX + 25)) saved = Favorites.toggle(saved, p("p$i"))
        assertEquals(Favorites.MAX, saved.size); assertEquals("p${Favorites.MAX + 25}", saved.first().id)   // newest kept
    }

    @Test fun mergePrefersTheFreshCopy() {
        val stale = p("a", hours = CLOSED); val fresh = p("a", hours = OPEN, km = 0.2); val only = p("far", km = 25.0)
        val merged = Favorites.merge(listOf(stale, only), listOf(fresh, p("other")))
        assertEquals(OPEN, merged[0].openingHours); assertEquals("far", merged[1].id); assertEquals(2, merged.size)
    }

    @Test fun favouritesSurviveStorage() {
        val saved = listOf(p("osm:node/1", name = "Truffles", cuisine = "burger", hours = OPEN, veg = "yes"), p("osm:way/2", name = "Far Away", km = 40.0, lat = 13.5, lng = 78.1))
        assertEquals(saved, parseNearby(PlacesCache.toJson(NearbyResult(0, saved))).places)
    }
}
