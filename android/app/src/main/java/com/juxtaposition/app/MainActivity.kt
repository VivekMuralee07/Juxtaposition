package com.juxtaposition.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.juxtaposition.app.ui.AppRoot
import com.juxtaposition.app.ui.JuxtaTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JuxtaTheme {
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted -> if (granted.values.any { it }) vm.useDeviceLocation() }
                BackHandler(enabled = vm.screen != Screen.SEARCH) { vm.back() }
                AppRoot(vm, onRequestLocation = {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                })
            }
        }
    }
}
