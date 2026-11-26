package com.juxtaposition.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/** Pure helpers for the map view (no Android classes, so they are unit-testable). */
object MapGeo {
    /** Dot colours by place type, as hex strings for the map style (same palette as the radar). */
    fun colorHex(type: String) = when (type) { "fast_food" -> "#F2A007"; "cafe" -> "#29B6A8"; else -> "#E4572E" }

    /** A GeoJSON FeatureCollection with one point per place; `selected` makes its dot bigger. */
    fun placesJson(places: List<Place>, selectedId: String?): String {
        val features = JSONArray()
        for (p in places) {
            val selected = p.id == selectedId
            features.put(JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.lng).put(p.lat)))
                .put("properties", JSONObject().put("id", p.id).put("color", colorHex(p.type)).put("r", if (selected) 11 else 7)))
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    fun pointJson(lat: Double, lng: Double): String =
        JSONObject().put("type", "Feature")
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lng).put(lat)))
            .put("properties", JSONObject()).toString()

    /** A ring of [radiusM] metres around the point, as a GeoJSON line (64 segments). */
    fun ringJson(lat: Double, lng: Double, radiusM: Int): String {
        val coords = JSONArray()
        val dLat = radiusM / 111_320.0
        val dLng = radiusM / (111_320.0 * cos(lat * PI / 180).coerceAtLeast(0.01))
        for (i in 0..64) {
            val a = 2 * PI * i / 64
            coords.put(JSONArray().put(lng + dLng * cos(a)).put(lat + dLat * sin(a)))
        }
        return JSONObject().put("type", "Feature")
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords))
            .put("properties", JSONObject()).toString()
    }

    /** Map zoom at which a circle of [radiusM] metres spans about [halfWidthDp] dp from the centre (512 px tiles). */
    fun zoomForRadius(radiusM: Int, latDeg: Double, halfWidthDp: Double = 150.0): Double {
        val metersPerDpAtZoom0 = 78_271.5 * cos(latDeg * PI / 180).coerceAtLeast(0.01)
        val z = ln(metersPerDpAtZoom0 * halfWidthDp / radiusM.coerceAtLeast(50)) / ln(2.0)
        return z.coerceIn(10.0, 18.0)
    }
}
