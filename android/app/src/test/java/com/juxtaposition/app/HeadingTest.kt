package com.juxtaposition.app

import org.junit.Assert.*
import org.junit.Test

class HeadingTest {
    private fun matrix(vararg v: Float) = v

    @Test fun phoneFlatUsesTheTopEdge() {
        // identity: top edge points north
        assertEquals(0f, Heading.fromRotationMatrix(matrix(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)), 0.5f)
        // top edge points east (rotated 90 degrees clockwise seen from above)
        assertEquals(90f, Heading.fromRotationMatrix(matrix(0f,1f,0f, -1f,0f,0f, 0f,0f,1f)), 0.5f)
        // top edge points south, west
        assertEquals(180f, Heading.fromRotationMatrix(matrix(-1f,0f,0f, 0f,-1f,0f, 0f,0f,1f)), 0.5f)
        assertEquals(270f, Heading.fromRotationMatrix(matrix(0f,-1f,0f, 1f,0f,0f, 0f,0f,1f)), 0.5f)
    }

    @Test fun phoneUprightUsesTheBackOfThePhone() {
        // held upright in portrait, camera (back) facing north: device Y = up, device Z (out of screen) = south
        assertEquals(0f, Heading.fromRotationMatrix(matrix(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)), 0.5f)
        // same, but turned to face east: back faces east, so device Z (towards the user) points west
        assertEquals(90f, Heading.fromRotationMatrix(matrix(0f,0f,-1f, 1f,0f,0f, 0f,1f,0f)), 0.5f)
    }

    @Test fun halfwayTiltBlendsInsteadOfJumping() {
        // facing north with the phone tilted 45 degrees: both estimates agree, result stays north
        val s = Math.sqrt(0.5).toFloat()
        assertEquals(0f, Heading.fromRotationMatrix(matrix(1f,0f,0f, 0f,s,-s, 0f,s,s)), 0.5f)
    }

    @Test fun trueHeadingAppliesDeclinationAndWraps() {
        assertEquals(5f, Heading.trueHeading(3f, 2f), 1e-4f)
        assertEquals(358f, Heading.trueHeading(0f, -2f), 1e-4f)   // -2 wraps to 358
        assertEquals(1f, Heading.trueHeading(359f, 2f), 1e-4f)
    }

    @Test fun smoothingTakesTheShortWayRound() {
        assertEquals(359f, Heading.smooth(null, 359f, 0.5f), 1e-4f)
        assertEquals(0f, Heading.smooth(358f, 2f, 0.5f), 1e-3f)       // 358 -> 2 is +4, half way = 0 (not 180)
        assertEquals(359f, Heading.smooth(2f, 356f, 0.5f), 1e-3f)     // and the other direction
        assertEquals(15f, Heading.smooth(10f, 20f, 0.5f), 1e-4f)
        assertEquals(10f, Heading.smooth(10f, 10f, 0.25f), 1e-4f)
    }

    @Test fun planePositionsRotateWithTheHeading() {
        // facing east: a place due east is straight ahead (screen up); due north is to the left
        val (ax, ay) = RadarMath.toPlane(90.0 - 90.0, 1.0)
        assertEquals(0.0, ax, 1e-9); assertEquals(1.0, ay, 1e-9)
        val (bx, by) = RadarMath.toPlane(0.0 - 90.0, 1.0)
        assertEquals(-1.0, bx, 1e-9); assertEquals(0.0, by, 1e-9)
        // the N marker, drawn at bearing -heading, follows the same rule
        val (nx, ny) = RadarMath.toPlane(-90.0, 2.0)
        assertEquals(-2.0, nx, 1e-9); assertEquals(0.0, ny, 1e-9)
    }
}
