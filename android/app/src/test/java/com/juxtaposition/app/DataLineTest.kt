package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class DataLineTest {
    private val NOW = 10_000_000L

    @Test fun phoneSource() {
        val info = DataInfo("overpass-api.de", 2140, NOW - 3 * 60_000, saved = false)
        assertEquals("OpenStreetMap via overpass-api.de (phone) • 2.1 s • updated 3 min ago • GPS ±12 m", DataLine.describe(info, "GPS ±12 m", NOW))
    }

    @Test fun savedCopyHasNoTimingOrSource() {
        val info = DataInfo(null, null, NOW - 2 * 3_600_000L, saved = true)
        assertEquals("Saved copy from 2 h ago • Network ±300 m", DataLine.describe(info, "Network ±300 m", NOW))
    }

    @Test fun nothingKnownYetGivesNoLine() {
        assertNull(DataLine.describe(null, null, NOW))
        assertEquals("GPS", DataLine.describe(null, "GPS", NOW))
    }

    @Test fun fixLabels() {
        assertEquals("GPS ±12 m", DataLine.fix("gps", 12.4f))
        assertEquals("Network ±300 m", DataLine.fix("network", 299.6f))
        assertEquals("GPS", DataLine.fix("gps", null))
        assertNull(DataLine.fix(null, 5f))
        assertEquals("Passive ±5 m", DataLine.fix("passive", 5f))
    }
}
