package com.juxtaposition.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juxtaposition.app.AppViewModel

@Composable
fun AppRoot(vm: AppViewModel, onRequestLocation: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long); vm.dismissError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Header(
                subtitle = vm.origin?.let { "%.4f, %.4f  •  %d place%s".format(it.first, it.second, vm.visiblePlaces.size, if (vm.visiblePlaces.size == 1) "" else "s") }
                    ?: "Finding your location…",
                loading = vm.loading, onRefresh = onRequestLocation, onSettings = { showSettings = true },
            )
            if (vm.origin == null && !vm.loading) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("📍", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.locationDenied) "Location permission is needed to find restaurants near you." else "Finding your location…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestLocation) { Text(if (vm.locationDenied) "Allow location" else "Retry") }
                }
            } else {
                NearbyScreen(vm)
            }
        }
    }

    if (showSettings) SettingsDialog(vm.apiUrl, onDismiss = { showSettings = false }) { vm.saveApiUrl(it); showSettings = false }
}

@Composable
internal fun Header(subtitle: String, loading: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nearby Eats", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Default.MyLocation, "Refresh my location") }
            FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server URL", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Your Nearby Eats API (the Render service URL).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
