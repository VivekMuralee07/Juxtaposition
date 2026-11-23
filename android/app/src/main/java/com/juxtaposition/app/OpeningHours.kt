package com.juxtaposition.app

import java.time.LocalDateTime

sealed class HoursStatus {
    /** `minutesLeft` is how long until it closes (null for 24-hour places). Within [CLOSING_SOON_MIN] the label counts down. */
    data class Open(val label: String, val minutesLeft: Int? = null) : HoursStatus() {
        val closingSoon get() = minutesLeft != null && minutesLeft <= OpeningHours.CLOSING_SOON_MIN
    }
    data class Closed(val label: String) : HoursStatus()
    data object Unknown : HoursStatus()
}

/**
 * Reads the common subset of OpenStreetMap's `opening_hours` syntax:
 *   "Mo-Su 10:00-23:00", "Mo-Sa 11:00-15:00,18:00-23:00; Su off", "24/7", overnight "18:00-02:00".
 * Anything fancier (months, weeks, sunrise/sunset, conditions) returns Unknown rather than guessing.
 * Public-holiday rules (PH) are ignored because we can't know the holidays.
 * Days: 0 = Monday ... 6 = Sunday.
 */
object OpeningHours {
    const val CLOSING_SOON_MIN = 45
    private val DAY_NAMES = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val LONG_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val UNSUPPORTED = Regex("""(?i)\b(sunrise|sunset|dawn|dusk|week|SH|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b|[\[\]()+]|\d{4}""")
    private val SPLIT = Regex("""^(.*?)(\d{1,2}:\d{2}.*|off|closed|24/7)?$""", RegexOption.IGNORE_CASE)
    private val INTERVAL = Regex("""^(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})$""")

    // intervals per weekday: (startMin, endMin); endMin > 1440 means it runs past midnight
    private fun parse(raw: String): Array<List<Pair<Int, Int>>>? {
        val days = Array<List<Pair<Int, Int>>>(7) { emptyList() }
        var sawRule = false
        // Rules are separated by ';' and often also by ',' right after a time ("Mo-Sa 10:00-20:00, Su off").
        // A comma after a digit and before a letter starts a new rule; "Mo,We,Fr" and "10:00-12:00, 14:00-18:00" are not split.
        val parts = raw.split(';').flatMap { it.split(Regex("(?<=\\d)\\s*,\\s*(?=[A-Za-z])")) }
        for (part in parts) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if (UNSUPPORTED.containsMatchIn(p)) return null
            val m = SPLIT.matchEntire(p) ?: return null
            val dayPart = m.groupValues[1].trim().trimEnd(',').trim()
            val timePart = m.groupValues[2].trim()
            if (timePart.isEmpty()) return null // days with no times: ambiguous
            if (Regex("""(?i)\bPH\b""").containsMatchIn(dayPart)) continue // holiday rule: ignored
            val covered = parseDays(dayPart) ?: return null
            val intervals = parseTimes(timePart) ?: return null
            for (d in covered) days[d] = intervals // later rules override earlier ones for the same day
            sawRule = true
        }
        return if (sawRule) days else null
    }

    private fun parseDays(s: String): Set<Int>? {
        if (s.isEmpty()) return (0..6).toSet()
        val out = mutableSetOf<Int>()
        for (piece in s.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val bits = piece.split('-').map { it.trim() }
            val a = DAY_NAMES.indexOfFirst { it.equals(bits[0], true) }
            if (a < 0) return null
            if (bits.size == 1) { out += a; continue }
            if (bits.size != 2) return null
            val b = DAY_NAMES.indexOfFirst { it.equals(bits[1], true) }
            if (b < 0) return null
            var d = a
            while (true) { out += d; if (d == b) break; d = (d + 1) % 7 }
        }
        return out
    }

    private fun parseTimes(s: String): List<Pair<Int, Int>>? {
        if (s.equals("off", true) || s.equals("closed", true)) return emptyList()
        if (s == "24/7") return listOf(0 to 1440)
        val out = mutableListOf<Pair<Int, Int>>()
        for (piece in s.split(',')) {
            val m = INTERVAL.matchEntire(piece.trim()) ?: return null
            val start = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            var end = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
            if (start > 1440 || end > 1440 || m.groupValues[2].toInt() > 59 || m.groupValues[4].toInt() > 59) return null
            if (end <= start) end += 1440 // overnight (or e.g. 00:00-00:00 = all day is not supported; treated as overnight)
            out += start to end
        }
        return out.sortedBy { it.first }
    }

    private fun hhmm(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

    fun status(raw: String?, dayOfWeek: Int, minuteOfDay: Int): HoursStatus {
        if (raw.isNullOrBlank()) return HoursStatus.Unknown
        val days = parse(raw.trim()) ?: return HoursStatus.Unknown
        if (days.all { it == listOf(0 to 1440) }) return HoursStatus.Open("Open 24 hours", null)

        fun open(closeMinute: Int, minutesLeft: Int) = HoursStatus.Open(
            if (minutesLeft <= CLOSING_SOON_MIN) "Closes in $minutesLeft min" else "Open · closes ${hhmm(closeMinute)}", minutesLeft,
        )

        // open now? today's intervals, or last night's that run past midnight
        for ((s, e) in days[dayOfWeek]) if (minuteOfDay in s until minOf(e, 1440)) return open(e, e - minuteOfDay)
        val yesterday = (dayOfWeek + 6) % 7
        for ((_, e) in days[yesterday]) if (e > 1440 && minuteOfDay < e - 1440) return open(e, e - 1440 - minuteOfDay)

        // closed: find the next opening within a week
        for (offset in 0..7) {
            val d = (dayOfWeek + offset) % 7
            val next = days[d].firstOrNull { offset > 0 || it.first > minuteOfDay } ?: continue
            val label = when (offset) {
                0 -> "Closed · opens ${hhmm(next.first)}"
                1 -> "Closed · opens tomorrow ${hhmm(next.first)}"
                else -> "Closed · opens ${LONG_NAMES[d]} ${hhmm(next.first)}"
            }
            return HoursStatus.Closed(label)
        }
        return HoursStatus.Closed("Closed")
    }

    fun status(raw: String?, now: LocalDateTime = LocalDateTime.now()) = status(raw, now.dayOfWeek.value - 1, now.hour * 60 + now.minute)
}

fun Place.hoursStatus(now: LocalDateTime = LocalDateTime.now()) = OpeningHours.status(openingHours, now)
