package com.juxtaposition.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.juxtaposition.app.Place
import com.juxtaposition.app.Projected
import com.juxtaposition.app.RadarMath

private fun colorFor(type: String) = when (type) {
    "fast_food" -> Color(0xFFF59E0B)
    "cafe" -> Color(0xFF8B5E3C)
    else -> Color(0xFF1B8A5A)
}

/**
 * Radar of nearby places around "you" at the centre. Distance rings are in km.
 * 2D: top-down. 3D: the ground plane tilts away, places become pins, and dragging spins the scene.
 */
@Composable
fun RadarView(places: List<Place>, selected: Place?, radiusM: Int, threeD: Boolean, onSelect: (Place?) -> Unit, modifier: Modifier = Modifier) {
    val tilt by animateFloatAsState(if (threeD) 1f else 0f, tween(500), label = "tilt")
    var rotation by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = Muted)
    val radiusKm = radiusM / 1000.0

    // Recomputed per frame so taps hit-test against exactly what is drawn.
    data class Spot(val place: Place, val base: Projected, val top: Projected)
    fun layout(w: Float, h: Float): List<Spot> {
        val cx = w / 2f
        val cy = h / 2f + if (tilt > 0f) 20f * tilt else 0f
        val pxPerKm = (minOf(w, h) / 2f - 28f) / radiusKm
        val pinH = 34.0
        return places.map {
            val (x, y) = RadarMath.toPlane(it.bearingDeg, it.distanceKm)
            Spot(it, RadarMath.project(x, y, rotation.toDouble(), tilt.toDouble(), pxPerKm, cx, cy),
                RadarMath.project(x, y, rotation.toDouble(), tilt.toDouble(), pxPerKm, cx, cy, pinH))
        }.sortedByDescending { it.base.depth } // far first, so near pins overlap them
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(places, radiusM) {
                detectTapGestures { tap ->
                    val spots = layout(size.width.toFloat(), size.height.toFloat())
                    val hit = spots.asReversed().minByOrNull { s ->
                        val t = if (tilt > 0.05f) s.top else s.base
                        (t.x - tap.x) * (t.x - tap.x) + (t.y - tap.y) * (t.y - tap.y)
                    }
                    val t = hit?.let { if (tilt > 0.05f) it.top else it.base }
                    val close = t != null && (t.x - tap.x) * (t.x - tap.x) + (t.y - tap.y) * (t.y - tap.y) < 48f * 48f
                    onSelect(if (close) hit!!.place else null)
                }
            }
            .pointerInput(threeD) {
                if (threeD) detectDragGestures { _, drag -> rotation = (rotation + drag.x * 0.35f) % 360f }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + 20f * tilt
        val pxPerKm = (minOf(w, h) / 2f - 28f) / radiusKm
        val cosT = Math.cos(Math.toRadians(tilt * RadarMath.MAX_TILT_DEG)).toFloat()

        // distance rings (ellipses when tilted)
        val rings = listOf(0.25f, 0.5f, 0.75f, 1f)
        for (f in rings) {
            val r = (radiusKm * f * pxPerKm).toFloat()
            drawOval(Color(0xFFE5E7EB), Offset(cx - r, cy - r * cosT), Size(2 * r, 2 * r * cosT), style = Stroke(width = 2f))
            val label = (radiusKm * f).let { if (it < 1) "${Math.round(it * 1000)} m" else "%.1f km".format(it) }
            drawText(measurer, label, Offset(cx + 4f, cy - r * cosT - 14f), labelStyle)
        }
        // N marker rotates with the scene
        val north = RadarMath.project(0.0, radiusKm * 1.0, rotation.toDouble(), tilt.toDouble(), pxPerKm, cx, cy)
        drawText(measurer, "N", Offset(north.x - 5f, north.y - 16f), TextStyle(fontSize = 12.sp, color = Color(0xFFDC2626)))
        drawLine(Color(0xFFFCA5A5), Offset(cx, cy), Offset(north.x, north.y), strokeWidth = 2f)

        // places
        val spots = layout(w, h)
        for (s in spots) {
            val c = colorFor(s.place.type)
            val isSel = s.place.id == selected?.id
            if (tilt > 0.05f) {
                drawLine(c.copy(alpha = 0.55f * tilt), Offset(s.base.x, s.base.y), Offset(s.top.x, s.top.y), strokeWidth = 3f)
                drawOval(Color.Black.copy(alpha = 0.12f), Offset(s.base.x - 6f, s.base.y - 2.5f), Size(12f, 5f))
            }
            val p = if (tilt > 0.05f) Offset(s.top.x, s.top.y) else Offset(s.base.x, s.base.y)
            drawCircle(if (isSel) Color.White else c, if (isSel) 13f else 8f, p)
            drawCircle(c, if (isSel) 10f else 6f, p)
        }
        // selected label on top
        spots.firstOrNull { it.place.id == selected?.id }?.let { s ->
            val p = if (tilt > 0.05f) s.top else s.base
            drawText(measurer, s.place.name, Offset((p.x - 40f).coerceIn(4f, w - 150f), p.y - 34f), TextStyle(fontSize = 12.sp, color = Color(0xFF111827)))
        }
        // you
        drawCircle(Color(0xFF2563EB).copy(alpha = 0.2f), 18f, Offset(cx, cy))
        drawCircle(Color.White, 9f, Offset(cx, cy))
        drawCircle(Color(0xFF2563EB), 6f, Offset(cx, cy))
        drawText(measurer, "You", Offset(cx - 12f, cy + 14f), TextStyle(fontSize = 11.sp, color = Color(0xFF2563EB)))
    }
}
