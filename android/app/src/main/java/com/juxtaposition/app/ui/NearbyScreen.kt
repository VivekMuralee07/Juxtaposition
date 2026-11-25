package com.juxtaposition.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.CuisineChip
import com.juxtaposition.app.SortMode
import com.juxtaposition.app.DistanceBands
import com.juxtaposition.app.RadarZoom
import com.juxtaposition.app.emoji
import com.juxtaposition.app.HoursStatus
import com.juxtaposition.app.Place
import com.juxtaposition.app.PlaceLinks
import com.juxtaposition.app.ViewMode
import com.juxtaposition.app.hoursStatus

private val RADII = listOf(500 to "500 m", 1000 to "1 km")
private val TYPES = listOf<Pair<String?, String>>(null to "All", "restaurant" to "🍽️  Restaurants", "fast_food" to "🍔  Fast food", "cafe" to "☕  Cafes")

/** Everything the filter rows show. */
data class FilterUi(
    val radiusM: Int = 1000, val typeFilter: String? = null, val cuisine: String? = null, val cuisineChips: List<CuisineChip> = emptyList(),
    val openNow: Boolean = false, val vegOnly: Boolean = false, val savedOnly: Boolean = false, val savedCount: Int = 0,
    val sort: SortMode = SortMode.NEAREST,
)

/** Everything the screen can ask for. Defaults do nothing, so previews and tests only name what they need. */
class NearbyActions(
    val onRadius: (Int) -> Unit = {}, val onType: (String?) -> Unit = {}, val onCuisine: (String?) -> Unit = {},
    val onOpenNow: (Boolean) -> Unit = {}, val onVegOnly: (Boolean) -> Unit = {}, val onSaved: (Boolean) -> Unit = {},
    val onSort: (SortMode) -> Unit = {}, val onMode: (ViewMode) -> Unit = {}, val onSelect: (Place?) -> Unit = {},
    val onToggleHeading: () -> Unit = {}, val onRefresh: () -> Unit = {}, val onPick: () -> Unit = {},
    val onToggleFavorite: (Place) -> Unit = {},
)

@Composable
fun NearbyScreen(vm: AppViewModel) {
    val savedPlaces = vm.favorites
    NearbyContent(
        places = vm.visiblePlaces, selected = vm.selected, viewMode = vm.viewMode, loading = vm.loading && !vm.hasData,
        filters = FilterUi(
            vm.radiusM, vm.typeFilter, vm.cuisine, vm.cuisineChips, vm.openNow, vm.vegOnly, vm.savedOnly, savedPlaces.size, vm.sort,
        ),
        actions = NearbyActions(
            onRadius = vm::setRadius, onType = vm::updateTypeFilter, onCuisine = vm::updateCuisine, onOpenNow = vm::updateOpenNow,
            onVegOnly = vm::updateVegOnly, onSaved = vm::updateSavedOnly, onSort = vm::updateSort, onMode = vm::updateViewMode,
            onSelect = vm::select, onToggleHeading = vm::toggleHeadingUp, onRefresh = { vm.locate() }, onPick = vm::pickForMe,
            onToggleFavorite = vm::toggleFavorite,
        ),
        favoriteIds = vm.favoriteIds, pickedId = vm.pickedId, refreshing = vm.loading,
        dataLine = vm.dataLine(), dataLineShort = vm.dataLineShort(),
        compass = CompassUi(vm.headingDeg, vm.headingUp, vm.compassAvailable, vm.compassLow, explore = vm.exploring != null),
    )
}

/** Stateless so it can be previewed / screenshot-tested with fixed data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyContent(
    places: List<Place>, selected: Place?, viewMode: ViewMode, loading: Boolean,
    filters: FilterUi, actions: NearbyActions,
    favoriteIds: Set<String> = emptySet(), pickedId: String? = null, refreshing: Boolean = false,
    animateRadar: Boolean = true, dataLine: String? = null, dataLineShort: String? = null,
    compass: CompassUi = CompassUi(),
) {
    Column(Modifier.fillMaxSize()) {
        // view switcher
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val modes = listOf(
                Triple(ViewMode.LIST, "List", Icons.AutoMirrored.Filled.ViewList),
                Triple(ViewMode.RADAR, "Radar", Icons.Default.Radar),
            )
            modes.forEachIndexed { i, (m, label, icon) ->
                SegmentedButton(
                    selected = viewMode == m, onClick = { actions.onMode(m) },
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
            Chip("🟢  Open now", filters.openNow) { actions.onOpenNow(!filters.openNow) }
            Chip("⭐  Saved${if (filters.savedCount > 0) " ${filters.savedCount}" else ""}", filters.savedOnly) { actions.onSaved(!filters.savedOnly) }
            Box(Modifier.align(Alignment.CenterVertically).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            RADII.forEach { (m, label) -> Chip(label, filters.radiusM == m) { actions.onRadius(m) } }
            Box(Modifier.align(Alignment.CenterVertically).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Chip("🌱  Veg", filters.vegOnly) { actions.onVegOnly(!filters.vegOnly) }
            TYPES.forEach { (t, label) -> Chip(label, filters.typeFilter == t) { actions.onType(t) } }
        }
        // "What are you craving?": the cuisines actually nearby, most common first
        if (filters.cuisineChips.isNotEmpty() && !filters.savedOnly) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.cuisineChips.forEach { c -> Chip("${c.emoji}  ${c.label} ${c.count}", filters.cuisine == c.label) { actions.onCuisine(c.label) } }
            }
        }
        if (filters.openNow) {
            val unknown = places.count { it.hoursStatus() is HoursStatus.Unknown }
            Text(
                if (unknown > 0) "Hiding places that are closed. $unknown of ${places.size} have no hours listed, so they may or may not be open."
                else "Hiding places that are closed.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Spacer(Modifier.height(6.dp))
        if (viewMode == ViewMode.LIST && places.isNotEmpty()) SortRow(places.size, filters.sort, actions.onSort)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && places.isEmpty() -> SkeletonList()
                places.isEmpty() -> EmptyState(filters.savedOnly)
                else -> Crossfade(viewMode, animationSpec = tween(220), label = "view") { mode ->
                    if (mode == ViewMode.LIST) {
                        PlaceList(
                            places, selected, favoriteIds, grouped = filters.sort == SortMode.NEAREST && !filters.savedOnly,
                            onSelect = { actions.onSelect(it) }, refreshing = refreshing, onRefresh = actions.onRefresh,
                            onPick = actions.onPick.takeIf { selected == null }, // the details card has its own "Not this one"
                        )
                    } else RadarCard(places, selected, filters.radiusM, actions.onSelect, animateRadar, compass, actions.onToggleHeading, actions.onPick.takeIf { selected == null })
                }
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            selected?.let {
                PlaceSheet(
                    it, picked = it.id == pickedId, isFavorite = it.id in favoriteIds,
                    onToggleFavorite = { actions.onToggleFavorite(it) }, onPickAgain = actions.onPick, onClose = { actions.onSelect(null) },
                )
            }
        }
        if (selected == null) {
            var expanded by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().clickable(enabled = dataLine != null) { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val line = if (expanded) dataLine else dataLineShort
                line?.let {
                    Text(
                        if (expanded) it else "$it  ⓘ", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Map data © OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun SortRow(count: Int, sort: SortMode, onSort: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$count place${if (count == 1) "" else "s"}", Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { open = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Sort: ${sort.label}", style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortMode.values().forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.label) }, onClick = { onSort(m); open = false },
                        leadingIcon = { if (m == sort) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
                    )
                }
            }
        }
    }
}

/** A floating "Pick for me" button. Place it inside a Box, aligned to the bottom centre. */
@Composable
private fun PickButton(modifier: Modifier, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick, modifier = modifier,
        icon = { Icon(Icons.Default.Casino, null) }, text = { Text("Pick for me") },
        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
    )
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

/** Compass state for the radar. `headingUp` is the user's choice; it only takes effect when the sensor exists. */
data class CompassUi(val headingDeg: Float? = null, val headingUp: Boolean = false, val available: Boolean = true, val lowAccuracy: Boolean = false, val explore: Boolean = false)

@Composable
private fun RadarCard(
    places: List<Place>, selected: Place?, radiusM: Int, onSelect: (Place?) -> Unit, animate: Boolean,
    compass: CompassUi, onToggleHeading: () -> Unit, onPick: (() -> Unit)?,
) {
    val headingUp = compass.headingUp && compass.available && !compass.explore
    val facing = if (headingUp) compass.headingDeg else null
    var zoom by remember { mutableFloatStateOf(1f) }
    androidx.compose.runtime.LaunchedEffect(radiusM) { zoom = 1f } // a new radius starts at the full view
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)) {
            RadarView(
                places, selected, radiusM, onSelect, Modifier.fillMaxSize(), animate,
                facingDeg = facing, showFacingCone = compass.headingDeg.takeIf { compass.available && !compass.explore && it != null },
                zoom = zoom, onZoomChange = { zoom = it },
            )
            Column(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp).fillMaxWidth(0.72f)) {
                Text(
                    when {
                        compass.explore -> "North up • around the area you searched"
                        !compass.available -> "North up • this phone has no compass sensor"
                        headingUp && compass.headingDeg == null -> "Heading up • waiting for the compass…"
                        headingUp -> "Heading up • top = the way you face"
                        else -> "North up"
                    },
                    color = Color(0xFFB7C4D6), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                )
                if (headingUp && compass.lowAccuracy) {
                    Text("Compass needs calibrating: wave the phone in a figure 8", color = Color(0xFFFFC27A), style = MaterialTheme.typography.labelSmall)
                }
                Text("Tap a dot • pinch or double-tap to zoom • faded = closed", color = Color(0xFF8394AA), style = MaterialTheme.typography.labelSmall)
            }
            if (zoom > 1.05f) {
                Surface(
                    onClick = { zoom = RadarZoom.MIN }, shape = RoundedCornerShape(50), color = Color(0xFF2B394A),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                ) {
                    Text("%.1f× • reset".format(zoom), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color(0xFFB7C4D6), style = MaterialTheme.typography.labelMedium)
                }
            }
            onPick?.let { PickButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), it) }
            if (compass.available && !compass.explore) {
                FilledTonalIconButton(
                    onClick = onToggleHeading, Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF2B394A), contentColor = if (headingUp) Color(0xFFFF8A4C) else Color(0xFFB7C4D6)),
                ) {
                    Icon(if (headingUp) Icons.Default.Navigation else Icons.Default.Explore, if (headingUp) "Switch to north up" else "Switch to heading up")
                }
            }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlaceList(
    places: List<Place>, selected: Place?, favoriteIds: Set<String>, grouped: Boolean,
    onSelect: (Place) -> Unit, refreshing: Boolean, onRefresh: () -> Unit, onPick: (() -> Unit)?,
) {
    val groups = remember(places, grouped) { if (grouped) places.groupBy { DistanceBands.index(it.distanceKm) }.toSortedMap() else emptyMap() }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 84.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (grouped) {
                groups.forEach { (band, inBand) ->
                    stickyHeader(key = "band$band") { BandHeader(DistanceBands.label(band), inBand.size) }
                    items(inBand, key = { it.id }) { p ->
                        // items slide into place / fade when the filters change or you walk past one
                        Box(Modifier.animateItem().padding(horizontal = 16.dp)) { PlaceCard(p, p.id == selected?.id, p.id in favoriteIds) { onSelect(p) } }
                    }
                }
            } else {
                items(places, key = { it.id }) { p ->
                    Box(Modifier.animateItem().padding(horizontal = 16.dp)) { PlaceCard(p, p.id == selected?.id, p.id in favoriteIds) { onSelect(p) } }
                }
            }
        }
        onPick?.let { PickButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), it) }
    }
}

@Composable
private fun BandHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun PlaceCard(p: Place, selected: Boolean, saved: Boolean, onClick: () -> Unit) {
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
            Avatar(p, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (saved) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Star, "Saved", Modifier.size(16.dp), tint = Color(0xFFF2A007)) }
                }
                Text(
                    listOfNotNull(p.typeLabel, p.cuisine).joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusText(p)
                if (p.vegetarian != null) {
                    Text(if (p.vegetarian == "only") "🌱 Pure veg" else "🌱 Veg options", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.width(8.dp))
            DistancePill(p)
        }
    }
}

/** "Open · closes 23:00" in green, "Closed · opens 10:00" in red; nothing when the hours are unknown. */
@Composable
private fun StatusText(p: Place) {
    when (val st = p.hoursStatus()) {
        is HoursStatus.Open -> Text(st.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (st.closingSoon) Color(0xFFD08A00) else Color(0xFF2F9E6B))
        is HoursStatus.Closed -> Text(st.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
        HoursStatus.Unknown -> Text("Hours not listed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

@Composable
private fun Avatar(place: Place, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(typeColor(place.type).copy(alpha = 0.16f)).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(place.emoji(), fontSize = (size * 0.46f).sp)
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
private fun PlaceSheet(p: Place, picked: Boolean, isFavorite: Boolean, onToggleFavorite: () -> Unit, onPickAgain: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, shadowElevation = 16.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 12.dp, bottom = 14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            if (picked) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 10.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎲  Picked for you", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(onClick = onPickAgain) { Text("Not this one") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.typeLabel, p.cuisine, p.vegetarian?.let { if (it == "only") "Pure veg" else "Veg options" }).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusText(p)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, if (isFavorite) "Remove from saved places" else "Save this place",
                        tint = if (isFavorite) Color(0xFFF2A007) else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = { openDirections(ctx, p) },
                    Modifier.weight(1f).height(48.dp),
                ) { Icon(Icons.Default.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Directions") }
                p.phone?.let {
                    FilledTonalIconButton(onClick = { open(ctx, Uri.parse("tel:" + it.filter { c -> c.isDigit() || c == '+' }), Intent.ACTION_DIAL) }, Modifier.size(48.dp)) { Icon(Icons.Default.Phone, "Call") }
                }
                p.website?.let {
                    FilledTonalIconButton(onClick = { open(ctx, Uri.parse(if (it.startsWith("http")) it else "https://$it"), Intent.ACTION_VIEW) }, Modifier.size(48.dp)) { Icon(Icons.Default.Language, "Website") }
                }
                FilledTonalIconButton(onClick = { share(ctx, p) }, Modifier.size(48.dp)) { Icon(Icons.Default.Share, "Share this place") }
            }
            PlaceLinks.osmEdit(p)?.let { url ->
                TextButton(onClick = { open(ctx, Uri.parse(url), Intent.ACTION_VIEW) }, Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text("Missing info? Add it on OpenStreetMap", style = MaterialTheme.typography.labelMedium)
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
private fun EmptyState(savedOnly: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (savedOnly) "⭐" else "🧭", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(if (savedOnly) "No saved places match" else "Nothing matches here", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            if (savedOnly) "Open a place and tap the ☆ to save it. Saved places stay on your phone and show up here wherever you are."
            else "Try 1 km, clear a filter, or tap the location button to refresh. OpenStreetMap coverage varies by area.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Walking navigation in Google Maps if present, else any map app's pin, else the browser. */
private fun openDirections(ctx: Context, p: Place) {
    for (url in listOf(PlaceLinks.walkingNavigation(p), PlaceLinks.mapPin(p), PlaceLinks.mapsWebDirections(p))) {
        if (runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

private fun share(ctx: Context, p: Place) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, p.name)
        putExtra(Intent.EXTRA_TEXT, PlaceLinks.shareText(p))
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share place").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun open(ctx: Context, uri: Uri, action: String) {
    runCatching { ctx.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
