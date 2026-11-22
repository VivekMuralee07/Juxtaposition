package com.juxtaposition.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.Place
import com.juxtaposition.app.ViewMode

private val RADII = listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km", 5000 to "5 km")
private val TYPES = listOf<Pair<String?, String>>(null to "All", "restaurant" to "Restaurants", "fast_food" to "Fast food", "cafe" to "Cafes")

@Composable
fun NearbyScreen(vm: AppViewModel) {
    val shown = vm.visiblePlaces
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = vm.viewMode.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            listOf(ViewMode.LIST to "List", ViewMode.RADAR_2D to "2D map", ViewMode.RADAR_3D to "3D view").forEach { (m, label) ->
                Tab(selected = vm.viewMode == m, onClick = { vm.updateViewMode(m) }, text = { Text(label) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADII.forEach { (m, label) -> FilterChip(selected = vm.radiusM == m, onClick = { vm.setRadius(m) }, label = { Text(label) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TYPES.forEach { (t, label) -> FilterChip(selected = vm.typeFilter == t, onClick = { vm.updateTypeFilter(t) }, label = { Text(label) }) }
        }
        Text(
            "${shown.size} place${if (shown.size == 1) "" else "s"} within ${RADII.firstOrNull { it.first == vm.radiusM }?.second ?: "${vm.radiusM} m"}",
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Muted, style = MaterialTheme.typography.labelMedium,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                shown.isEmpty() && !vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No restaurants found here.\nTry a larger radius. OpenStreetMap coverage varies by area.", color = Muted)
                }
                vm.viewMode == ViewMode.LIST -> PlaceList(shown, vm.selected, vm::select)
                else -> RadarView(shown, vm.selected, vm.radiusM, vm.viewMode == ViewMode.RADAR_3D, vm::select, Modifier.fillMaxSize())
            }
        }

        vm.selected?.let { PlaceDetail(it, onClose = { vm.select(null) }) }
        Text("Map data © OpenStreetMap contributors. Distances are straight-line.", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun PlaceList(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(places, key = { it.id }) { p ->
            Card(
                Modifier.fillMaxWidth().clickable { onSelect(p) },
                colors = CardDefaults.cardColors(containerColor = if (p.id == selected?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(p.typeLabel, p.cuisine).joinToString(" • "), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(distanceText(p.distanceKm), fontWeight = FontWeight.Bold, color = Green)
                        Text(p.direction, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceDetail(p: Place, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Text(listOfNotNull(p.typeLabel, p.cuisine, p.vegetarian?.let { if (it == "only") "Pure veg" else "Veg options" }).joinToString(" • "), color = Muted)
            Text("${distanceText(p.distanceKm)} ${p.direction} of you • ~${p.walkMinutes} min walk", fontWeight = FontWeight.SemiBold, color = Green)
            p.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            p.openingHours?.let { Text("Hours: $it", style = MaterialTheme.typography.bodySmall, color = Muted) }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(ctx, Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.name)})"), Intent.ACTION_VIEW) }) { Text("Directions") }
                p.phone?.let { OutlinedButton(onClick = { open(ctx, Uri.parse("tel:" + it.filter { c -> c.isDigit() || c == '+' }), Intent.ACTION_DIAL) }) { Text("Call") } }
                p.website?.let { OutlinedButton(onClick = { open(ctx, Uri.parse(if (it.startsWith("http")) it else "https://$it"), Intent.ACTION_VIEW) }) { Text("Website") } }
            }
        }
    }
}

fun distanceText(km: Double) = if (km < 1) "${Math.round(km * 1000)} m" else "%.1f km".format(km)

private fun open(ctx: Context, uri: Uri, action: String) {
    runCatching { ctx.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
