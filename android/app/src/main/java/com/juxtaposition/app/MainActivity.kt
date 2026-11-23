package com.juxtaposition.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.juxtaposition.app.ui.AppRoot
import com.juxtaposition.app.ui.JuxtaTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val perms = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasLocationPermission() = perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    override fun onStart() {
        super.onStart()
        if (hasLocationPermission()) vm.startLocationUpdates() // follow the user while the app is on screen
    }

    override fun onStop() {
        super.onStop()
        vm.stopLocationUpdates() // don't keep the GPS running in the background
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JuxtaTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                    vm.onPermissionResult(granted.values.any { it })
                }
                val request = { launcher.launch(perms) }
                LaunchedEffect(Unit) {
                    if (vm.origin == null) { if (hasLocationPermission()) vm.onPermissionResult(true) else request() }
                }
                AppRoot(vm, onRequestLocation = { if (hasLocationPermission()) vm.locate() else request() })
            }
        }
    }
}
