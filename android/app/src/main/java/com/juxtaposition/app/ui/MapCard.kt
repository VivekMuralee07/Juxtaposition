package com.juxtaposition.app.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.juxtaposition.app.MapGeo
import com.juxtaposition.app.Place
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val PLACES = "places"
private const val RING = "ring"
private const val ME = "me"

/** A street map (OpenFreeMap tiles, MapLibre) with the places as dots, your position, and the search radius. */
@Composable
internal fun MapCard(
    places: List<Place>, selected: Place?, center: Pair<Double, Double>, radiusM: Int,
    onSelect: (Place?) -> Unit, onPick: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    val mapView = remember { MapLibre.getInstance(ctx); MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentPlaces by rememberUpdatedState(places)
    val currentSelect by rememberUpdatedState(onSelect)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Tear down whatever the lifecycle had brought up, so leaving the Map tab never leaks the GL view.
            val s = lifecycle.currentState
            if (s.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (s.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            if (s.isAtLeast(Lifecycle.State.CREATED)) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st ->
                st.addSource(GeoJsonSource(RING, MapGeo.ringJson(center.first, center.second, radiusM)))
                st.addLayer(LineLayer("$RING-layer", RING).withProperties(
                    PropertyFactory.lineColor("#3B82F6"), PropertyFactory.lineWidth(2f), PropertyFactory.lineOpacity(0.6f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                ))
                st.addSource(GeoJsonSource(PLACES, MapGeo.placesJson(currentPlaces, null)))
                st.addLayer(CircleLayer("$PLACES-layer", PLACES).withProperties(
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                    PropertyFactory.circleRadius(Expression.get("r")),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f),
                ))
                st.addSource(GeoJsonSource(ME, MapGeo.pointJson(center.first, center.second)))
                st.addLayer(CircleLayer("$ME-layer", ME).withProperties(
                    PropertyFactory.circleColor("#2563EB"), PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f),
                ))
                style = st
            }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val hit = m.queryRenderedFeatures(RectF(pt.x - 28f, pt.y - 28f, pt.x + 28f, pt.y + 28f), "$PLACES-layer").firstOrNull()
                val id = hit?.getStringProperty("id")
                currentSelect(currentPlaces.firstOrNull { it.id == id })
                true
            }
        }
    }

    // Keep the dots, ring and "you" marker in step with the data.
    LaunchedEffect(style, places, selected?.id) { style?.getSourceAs<GeoJsonSource>(PLACES)?.setGeoJson(MapGeo.placesJson(places, selected?.id)) }
    LaunchedEffect(style, center, radiusM) {
        style?.getSourceAs<GeoJsonSource>(ME)?.setGeoJson(MapGeo.pointJson(center.first, center.second))
        style?.getSourceAs<GeoJsonSource>(RING)?.setGeoJson(MapGeo.ringJson(center.first, center.second, radiusM))
    }
    // Re-centre when the area or radius changes (about 100 m of walking does not move the camera under your finger).
    val areaKey = "%.3f,%.3f,%d".format(center.first, center.second, radiusM)
    LaunchedEffect(map, areaKey) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.first, center.second), MapGeo.zoomForRadius(radiusM, center.first)))
    }
    LaunchedEffect(map, selected?.id) {
        selected?.let { map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(it.lat, it.lng))) }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).clip(MaterialTheme.shapes.large)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Text(
            "© OpenFreeMap © OpenMapTiles Data from OpenStreetMap", Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFF444444),
        )
        onPick?.let { PickButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), it) }
    }
}
