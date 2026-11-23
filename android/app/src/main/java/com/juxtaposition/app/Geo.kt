package com.juxtaposition.app

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** Initial compass bearing 0..359 (0 = north, 90 = east). */
    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (((Math.toDegrees(atan2(y, x)) + 360) % 360)).let { Math.round(it).toInt() % 360 }
    }

    private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    fun compass(deg: Int) = COMPASS[Math.round(deg / 45.0).toInt() % 8]
}
