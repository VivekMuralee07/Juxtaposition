package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class VisualLogicTest {
    @Test fun cuisineGivesAFoodSpecificEmoji() {
        assertEquals("🍛", CuisineIcon.emoji("restaurant", "biryani, andhra"))
        assertEquals("🍛", CuisineIcon.emoji("restaurant", "hyderabadi"))
        assertEquals("🥞", CuisineIcon.emoji("restaurant", "south indian"))      // before the generic "indian"
        assertEquals("🍢", CuisineIcon.emoji("restaurant", "north indian, kebab")) // a specific dish beats the generic cuisine
        assertEquals("🍛", CuisineIcon.emoji("restaurant", "north indian"))
        assertEquals("🍕", CuisineIcon.emoji("fast_food", "pizza"))
        assertEquals("🍔", CuisineIcon.emoji("fast_food", "burger"))
        assertEquals("🥟", CuisineIcon.emoji("restaurant", "momo"))
        assertEquals("🍜", CuisineIcon.emoji("restaurant", "chinese"))
        assertEquals("☕", CuisineIcon.emoji("cafe", "coffee shop"))
        assertEquals("🍵", CuisineIcon.emoji("cafe", "tea, snacks"))
        assertEquals("🍰", CuisineIcon.emoji("restaurant", "bakery"))
        assertEquals("🍦", CuisineIcon.emoji("restaurant", "ice cream"))
    }

    @Test fun nameIsUsedWhenThereIsNoCuisine() {
        assertEquals("🍕", CuisineIcon.emoji("restaurant", null, "Domino's Pizza"))
        assertEquals("🍛", CuisineIcon.emoji("restaurant", null, "Meghana Biryani House"))
        assertEquals("☕", CuisineIcon.emoji("restaurant", null, "Third Wave Coffee"))
        // cuisine wins over the name
        assertEquals("🍔", CuisineIcon.emoji("restaurant", "burger", "Pizza Palace"))
    }

    @Test fun fallsBackToTheTypeAndDoesNotMatchInsideWords() {
        assertEquals("🍽️", CuisineIcon.emoji("restaurant", null, "Sri Krishna Bhavan"))
        assertEquals("🍔", CuisineIcon.emoji("fast_food", null, "Quick Bites"))
        assertEquals("☕", CuisineIcon.emoji("cafe", null, "The Corner"))
        assertEquals("🍽️", CuisineIcon.emoji("restaurant", "substitute", "Subhash"))   // "sub" must not match "substitute"/"Subhash"
        assertEquals("🍽️", CuisineIcon.emoji("restaurant", "", null))
    }

    @Test fun walkingTimeIsConsistent() {
        assertEquals(1, Walking.minutes(0.01))
        assertEquals(16, Walking.minutes(1.0))                   // 1 km * 1.3 / 5 km/h = 15.6 min
        assertEquals(0.3205, Walking.kmForMinutes(5.0), 1e-3)           // 5 min on foot = 5/60 h * 5 km/h / 1.3 detour
        assertEquals(Walking.minutesExact(Walking.kmForMinutes(10.0)), 10.0, 1e-9)
    }

    @Test fun ringsAreInWalkingMinutesWithALabelledBoundary() {
        val one = RingScale.rings(1000.0)
        assertEquals(listOf("5 min", "10 min", "1 km · 16 min"), one.map { it.label })
        assertEquals(listOf(false, false, true), one.map { it.isBoundary })
        assertEquals(1.0, one.last().distanceKm, 1e-9)
        assertTrue(one[0].distanceKm < one[1].distanceKm && one[1].distanceKm < one.last().distanceKm)
        assertEquals(listOf("2 min", "4 min", "6 min", "500 m · 8 min"), RingScale.rings(500.0).map { it.label })
    }

    @Test fun ringsAdaptWhenZoomedIn() {
        assertEquals(listOf("1 min", "2 min", "3 min", "250 m · 4 min"), RingScale.rings(250.0).map { it.label })
        // smallest view the app can show is 500 m / 6x zoom = about 83 m
        assertEquals(RadarZoom.visibleRadiusM(500, RadarZoom.MAX), 83.33, 0.01)
        // never an absurd number of rings, and always exactly one boundary at the end
        for (r in listOf(83.0, 150.0, 400.0, 1000.0, 2500.0)) {
            val rings = RingScale.rings(r)
            assertTrue("$r m -> ${rings.size} rings", rings.size in 2..6)
            assertEquals(1, rings.count { it.isBoundary }); assertTrue(rings.last().isBoundary)
        }
    }

    @Test fun absurdlyTinyViewStillWorks() {
        val rings = RingScale.rings(60.0)                       // below anything the zoom limit allows
        assertEquals(listOf("60 m · 1 min"), rings.map { it.label })
        assertTrue(rings.single().isBoundary)
    }

    @Test fun distanceLabelsReadNaturally() {
        assertEquals("420 m", RingScale.distanceLabel(416.7))
        assertEquals("1 km", RingScale.distanceLabel(1000.0))
        assertEquals("1.5 km", RingScale.distanceLabel(1500.0))
    }

    @Test fun zoomIsClampedAndDoubleTapToggles() {
        assertEquals(1f, RadarZoom.apply(1f, 0.5f), 0f)             // can't zoom out past the full view
        assertEquals(6f, RadarZoom.apply(4f, 3f), 0f)               // or in past 6x
        assertEquals(2f, RadarZoom.apply(1f, 2f), 0f)
        assertEquals(RadarZoom.DOUBLE_TAP, RadarZoom.toggle(1f), 0f)
        assertEquals(1f, RadarZoom.toggle(3f), 0f)
        assertEquals(400.0, RadarZoom.visibleRadiusM(1000, 2.5f), 1e-6)
    }

    @Test fun shortInfoLine() {
        val now = 10_000_000L
        assertEquals("Updated 3 min ago • GPS ±12 m", DataLine.short(DataInfo("overpass-api.de", 2100, now - 180_000, false), "GPS ±12 m", now))
        assertEquals("Saved copy from 2 h ago", DataLine.short(DataInfo(null, null, now - 7_200_000L, true), null, now))
        assertEquals("GPS ±5 m", DataLine.short(null, "GPS ±5 m", now))
        assertNull(DataLine.short(null, null, now))
    }
}
