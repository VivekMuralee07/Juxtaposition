package com.juxtaposition.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.juxtaposition.app.Place
import com.juxtaposition.app.HoursStatus
import com.juxtaposition.app.hoursStatus
import com.juxtaposition.app.RadarMath
import com.juxtaposition.app.RadarZoom
import com.juxtaposition.app.RingScale

// The radar is an "instrument panel": always dark so pins glow, whatever the app theme.
private val PanelTop = Color(0xFF1C2633)
private val PanelBottom = Color(0xFF0E141C)
private val Ring = Color(0xFF33445A)
private val RingLabel = Color(0xFF8394AA)
private val YouBlue = Color(0xFF4DA3FF)
private val Accent = Color(0xFFFF8A4C)
private val NorthRed = Color(0xFFFF6B6B)

/**
 * Nearby places around "you" at the centre, with distance rings and a radar sweep.
 * Places that are known to be closed right now are dimmed.
 */
@Composable
fun RadarView(
    places: List<Place>, selected: Place?, radiusM: Int,
    onSelect: (Place?) -> Unit, modifier: Modifier = Modifier, animate: Boolean = true,
    /** Direction the user faces (degrees from true north) when it should be "up"; null = north is up. */
    facingDeg: Float? = null, showFacingCone: Float? = null,
    /** Pinch / double-tap zoom, hoisted so the card can show a reset button. 1 = the whole selected radius. */
    zoom: Float = 1f, onZoomChange: (Float) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val zoomNow by rememberUpdatedState(zoom)
    val radiusKm = radiusM / 1000.0
    val rotation by rememberUpdatedState(facingDeg ?: 0f) // read at draw / tap time, so taps always match what is drawn
    val closedIds = remember(places) {
        val now = java.time.LocalDateTime.now()
        places.filter { it.hoursStatus(now) is HoursStatus.Closed }.map { it.id }.toSet()
    }

    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6500, easing = LinearEasing)), label = "sweep")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val sweepAngle = if (animate) sweep else 40f
    val pulseT = if (animate) pulse else 0.4f

    data class Spot(val place: Place, val x: Float, val y: Float)
    fun layout(w: Float, h: Float): List<Spot> {
        val pxPerKm = (minOf(w, h) / 2f - 34f) / (radiusKm / zoomNow)
        return places.map {
            val (px, py) = RadarMath.toPlane(it.bearingDeg - rotation.toDouble(), it.distanceKm)
            val (sx, sy) = RadarMath.project(px, py, pxPerKm, w / 2f, h / 2f)
            Spot(it, sx, sy)
        }.sortedByDescending { it.place.distanceKm } // nearest drawn last, so it sits on top
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(places, radiusM) {
                detectTapGestures(
                    onDoubleTap = { onZoomChange(RadarZoom.toggle(zoomNow)) },
                    onTap = { tap ->
                        val hit = layout(size.width.toFloat(), size.height.toFloat()).asReversed()
                            .minByOrNull { (it.x - tap.x) * (it.x - tap.x) + (it.y - tap.y) * (it.y - tap.y) }
                        val close = hit != null && (hit.x - tap.x) * (hit.x - tap.x) + (hit.y - tap.y) * (hit.y - tap.y) < 52f * 52f
                        if (close) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // a light tick
                        onSelect(if (close) hit!!.place else null)
                    },
                )
            }
            .pointerInput(Unit) { detectTransformGestures { _, _, gestureZoom, _ -> onZoomChange(RadarZoom.apply(zoomNow, gestureZoom)) } }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val visibleRadiusKm = radiusKm / zoomNow
        val pxPerKm = (minOf(w, h) / 2f - 34f) / visibleRadiusKm
        val outer = (visibleRadiusKm * pxPerKm).toFloat()

        drawRect(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
        drawRect(Brush.radialGradient(listOf(Accent.copy(alpha = 0.10f), Color.Transparent), Offset(cx, cy), minOf(w, h) * 0.6f))

        // soft filled disc + rings, labelled in walking minutes; the outer ring shows distance and time
        drawCircle(Color.White.copy(alpha = 0.035f), outer, Offset(cx, cy))
        val diag = 0.7071f
        for (ring in RingScale.rings(visibleRadiusKm * 1000)) {
            val rr = (ring.distanceKm * pxPerKm).toFloat()
            drawCircle(Ring, rr, Offset(cx, cy), style = Stroke(width = if (ring.isBoundary) 2.5f else 1.5f))
            // labels sit on the lower-left diagonal, away from the N/E/S/W letters, on a small backing so the ring line doesn't cross them
            val lay = measurer.measure(ring.label, TextStyle(fontSize = 10.sp, color = RingLabel))
            val lx = cx - rr * diag - lay.size.width / 2f
            val ly = cy + rr * diag - lay.size.height / 2f
            drawRoundRect(PanelBottom.copy(alpha = 0.85f), Offset(lx - 5f, ly - 1f), Size(lay.size.width + 10f, lay.size.height + 2f), CornerRadius(8f))
            drawText(lay, topLeft = Offset(lx, ly))
        }
        drawLine(Ring.copy(alpha = 0.5f), Offset(cx - outer, cy), Offset(cx + outer, cy), strokeWidth = 1f)

        // radar sweep: a thin fading trail behind the sweep line
        rotate(sweepAngle, Offset(cx, cy)) {
            drawCircle(
                Brush.sweepGradient(0f to Color.Transparent, 0.82f to Color.Transparent, 1f to Accent.copy(alpha = 0.34f), center = Offset(cx, cy)),
                outer, Offset(cx, cy),
            )
            drawLine(Accent.copy(alpha = 0.55f), Offset(cx, cy), Offset(cx + outer, cy), strokeWidth = 2f)
        }

        // compass letters sit on the outer ring and rotate with the view; N is highlighted
        for ((label, deg) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)) {
            val (lx, ly) = RadarMath.toPlane(deg - rotation, visibleRadiusKm)
            val (sx, sy) = RadarMath.project(lx, ly, pxPerKm, cx, cy)
            val isN = label == "N"
            if (isN) drawLine(NorthRed.copy(alpha = 0.35f), Offset(cx, cy), Offset(sx, sy), strokeWidth = 1.5f)
            val col = if (isN) NorthRed else RingLabel
            drawCircle(col.copy(alpha = if (isN) 0.18f else 0.10f), 12f, Offset(sx, sy))
            val lay = measurer.measure(label, TextStyle(fontSize = if (isN) 12.sp else 11.sp, color = col, fontWeight = FontWeight.Bold))
            drawText(lay, topLeft = Offset(sx - lay.size.width / 2f, sy - lay.size.height / 2f))
        }

        // places
        val spots = layout(w, h)
        for (s in spots) {
            val c = typeColor(s.place.type)
            val isSel = s.place.id == selected?.id
            val dim = if (s.place.id in closedIds) 0.38f else 1f
            val p = Offset(s.x, s.y)
            drawCircle(c.copy(alpha = 0.22f * dim), if (isSel) 20f else 13f, p)
            drawCircle(c.copy(alpha = dim), if (isSel) 9f else 6.5f, p)
            drawCircle(Color.White.copy(alpha = 0.9f * dim), if (isSel) 3.5f else 2.2f, p)
            if (isSel) {
                drawCircle(Color.White, 15f, p, style = Stroke(width = 2.5f))
                drawCircle(c.copy(alpha = 0.5f * (1f - pulseT)), 15f + 22f * pulseT, p, style = Stroke(width = 2f))
            }
        }
        // labels last so they sit above pins: selected first, then the nearest three
        val nearest = places.sortedBy { it.distanceKm }.take(3).map { it.id }.toSet()
        val placed = mutableListOf<Rect>()
        spots.filter { it.place.id == selected?.id || it.place.id in nearest }
            .sortedWith(compareBy({ it.place.id != selected?.id }, { it.place.distanceKm }))
            .forEach { pill(measurer, it.place.name, Offset(it.x, it.y), w, h, placed, bright = it.place.id == selected?.id) }

        // the direction you face: a soft beam from your dot (always up in heading-up mode)
        showFacingCone?.let { facing ->
            rotate(facing - rotation, Offset(cx, cy)) {
                val beam = 78f
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx - beam * 0.46f, cy - beam)
                        lineTo(cx + beam * 0.46f, cy - beam)
                        close()
                    },
                    Brush.verticalGradient(listOf(YouBlue.copy(alpha = 0.0f), YouBlue.copy(alpha = 0.40f)), startY = cy - beam, endY = cy),
                )
            }
        }
        // you
        drawCircle(YouBlue.copy(alpha = 0.30f * (1f - pulseT)), 14f + 46f * pulseT, Offset(cx, cy))
        drawCircle(YouBlue.copy(alpha = 0.22f), 17f, Offset(cx, cy))
        drawCircle(Color.White, 9.5f, Offset(cx, cy))
        drawCircle(YouBlue, 6.5f, Offset(cx, cy))
    }
}

/** Draws a name pill above (or, if that collides, below) the point; skips it if neither fits and it isn't the selected one. */
private fun DrawScope.pill(measurer: TextMeasurer, text: String, anchor: Offset, canvasW: Float, canvasH: Float, placed: MutableList<Rect>, bright: Boolean) {
    val shown = if (text.length > 22) text.take(21) + "…" else text
    val layout = measurer.measure(shown, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (bright) Color(0xFF111827) else Color(0xFFE6EDF6)))
    val padX = 10f
    val padY = 5f
    val wBox = layout.size.width + padX * 2
    val hBox = layout.size.height + padY * 2
    val x = (anchor.x - wBox / 2).coerceIn(6f, canvasW - wBox - 6f)
    val above = Rect(Offset(x, anchor.y - 22f - hBox), Size(wBox, hBox))
    val below = Rect(Offset(x, anchor.y + 18f), Size(wBox, hBox))
    fun free(r: Rect) = r.top >= 4f && r.bottom <= canvasH - 4f && placed.none { it.inflate(3f).overlaps(r) }
    val spot = when {
        free(above) -> above
        free(below) -> below
        bright -> above // the selected place always gets its label
        else -> return
    }
    placed += spot
    drawRoundRect(if (bright) Color.White else Color(0xFF263447).copy(alpha = 0.92f), spot.topLeft, spot.size, CornerRadius(hBox / 2))
    drawText(layout, topLeft = Offset(spot.left + padX, spot.top + padY))
}
