package com.juxtaposition.app.ui

import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import com.juxtaposition.app.BuildConfig
import com.juxtaposition.app.ApkInstaller
import com.juxtaposition.app.UpdateInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.juxtaposition.app.GeoResult
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
    var searchOpen by remember { mutableStateOf(false) }
    var showExplore by remember { mutableStateOf(false) }
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
                subtitle = vm.origin?.let { o ->
                    val n = "%d place%s".format(vm.visiblePlaces.size, if (vm.visiblePlaces.size == 1) "" else "s")
                    vm.exploring?.let { "Near ${it.label.substringBefore(',')}  •  $n" } ?: "%.4f, %.4f  •  %s".format(o.first, o.second, n)
                }
                    ?: "Finding your location…",
                loading = vm.loading, onRefresh = onRequestLocation, onSettings = { showSettings = true }, onExplore = { showExplore = true },
                searchActive = searchOpen || vm.query.isNotEmpty(),
                onSearch = { searchOpen = !searchOpen; if (!searchOpen) vm.updateQuery("") },
            )
            vm.exploring?.let { spot ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Exploring ${spot.label}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
                        TextButton(onClick = { vm.locate() }) { Text("My location") }
                    }
                }
            }
            vm.update?.let { info ->
                val ctx = LocalContext.current
                val inBrowser = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }
                val install = {
                    vm.readyApk?.let { apk ->
                        if (ApkInstaller.canInstall(ctx)) runCatching { ApkInstaller.install(ctx, apk) }.onFailure { inBrowser() }
                        else runCatching { ApkInstaller.askPermission(ctx) }.onFailure { inBrowser() }
                    }
                    Unit
                }
                UpdateBanner(
                    info, downloadPct = vm.downloadPct,
                    onDownload = { if (vm.readyApk != null) install() else vm.downloadUpdate(onFail = inBrowser) },
                    onLater = vm::dismissUpdate,
                )
            }
            vm.notice?.let { msg ->
                Text(
                    msg, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
                )
            }
            AnimatedVisibility(searchOpen && vm.origin != null) {
                OutlinedTextField(
                    value = vm.query, onValueChange = vm::updateQuery, singleLine = true,
                    placeholder = { Text("Search name or cuisine") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.updateQuery("") }) { Icon(Icons.Default.Close, "Clear") } },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
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

    if (showExplore) ExploreDialog(
        results = vm.geoResults, busy = vm.geoBusy, message = vm.geoMessage,
        onSearch = vm::searchArea,
        onPick = { vm.explore(it); showExplore = false },
        onDismiss = { showExplore = false; vm.clearAreaSearch() },
    )

    if (showSettings) SettingsDialog(
        updateMessage = vm.updateMessage, onCheckUpdates = { vm.checkForUpdates(manual = true) },
        onDismiss = { showSettings = false; vm.clearUpdateMessage() },
    )
}

@Composable
internal fun Header(
    subtitle: String, loading: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit,
    searchActive: Boolean = false, onSearch: () -> Unit = {}, onExplore: () -> Unit = {},
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nearby Eats", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onSearch) { Icon(if (searchActive) Icons.Default.Close else Icons.Default.Search, if (searchActive) "Close search" else "Search") }
            FilledTonalIconButton(onClick = onExplore) { Icon(Icons.Default.Place, "Look up another area") }
            FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Default.MyLocation, "Refresh my location") }
            FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ExploreDialog(
    results: List<GeoResult>?, busy: Boolean, message: String?,
    onSearch: (String) -> Unit, onPick: (GeoResult) -> Unit, onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Look up another area", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("See restaurants around a place you are going to. Only what you type is sent to OpenStreetMap, never where you are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                    placeholder = { Text("e.g. Indiranagar, Bengaluru") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                results?.forEach { r ->
                    Text(r.label, Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSearch(text) }, enabled = text.isNotBlank() && !busy) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsDialog(updateMessage: String?, onCheckUpdates: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onCheckUpdates, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Check for updates") }
                updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                Text("Places come from OpenStreetMap, asked directly from your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun UpdateBanner(info: UpdateInfo, downloadPct: Int? = null, onDownload: () -> Unit, onLater: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update available", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when {
                        downloadPct == null -> "Version ${info.version} is ready to download."
                        downloadPct >= AppViewModel.DOWNLOAD_READY -> "Version ${info.version} downloaded. Tap Install to finish."
                        downloadPct >= 0 -> "Downloading version ${info.version}… $downloadPct%"
                        else -> "Downloading version ${info.version}…"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            val busy = downloadPct != null && downloadPct < AppViewModel.DOWNLOAD_READY
            TextButton(onClick = onLater, enabled = !busy) { Text("Later") }
            Button(onClick = onDownload, enabled = !busy) { Text(if (downloadPct == AppViewModel.DOWNLOAD_READY) "Install" else "Download") }
            Spacer(Modifier.width(4.dp))
        }
    }
}
