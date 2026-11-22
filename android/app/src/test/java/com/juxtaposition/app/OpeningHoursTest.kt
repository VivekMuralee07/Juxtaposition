package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class OpeningHoursTest {
    private fun st(raw: String?, day: Int, hh: Int, mm: Int = 0) = OpeningHours.status(raw, day, hh * 60 + mm)
    private val MON = 0; private val WED = 2; private val SAT = 5; private val SUN = 6

    @Test fun simpleDaily() {
        val h = "Mo-Su 10:00-23:00"
        assertEquals(HoursStatus.Open("Open · closes 23:00"), st(h, WED, 12))
        assertEquals(HoursStatus.Closed("Closed · opens 10:00"), st(h, WED, 8))
        assertEquals(HoursStatus.Closed("Closed · opens tomorrow 10:00"), st(h, WED, 23, 30))
    }

    @Test fun edgesAreHalfOpen() {
        val h = "Mo-Su 10:00-23:00"
        assertTrue(st(h, MON, 10, 0) is HoursStatus.Open)      // opens exactly at 10:00
        assertTrue(st(h, MON, 22, 59) is HoursStatus.Open)
        assertTrue(st(h, MON, 23, 0) is HoursStatus.Closed)    // closed exactly at 23:00
    }

    @Test fun splitShiftAndOffDay() {
        val h = "Mo-Sa 11:00-15:00,18:00-23:00; Su off"
        assertTrue(st(h, SAT, 13) is HoursStatus.Open)
        assertEquals(HoursStatus.Closed("Closed · opens 18:00"), st(h, SAT, 16))
        assertEquals(HoursStatus.Closed("Closed · opens tomorrow 11:00"), st(h, SUN, 12))   // Sunday is off; Monday is tomorrow
        assertEquals(HoursStatus.Closed("Closed · opens Mon 09:00"), st("Mo-Fr 09:00-17:00", 4, 18))   // Friday evening: next opening is 3 days away
    }

    @Test fun laterRuleOverridesEarlier() {
        val h = "Mo-Fr 09:00-17:00; We off"
        assertTrue(st(h, MON, 10) is HoursStatus.Open)
        assertEquals(HoursStatus.Closed("Closed · opens tomorrow 09:00"), st(h, WED, 10))
    }

    @Test fun overnightHoursSpillIntoNextDay() {
        val h = "Mo-Su 18:00-02:00"
        assertEquals(HoursStatus.Open("Open · closes 02:00"), st(h, SAT, 23))   // Saturday evening
        assertEquals(HoursStatus.Open("Open · closes 02:00"), st(h, SUN, 1))    // after midnight, still Saturday's shift
        assertEquals(HoursStatus.Closed("Closed · opens 18:00"), st(h, SUN, 3))
        // Monday 01:00 is covered by Sunday's shift even if Monday itself is off
        assertTrue(st("Su 20:00-03:00", MON, 1) is HoursStatus.Open)
        assertTrue(st("Su 20:00-03:00", MON, 4) is HoursStatus.Closed)
    }

    @Test fun twentyFourSeven() {
        assertEquals(HoursStatus.Open("Open 24 hours"), st("24/7", WED, 3))
        assertEquals(HoursStatus.Open("Open 24 hours"), st("Mo-Su 00:00-24:00", SAT, 15))
    }

    @Test fun publicHolidayRulesAreIgnored() {
        assertTrue(st("Mo-Su 10:00-22:00; PH off", WED, 12) is HoursStatus.Open)
    }

    @Test fun unsupportedOrMissingIsUnknown() {
        assertEquals(HoursStatus.Unknown, st(null, WED, 12))
        assertEquals(HoursStatus.Unknown, st("", WED, 12))
        assertEquals(HoursStatus.Unknown, st("Mo-Su sunrise-sunset", WED, 12))
        assertEquals(HoursStatus.Unknown, st("Apr-Sep Mo-Su 09:00-18:00", WED, 12))
        assertEquals(HoursStatus.Unknown, st("Mo-Fr 09:00-17:00 open \"by appointment\"", WED, 12))
        assertEquals(HoursStatus.Unknown, st("by appointment", WED, 12))
        assertEquals(HoursStatus.Unknown, st("Mo-Fr", WED, 12))      // days but no times
        assertEquals(HoursStatus.Unknown, st("Mo-Su 25:99-26:00", WED, 12))
    }

    @Test fun dayRangesWrapAroundTheWeek() {
        val h = "Fr-Mo 10:00-22:00"   // Fri, Sat, Sun, Mon
        assertTrue(st(h, SUN, 12) is HoursStatus.Open)
        assertTrue(st(h, MON, 12) is HoursStatus.Open)
        assertTrue(st(h, WED, 12) is HoursStatus.Closed)
        assertEquals(HoursStatus.Closed("Closed · opens Fri 10:00"), st(h, WED, 12))
    }

    @Test fun neverOpenInSevenDays() {
        assertEquals(HoursStatus.Closed("Closed"), st("Mo-Su off", WED, 12))
    }
}
