package com.juxtaposition.app

import kotlin.math.cos
import kotlin.math.sin

object RadarMath {
    /** Position on the ground plane in km: x = east, y = north. */
    fun toPlane(bearingDeg: Int, distanceKm: Double): Pair<Double, Double> {
        val b = Math.toRadians(bearingDeg.toDouble())
        return distanceKm * sin(b) to distanceKm * cos(b)
    }

    /** km on the plane -> screen pixels (north is up, y grows downwards on screen). */
    fun project(xKm: Double, yKm: Double, pxPerKm: Double, cx: Float, cy: Float): Pair<Float, Float> =
        (cx + xKm * pxPerKm).toFloat() to (cy - yKm * pxPerKm).toFloat()
}
