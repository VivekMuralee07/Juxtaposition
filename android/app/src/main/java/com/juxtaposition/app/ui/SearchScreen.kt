package com.juxtaposition.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.Restaurant

@Composable
fun SearchScreen(vm: AppViewModel, onRequestLocation: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = vm.query, onValueChange = vm::onQueryChange, singleLine = true,
            placeholder = { Text("Search restaurant or cuisine") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = onRequestLocation,
                label = {
                    Text(
                        vm.locationNote ?: if (vm.usingDeviceLocation) "Using your location"
                        else "Using demo location (Bengaluru) - tap to use mine"
                    )
                },
                leadingIcon = { Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp)) },
            )
        }
        if (!vm.loading && vm.restaurants.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No restaurants found", color = Muted) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(vm.restaurants, key = { it.id }) { RestaurantCard(it) { vm.openRestaurant(it) } }
        }
    }
}

@Composable
private fun RestaurantCard(r: Restaurant, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(16.dp))
                Text(" ${r.rating}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("${r.cuisine} • ${r.area} • ${r.distanceKm} km", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("On ${r.platforms.size} apps: " + r.platforms.joinToString { it.replaceFirstChar(Char::uppercase) },
                style = MaterialTheme.typography.labelSmall, color = Green)
        }
    }
}
