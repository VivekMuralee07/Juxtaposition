package com.juxtaposition.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OverpassTest {
    private val ORIGIN = 12.9716 to 77.5946
    private val sample = """
    {"elements":[
     {"type":"node","id":1,"lat":12.9806,"lon":77.5946,"tags":{"name":"North Cafe","amenity":"cafe","cuisine":"coffee_shop;dessert","addr:street":"MG Road","addr:city":"Bengaluru","phone":"+91 80 1","opening_hours":"Mo-Su 08:00-22:00"}},
     {"type":"way","id":2,"center":{"lat":12.9716,"lon":77.6036},"tags":{"name":"East Biryani","amenity":"restaurant","diet:vegetarian":"yes","contact:website":"east.example"}},
     {"type":"node","id":3,"lat":12.9716,"lon":77.6036,"tags":{"name":"East Biryani","amenity":"restaurant"}},
     {"type":"node","id":4,"lat":12.97,"lon":77.59,"tags":{"amenity":"restaurant"}},
     {"type":"node","id":5,"lat":12.99,"lon":77.60,"tags":{"name":"Far Place","amenity":"fast_food"}},
     {"type":"relation","id":6,"tags":{"name":"No Position","amenity":"cafe"}}
    ]}""".trimIndent()

    @Test fun geoMathMatchesTheServer() {
        assertEquals(0, Geo.bearingDeg(12.9716, 77.5946, 13.0, 77.5946))
        assertEquals(90, Geo.bearingDeg(12.9716, 77.5946, 12.9716, 77.7))
        assertEquals("N", Geo.compass(0)); assertEquals("E", Geo.compass(95)); assertEquals("N", Geo.compass(350)); assertEquals("SW", Geo.compass(225))
        assertEquals(1.0, Geo.distanceKm(12.9716, 77.5946, 12.9806, 77.5946), 0.02)
    }

    @Test fun parseSortsDedupesAndMapsTags() {
        val r = OverpassClient.parse(sample, ORIGIN.first, ORIGIN.second, 5000)
        assertEquals(listOf("East Biryani", "North Cafe", "Far Place"), r.places.map { it.name }) // unnamed, duplicate and positionless dropped
        val cafe = r.places.first { it.name == "North Cafe" }
        assertEquals("N", cafe.direction); assertEquals("coffee shop, dessert", cafe.cuisine); assertEquals("MG Road, Bengaluru", cafe.address)
        assertEquals("Mo-Su 08:00-22:00", cafe.openingHours); assertEquals("+91 80 1", cafe.phone); assertEquals("Cafe", cafe.typeLabel)
        val east = r.places.first()
        assertEquals("yes", east.vegetarian); assertEquals("east.example", east.website); assertEquals("osm:way/2", east.id)
        assertEquals(5000, r.radiusM)
    }

    @Test fun queryUsesExactAmenityMatches() {
        val q = OverpassClient.buildQuery(12.9, 77.6, 1000, listOf("restaurant", "cafe"))
        assertTrue(q.contains("nwr[\"amenity\"=\"restaurant\"][\"name\"](around:1000,12.9,77.6);"))
        assertTrue(q.contains("\"amenity\"=\"cafe\"")); assertFalse(q.contains("~")); assertFalse(q.contains("fast_food"))
    }

    @Test fun firstGoodMirrorWinsAndASlowOneDoesNotBlock() = runBlocking {
        val calls = mutableListOf<String>()
        val r = OverpassClient.nearby(12.9716, 77.5946, 1000, mirrors = listOf("https://slow.test/x", "https://fast.test/x"), hedgeMs = 30) { url, _, _ ->
            calls += url
            if (url.contains("slow")) { delay(5_000); "never" } else sample
        }
        assertEquals(3, r.places.size)
        assertEquals(2, calls.size) // fast one was started after the hedge delay
    }

    @Test fun failuresFallThroughToTheNextMirror() = runBlocking {
        val r = OverpassClient.nearby(12.9716, 77.5946, 1000, mirrors = listOf("https://a.test/x", "https://b.test/x"), hedgeMs = 10) { url, _, _ ->
            if (url.contains("a.test")) throw java.io.IOException("HTTP 429") else sample
        }
        assertEquals(3, r.places.size)
    }

    @Test fun overloadRemarkIsAFailureNotAnEmptyList() = runBlocking {
        val busy = """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 1 after 26 seconds."}"""
        val e = try {
            OverpassClient.nearby(12.9716, 77.5946, 1000, mirrors = listOf("https://a.test/x", "https://b.test/x"), hedgeMs = 5) { _, _, _ -> busy }
            null
        } catch (e: ApiException) { e }
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("a.test: Query timed out") && e.message!!.contains("b.test"))
        // ...but a genuinely empty answer (no remark) is a valid result
        val none = OverpassClient.nearby(12.9716, 77.5946, 1000, mirrors = listOf("https://a.test/x"), hedgeMs = 5) { _, _, _ -> """{"elements":[]}""" }
        assertTrue(none.places.isEmpty())
    }

    @Test fun allMirrorsFailingNamesEachOne() = runBlocking {
        val e = try {
            OverpassClient.nearby(1.0, 1.0, 500, mirrors = listOf("https://a.test/x", "https://b.test/x"), hedgeMs = 5) { url, _, _ ->
                throw if (url.contains("a")) java.net.SocketTimeoutException() else java.io.IOException("HTTP 503")
            }
            null
        } catch (e: ApiException) { e }
        assertEquals("a.test: timeout; b.test: HTTP 503", e!!.message)
    }

    @Test fun cacheRoundTripKeepsEveryField() {
        val r = OverpassClient.parse(sample, ORIGIN.first, ORIGIN.second, 5000)
        val again = parseNearby(PlacesCache.toJson(r))
        assertEquals(r.places, again.places)
        assertEquals(r.radiusM, again.radiusM)
    }

    @Test fun agoTextIsHumanFriendly() {
        assertEquals("just now", agoText(10_000)); assertEquals("5 min ago", agoText(5 * 60_000L))
        assertEquals("3 h ago", agoText(3 * 3_600_000L)); assertEquals("2 d ago", agoText(2 * 86_400_000L))
    }
}
