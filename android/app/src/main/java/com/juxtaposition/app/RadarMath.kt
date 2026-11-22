package com.juxtaposition.app

import kotlin.math.cos
import kotlin.math.sin

/** A point projected onto the screen. `depth` grows with distance "into" the scene (draw large depth first). */
data class Projected(val x: Float, val y: Float, val depth: Float)

object RadarMath {
    const val MAX_TILT_DEG = 58.0

    /** Position on the ground plane in km: x = east, y = north. */
    fun toPlane(bearingDeg: Int, distanceKm: Double): Pair<Double, Double> {
        val b = Math.toRadians(bearingDeg.toDouble())
        return distanceKm * sin(b) to distanceKm * cos(b)
    }

    /**
     * Rotate the plane by [rotDeg] (spin around the vertical axis), tilt it by [tilt] (0 = top-down 2D, 1 = max tilt),
     * and scale km -> pixels. [heightPx] lifts a pin straight up on screen (0 when viewed top-down).
     */
    fun project(xKm: Double, yKm: Double, rotDeg: Double, tilt: Double, pxPerKm: Double, cx: Float, cy: Float, heightPx: Double = 0.0): Projected {
        val r = Math.toRadians(rotDeg)
        val xr = xKm * cos(r) - yKm * sin(r)
        val yr = xKm * sin(r) + yKm * cos(r)
        val t = Math.toRadians(tilt * MAX_TILT_DEG)
        return Projected(
            x = (cx + xr * pxPerKm).toFloat(),
            y = (cy - yr * pxPerKm * cos(t) - heightPx * sin(t)).toFloat(),
            depth = yr.toFloat(),
        )
    }
}
