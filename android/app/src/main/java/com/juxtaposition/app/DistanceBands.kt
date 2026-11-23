package com.juxtaposition.app

/** 500 m distance bands for the grouped list: 0 = within 500 m, 1 = 500 m - 1 km, ... */
object DistanceBands {
    const val STEP_M = 500

    fun index(km: Double): Int = (km * 1000 / STEP_M).toInt().coerceAtLeast(0)

    private fun km(m: Int): String = (m / 1000.0).let { if (it == Math.floor(it)) "%.0f".format(it) else "%.1f".format(it) }

    fun label(index: Int): String {
        val lower = index * STEP_M
        val upper = lower + STEP_M
        return when {
            lower == 0 -> "Within $upper m"
            upper < 1000 -> "$lower m – $upper m"
            lower < 1000 -> "$lower m – ${km(upper)} km"
            else -> "${km(lower)} – ${km(upper)} km"
        }
    }
}
