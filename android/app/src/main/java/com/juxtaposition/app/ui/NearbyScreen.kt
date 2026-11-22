package com.juxtaposition.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.Place
import com.juxtaposition.app.ViewMode

private val RADII = listOf(500 to "500 m", 1000 to "1 km", 2000 to "2 km", 5000 to "5 km")
private val TYPES = listOf<Pair<String?, String>>(null to "All", "restaurant" to "🍽️  Restaurants", "fast_food" to "🍔  Fast food", "cafe" to "☕  Cafes")

@Composable
fun NearbyScreen(vm: AppViewModel) {
    NearbyContent(
        places = vm.visiblePlaces, selected = vm.selected, radiusM = vm.radiusM, typeFilter = vm.typeFilter,
        viewMode = vm.viewMode, loading = vm.loading,
        onRadius = vm::setRadius, onType = vm::updateTypeFilter, onMode = vm::updateViewMode, onSelect = vm::select,
    )
}

/** Stateless so it can be previewed / screenshot-tested with fixed data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyContent(
    places: List<Place>, selected: Place?, radiusM: Int, typeFilter: String?, viewMode: ViewMode, loading: Boolean,
    onRadius: (Int) -> Unit, onType: (String?) -> Unit, onMode: (ViewMode) -> Unit, onSelect: (Place?) -> Unit,
    animateRadar: Boolean = true,
) {
    Column(Modifier.fillMaxSize()) {
        // view switcher
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val modes = listOf(
                Triple(ViewMode.LIST, "List", Icons.AutoMirrored.Filled.ViewList),
                Triple(ViewMode.RADAR_2D, "Radar", Icons.Default.Radar),
                Triple(ViewMode.RADAR_3D, "3D", Icons.Default.ViewInAr),
            )
            modes.forEachIndexed { i, (m, label, icon) ->
                SegmentedButton(
                    selected = viewMode == m, onClick = { onMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    icon = { Icon(icon, null, Modifier.size(18.dp)) },
                    label = { Text(label) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADII.forEach { (m, label) -> Chip(label, radiusM == m) { onRadius(m) } }
            Box(Modifier.align(Alignment.CenterVertically).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            TYPES.forEach { (t, label) -> Chip(label, typeFilter == t) { onType(t) } }
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && places.isEmpty() -> SkeletonList()
                places.isEmpty() -> EmptyState()
                viewMode == ViewMode.LIST -> PlaceList(places, selected, onSelect)
                else -> RadarCard(places, selected, radiusM, viewMode == ViewMode.RADAR_3D, onSelect, animateRadar)
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) { selected?.let { PlaceSheet(it, onClose = { onSelect(null) }) } }
        if (selected == null) {
            Text(
                "Map data © OpenStreetMap contributors • straight-line distances",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun RadarCard(places: List<Place>, selected: Place?, radiusM: Int, threeD: Boolean, onSelect: (Place?) -> Unit, animate: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)) {
            RadarView(places, selected, radiusM, threeD, onSelect, Modifier.fillMaxSize(), animate)
            Text(
                if (threeD) "Drag to spin • tap a pin" else "Tap a dot for details",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                color = Color(0xFF8394AA), style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            listOf("restaurant" to "Restaurant", "fast_food" to "Fast food", "cafe" to "Cafe").forEach { (t, l) ->
                Box(Modifier.size(9.dp).clip(CircleShape).background(typeColor(t)))
                Text(" $l", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
            }
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF4DA3FF)))
            Text(" You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceList(places: List<Place>, selected: Place?, onSelect: (Place) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(places, key = { it.id }) { PlaceCard(it, it.id == selected?.id) { onSelect(it) } }
    }
}

@Composable
private fun PlaceCard(p: Place, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, label = "card",
    )
    Card(
        onClick = onClick, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.type, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(p.typeLabel, p.cuisine).joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (p.vegetarian != null) {
                    Text(if (p.vegetarian == "only") "🌱 Pure veg" else "🌱 Veg options", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.width(8.dp))
            DistancePill(p)
        }
    }
}

@Composable
private fun Avatar(type: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(typeColor(type).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(typeEmoji(type), fontSize = (size * 0.46f).sp)
    }
}

@Composable
private fun DistancePill(p: Place) {
    val c = distanceBandColor(p.distanceKm, MaterialTheme.colorScheme.onSurfaceVariant)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Navigation, null, Modifier.size(14.dp).rotate(p.bearingDeg.toFloat()), tint = c)
            Spacer(Modifier.width(4.dp))
            Text(distanceText(p.distanceKm), color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text("${p.direction} • ${p.walkMinutes} min walk", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaceSheet(p: Place, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, shadowElevation = 16.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.type, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.typeLabel, p.cuisine, p.vegetarian?.let { if (it == "only") "Pure veg" else "Veg options" }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), distanceText(p.distanceKm), "away", distanceBandColor(p.distanceKm, MaterialTheme.colorScheme.onSurface))
                Stat(Modifier.weight(1f), p.direction, "bearing ${p.bearingDeg}°", MaterialTheme.colorScheme.onSurface, arrow = p.bearingDeg)
                Stat(Modifier.weight(1f), "~${p.walkMinutes} min", "on foot", MaterialTheme.colorScheme.onSurface)
            }
            p.address?.let { InfoRow(Icons.Default.Place, it) }
            p.openingHours?.let { InfoRow(Icons.Default.Schedule, it) }
            Row(Modifier.padding(top = 12.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { open(ctx, Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.name)})"), Intent.ACTION_VIEW) },
                    Modifier.weight(1f).height(48.dp),
                ) { Icon(Icons.Default.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Directions") }
                p.phone?.let {
                    FilledTonalIconButton(onClick = { open(ctx, Uri.parse("tel:" + it.filter { c -> c.isDigit() || c == '+' }), Intent.ACTION_DIAL) }, Modifier.size(48.dp)) { Icon(Icons.Default.Phone, "Call") }
                }
                p.website?.let {
                    FilledTonalIconButton(onClick = { open(ctx, Uri.parse(if (it.startsWith("http")) it else "https://$it"), Intent.ACTION_VIEW) }, Modifier.size(48.dp)) { Icon(Icons.Default.Language, "Website") }
                }
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, caption: String, color: Color, arrow: Int? = null) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrow != null) { Icon(Icons.Default.Navigation, null, Modifier.size(15.dp).rotate(arrow.toFloat()), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = color)
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(top = 10.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SkeletonList() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().alpha(a).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🧭", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("Nothing around here yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            "Check the location above is where you really are, try a bigger radius, or tap the location button to refresh. OpenStreetMap coverage varies by area.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun open(ctx: Context, uri: Uri, action: String) {
    runCatching { ctx.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
