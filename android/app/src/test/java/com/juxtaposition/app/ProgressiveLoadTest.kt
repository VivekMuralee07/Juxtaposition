package com.juxtaposition.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProgressiveLoadTest {
    private fun result(r: Int, n: Int) = NearbyResult(r, (1..n).map {
        Place("id$it", "P$it", "restaurant", null, 0.0, 0.0, it * 0.1, 0, "N", null, null, null, null, null)
    })

    @Test fun stepsWidenUpToTheRequestedRadius() {
        assertEquals(listOf(500), ProgressiveLoad.steps(500))
        assertEquals(listOf(1000), ProgressiveLoad.steps(1000))
        assertEquals(listOf(1000, 2000), ProgressiveLoad.steps(2000))
        assertEquals(listOf(1000, 2000, 5000), ProgressiveLoad.steps(5000))
        assertEquals(listOf(1000, 2000, 3000), ProgressiveLoad.steps(3000))
    }

    @Test fun everyStepIsReportedAndTheLastOneIsMarkedFinal() = runBlocking {
        val seen = mutableListOf<Triple<Int, Int, Boolean>>()
        val out = ProgressiveLoad.run(5000, { r -> result(r, r / 100) to emptyList() }) { r, res, fin -> seen += Triple(r, res.places.size, fin) }
        assertEquals(listOf(Triple(1000, 10, false), Triple(2000, 20, false), Triple(5000, 50, true)), seen)
        assertTrue(out.complete(5000)); assertEquals(5000, out.reachedRadiusM); assertEquals(50, out.result!!.places.size)
    }

    @Test fun aFailingWiderStepKeepsTheNearerResultsAndStopsAsking() = runBlocking {
        val asked = mutableListOf<Int>()
        val out = ProgressiveLoad.run(5000, { r ->
            asked += r
            if (r >= 2000) null to listOf("OpenStreetMap: timeout; server: busy") else result(r, 7) to emptyList()
        }) { _, _, _ -> }
        assertEquals(listOf(1000, 2000), asked)          // 5 km never attempted after 2 km failed
        assertFalse(out.complete(5000)); assertEquals(1000, out.reachedRadiusM); assertEquals(7, out.result!!.places.size)
        assertEquals(listOf("OpenStreetMap: timeout; server: busy"), out.problems)
    }

    @Test fun failingTheFirstStepGivesNothing() = runBlocking {
        val out = ProgressiveLoad.run(2000, { null to listOf("OpenStreetMap: timeout") }) { _, _, _ -> fail("no step should succeed") }
        assertNull(out.result); assertNull(out.reachedRadiusM); assertFalse(out.complete(2000))
    }

    @Test fun smallRadiusIsASingleStep() = runBlocking {
        var calls = 0
        val out = ProgressiveLoad.run(500, { r -> calls++; result(r, 3) to emptyList() }) { _, _, fin -> assertTrue(fin) }
        assertEquals(1, calls); assertTrue(out.complete(500))
    }
}
