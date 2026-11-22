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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, onRequestLocation: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long); vm.dismissError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Eats", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRequestLocation) { Icon(Icons.Default.MyLocation, "Refresh my location") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.origin == null && !vm.loading) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (vm.locationDenied) "Location permission is needed to find restaurants near you."
                        else "Finding your location…",
                        color = Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRequestLocation) { Text(if (vm.locationDenied) "Allow location" else "Retry") }
                }
            } else {
                NearbyScreen(vm)
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showSettings) SettingsDialog(vm.apiUrl, onDismiss = { showSettings = false }) { vm.saveApiUrl(it); showSettings = false }
}

@Composable
private fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server URL") },
        text = {
            Column {
                Text("Your Nearby Eats API (the Render service URL).", color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
