package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class NearbyTest {
    private val json = """
    {"origin":{"lat":12.97,"lng":77.59},"radiusM":1000,"source":"OpenStreetMap","places":[
     {"id":"osm:node/1","name":"North Cafe","type":"cafe","cuisine":"coffee shop","lat":12.98,"lng":77.59,"distanceKm":1.1,"bearingDeg":0,"direction":"N",
      "address":"MG Road","openingHours":null,"phone":"+91 80 1","website":null,"vegetarian":null},
     {"id":"osm:way/2","name":"East Biryani","type":"restaurant","cuisine":null,"lat":12.97,"lng":77.6,"distanceKm":0.12,"bearingDeg":90,"direction":"E",
      "address":null,"openingHours":"Mo-Su 10:00-23:00","phone":null,"website":null,"vegetarian":"yes"}]}
    """.trimIndent()

    @Test fun parsesPlaces() {
        val r = parseNearby(json)
        assertEquals(1000, r.radiusM)
        assertEquals(2, r.places.size)
        val cafe = r.places[0]
        assertEquals("Cafe", cafe.typeLabel); assertEquals("coffee shop", cafe.cuisine); assertNull(cafe.website)
        assertEquals("Restaurant", r.places[1].typeLabel); assertNull(r.places[1].cuisine)
        assertEquals("Mo-Su 10:00-23:00", r.places[1].openingHours)
    }

    @Test fun walkMinutesHasFloorOfOne() {
        val p = parseNearby(json).places
        assertEquals(2, p[1].walkMinutes)       // 120 m * 1.3 / 5 km/h = 1.87 min
        assertEquals(1, p[1].copy(distanceKm = 0.01).walkMinutes) // never below 1
        assertEquals(17, p[0].walkMinutes)      // 1.1 km * 1.3 / 5 km/h
    }

    @Test fun planePositionFollowsCompass() {
        val (x, y) = RadarMath.toPlane(90, 2.0)
        assertEquals(2.0, x, 1e-9); assertEquals(0.0, y, 1e-9)   // east
        val (nx, ny) = RadarMath.toPlane(0, 1.0)
        assertEquals(0.0, nx, 1e-9); assertEquals(1.0, ny, 1e-9) // north
    }

    @Test fun projectionTopDownAndTilted() {
        // top-down: north is straight up, 100 px per km
        val up = RadarMath.project(0.0, 1.0, 0.0, 0.0, 100.0, 500f, 500f)
        assertEquals(500f, up.x, 0.01f); assertEquals(400f, up.y, 0.01f)
        // pin height has no effect when viewed top-down
        assertEquals(400f, RadarMath.project(0.0, 1.0, 0.0, 0.0, 100.0, 500f, 500f, 30.0).y, 0.01f)
        // tilted: ground squashed vertically and pin lifted
        val flat = RadarMath.project(0.0, 1.0, 0.0, 1.0, 100.0, 500f, 500f)
        assertTrue(flat.y > up.y)                    // closer to centre vertically than top-down
        assertTrue(RadarMath.project(0.0, 1.0, 0.0, 1.0, 100.0, 500f, 500f, 30.0).y < flat.y) // pin top above its base
        // rotating by 90 deg moves north to the screen's left/right
        val rot = RadarMath.project(0.0, 1.0, 90.0, 0.0, 100.0, 500f, 500f)
        assertEquals(400f, rot.x, 0.01f); assertEquals(500f, rot.y, 0.01f)
        // farther north => larger depth
        assertTrue(RadarMath.project(0.0, 2.0, 0.0, 1.0, 100.0, 0f, 0f).depth > flat.depth)
    }
}
