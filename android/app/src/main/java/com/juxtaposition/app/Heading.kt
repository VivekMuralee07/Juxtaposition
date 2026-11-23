package com.juxtaposition.app

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Pure compass maths (no Android types), so it can be unit-tested. */
object Heading {
    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** Magnetic heading -> true heading using the local magnetic declination (degrees east of true north). */
    fun trueHeading(magneticDeg: Float, declinationDeg: Float): Float = normalize(magneticDeg + declinationDeg)

    /** Circular low-pass filter: moves `alpha` of the way to `next` along the SHORT way round (359 -> 1 is +2, not -358). */
    fun smooth(previous: Float?, next: Float, alpha: Float): Float {
        if (previous == null) return normalize(next)
        val delta = ((next - previous + 540f) % 360f) - 180f
        return normalize(previous + alpha * delta)
    }

    /**
     * Direction the user is facing, from a device-to-world rotation matrix (row-major, world: x = east, y = north, z = up).
     * Phone flat (screen up): the top edge points where you face. Phone upright (held in front of you while walking):
     * the back of the phone points where you face. In between the two are blended. Returns NaN if undetermined.
     */
    fun fromRotationMatrix(r: FloatArray): Float {
        val w = ((abs(r[8]) - 0.5f) / 0.3f).coerceIn(0f, 1f) // 1 = flat, 0 = upright
        fun unit(x: Float, y: Float): Pair<Float, Float> = hypot(x, y).let { n -> if (n < 1e-4f) 0f to 0f else x / n to y / n }
        val (tx, ty) = unit(r[1], r[4])    // top edge, horizontal part
        val (bx, by) = unit(-r[2], -r[5])  // back of the phone, horizontal part
        val x = w * tx + (1 - w) * bx
        val y = w * ty + (1 - w) * by
        if (abs(x) < 1e-4f && abs(y) < 1e-4f) return Float.NaN
        return normalize(Math.toDegrees(atan2(x, y).toDouble()).toFloat())
    }
}

/**
 * Reads the phone's compass (rotation-vector sensor, or accelerometer + magnetometer as a fallback) and reports the
 * true-north heading, smoothed. Start it only while it is needed: it uses a little battery.
 */
class CompassSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val available: Boolean get() = rotation != null || (accel != null && magnet != null)

    var declinationDeg = 0f
    var onHeading: (Float) -> Unit = {}
    var onLowAccuracy: (Boolean) -> Unit = {}

    private var smoothed: Float? = null
    private var lastEmitMs = 0L
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var haveMagnet = false

    fun start() {
        if (rotation != null) sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        else if (accel != null && magnet != null) {
            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
        smoothed = null; haveGravity = false; haveMagnet = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9)
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> try {
                SensorManager.getRotationMatrixFromVector(r, e.values)
            } catch (_: IllegalArgumentException) { // some devices report 5 values
                SensorManager.getRotationMatrixFromVector(r, e.values.copyOf(4))
            }
            Sensor.TYPE_ACCELEROMETER -> { e.values.copyInto(gravity, 0, 0, 3); haveGravity = true }
            Sensor.TYPE_MAGNETIC_FIELD -> { e.values.copyInto(geomagnetic, 0, 0, 3); haveMagnet = true }
        }
        if (e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
            if (!(haveGravity && haveMagnet) || !SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) return
        }
        val magnetic = Heading.fromRotationMatrix(r)
        if (magnetic.isNaN()) return
        smoothed = Heading.smooth(smoothed, Heading.trueHeading(magnetic, declinationDeg), 0.25f)
        val now = System.currentTimeMillis()
        if (now - lastEmitMs >= 80) { lastEmitMs = now; onHeading(smoothed!!) } // ~12 updates a second is plenty
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        onLowAccuracy(accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
    }

    companion object {
        fun declination(lat: Double, lng: Double): Float =
            GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
    }
}
