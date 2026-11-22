package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    private val json = """
    {"mode":"simulated","restaurant":{"id":"r","name":"R","cuisine":"c","rating":4.5,"area":"a","platforms":["swiggy"],"distanceKm":2.1},
     "bestPlatform":"swiggy","maxSavings":18,
     "results":[
      {"platform":"swiggy","name":"Swiggy","color":"#FC8019","available":true,"items":[{"id":"1","name":"Biryani","qty":2,"unitPrice":300,"lineTotal":600}],
       "itemsTotal":600,"deliveryFee":30,"platformFee":6,"taxes":31,"discount":0,"couponCode":null,"total":667,"etaMinutes":35,"distanceKm":2.1,
       "isCheapest":true,"isFastest":false,"isBestValue":true,"savingsVsWorst":18,"source":"ondc","deepLink":{"scheme":null,"androidPackage":"in.swiggy.android"}},
      {"platform":"ondc","name":"ONDC","available":false,"reason":"Not listed"}]}
    """.trimIndent()

    @Test fun parsesComparison() {
        val c = parseComparison(json)
        assertEquals("swiggy", c.bestPlatform)
        assertTrue(c.simulated)
        assertEquals(2, c.results.size)
        val q = c.results[0]
        assertEquals("ondc", q.source); assertEquals(667, q.total); assertNull(q.couponCode); assertNull(q.scheme)
        assertEquals("in.swiggy.android", q.androidPackage)
        assertFalse(c.results[1].available); assertEquals("Not listed", c.results[1].reason)
    }
}
