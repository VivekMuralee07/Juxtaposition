package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class DistanceBandsTest {
    @Test fun bandsAreEveryFiveHundredMetres() {
        assertEquals(0, DistanceBands.index(0.0))
        assertEquals(0, DistanceBands.index(0.499))
        assertEquals(1, DistanceBands.index(0.5))     // exactly 500 m starts the next band
        assertEquals(1, DistanceBands.index(0.999))
        assertEquals(2, DistanceBands.index(1.0))
        assertEquals(9, DistanceBands.index(4.99))
        assertEquals(10, DistanceBands.index(5.0))
    }

    @Test fun labelsReadNaturally() {
        assertEquals("Within 500 m", DistanceBands.label(0))
        assertEquals("500 m – 1 km", DistanceBands.label(1))
        assertEquals("1 – 1.5 km", DistanceBands.label(2))
        assertEquals("1.5 – 2 km", DistanceBands.label(3))
        assertEquals("2 – 2.5 km", DistanceBands.label(4))
        assertEquals("4.5 – 5 km", DistanceBands.label(9))
    }
}
