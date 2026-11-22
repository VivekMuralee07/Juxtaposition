package com.juxtaposition.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.Screen

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
                title = {
                    Text(
                        when (vm.screen) {
                            Screen.SEARCH -> "Juxtaposition"
                            Screen.MENU -> vm.selected?.name ?: ""
                            Screen.RESULT -> "Price comparison"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (vm.screen != Screen.SEARCH) {
                        IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                Screen.SEARCH -> SearchScreen(vm, onRequestLocation)
                Screen.MENU -> MenuScreen(vm)
                Screen.RESULT -> ResultScreen(vm)
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showSettings) SettingsDialog(vm.apiUrl, onDismiss = { showSettings = false }) {
        vm.saveApiUrl(it); showSettings = false
    }
}

@Composable
private fun SettingsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server URL") },
        text = {
            Column {
                Text("Your Juxtaposition API (e.g. the Render service URL).", color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
