package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class OpeningHoursTest {
    private fun st(raw: String?, day: Int, hh: Int, mm: Int = 0) = OpeningHours.status(raw, day, hh * 60 + mm)
    private fun label(s: HoursStatus): String? = when (s) { is HoursStatus.Open -> s.label; is HoursStatus.Closed -> s.label; HoursStatus.Unknown -> null }
    private val MON = 0; private val WED = 2; private val SAT = 5; private val SUN = 6

    @Test fun simpleDaily() {
        val h = "Mo-Su 10:00-23:00"
        assertEquals("Open · closes 23:00", label(st(h, WED, 12)))
        assertEquals("Closed · opens 10:00", label(st(h, WED, 8)))
        assertEquals("Closed · opens tomorrow 10:00", label(st(h, WED, 23, 30)))
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
        assertEquals("Closed · opens 18:00", label(st(h, SAT, 16)))
        assertEquals("Closed · opens tomorrow 11:00", label(st(h, SUN, 12)))   // Sunday is off; Monday is tomorrow
        assertEquals("Closed · opens Mon 09:00", label(st("Mo-Fr 09:00-17:00", 4, 18)))   // Friday evening: next opening is 3 days away
    }

    @Test fun laterRuleOverridesEarlier() {
        val h = "Mo-Fr 09:00-17:00; We off"
        assertTrue(st(h, MON, 10) is HoursStatus.Open)
        assertEquals("Closed · opens tomorrow 09:00", label(st(h, WED, 10)))
    }

    @Test fun overnightHoursSpillIntoNextDay() {
        val h = "Mo-Su 18:00-02:00"
        assertEquals("Open · closes 02:00", label(st(h, SAT, 23)))   // Saturday evening
        assertEquals("Open · closes 02:00", label(st(h, SUN, 1)))    // after midnight, still Saturday's shift
        assertEquals("Closed · opens 18:00", label(st(h, SUN, 3)))
        // Monday 01:00 is covered by Sunday's shift even if Monday itself is off
        assertTrue(st("Su 20:00-03:00", MON, 1) is HoursStatus.Open)
        assertTrue(st("Su 20:00-03:00", MON, 4) is HoursStatus.Closed)
    }

    @Test fun twentyFourSeven() {
        assertEquals("Open 24 hours", label(st("24/7", WED, 3)))
        assertEquals("Open 24 hours", label(st("Mo-Su 00:00-24:00", SAT, 15)))
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
        assertEquals("Closed · opens Fri 10:00", label(st(h, WED, 12)))
    }

    @Test fun neverOpenInSevenDays() {
        assertEquals("Closed", label(st("Mo-Su off", WED, 12)))
    }

    @Test fun rulesSeparatedByCommas() {
        val h = "Mo-Sa 10:00-20:00, Su 12:00-18:00"
        assertEquals("Open · closes 20:00", label(st(h, WED, 12)))
        assertEquals("Open · closes 18:00", label(st(h, SUN, 12)))
        assertTrue(st(h, SUN, 19) is HoursStatus.Closed)
        assertTrue(st("Mo-Fr 09:00-17:00, Sa,Su off", SAT, 12) is HoursStatus.Closed)
        assertTrue(st("Mo-Fr 09:00-17:00, Sa,Su off", WED, 12) is HoursStatus.Open)
    }

    @Test fun dayListsAndSplitShiftsAreNotBrokenByTheCommaRule() {
        assertTrue(st("Mo,We,Fr 10:00-20:00", WED, 12) is HoursStatus.Open)
        assertTrue(st("Mo,We,Fr 10:00-20:00", 1, 12) is HoursStatus.Closed)      // Tuesday not listed
        assertTrue(st("Mo-Su 11:00-15:00, 18:00-23:00", WED, 16) is HoursStatus.Closed)
        assertTrue(st("Mo-Su 11:00-15:00, 18:00-23:00", WED, 19) is HoursStatus.Open)
    }

    @Test fun lowerCaseAndLooseSpacing() {
        assertTrue(st("mo-su 9:00 - 22:00", WED, 12) is HoursStatus.Open)
    }

    @Test fun closingSoonCountsDown() {
        val h = "Mo-Su 10:00-23:00"
        val a = st(h, WED, 22, 15) as HoursStatus.Open        // 45 minutes left: counts down
        assertEquals("Closes in 45 min", a.label); assertEquals(45, a.minutesLeft); assertTrue(a.closingSoon)
        val b = st(h, WED, 22, 59) as HoursStatus.Open
        assertEquals("Closes in 1 min", b.label); assertTrue(b.closingSoon)
        val c = st(h, WED, 22, 14) as HoursStatus.Open        // 46 minutes left: still the clock time
        assertEquals("Open · closes 23:00", c.label); assertEquals(46, c.minutesLeft); assertFalse(c.closingSoon)
        val d = st(h, WED, 12) as HoursStatus.Open
        assertFalse(d.closingSoon); assertEquals(660, d.minutesLeft)
    }

    @Test fun closingSoonAcrossMidnight() {
        val h = "Mo-Su 18:00-02:00"
        val a = st(h, SAT, 23, 30) as HoursStatus.Open         // 2.5 hours left
        assertEquals(150, a.minutesLeft); assertFalse(a.closingSoon)
        val b = st(h, SUN, 1, 30) as HoursStatus.Open          // after midnight, Saturday's shift: 30 minutes left
        assertEquals("Closes in 30 min", b.label); assertEquals(30, b.minutesLeft); assertTrue(b.closingSoon)
    }

    @Test fun twentyFourHoursNeverClosesSoon() {
        val a = st("24/7", WED, 23, 59) as HoursStatus.Open
        assertNull(a.minutesLeft); assertFalse(a.closingSoon)
    }
}
