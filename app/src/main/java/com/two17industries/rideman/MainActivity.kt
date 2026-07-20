package com.two17industries.rideman

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.two17industries.rideman.data.RideOrientation
import com.two17industries.rideman.ui.RideViewModel
import com.two17industries.rideman.ui.RidemanNav
import com.two17industries.rideman.ui.theme.RidemanTheme

class MainActivity : ComponentActivity() {

    private var rideViewModel: RideViewModel? = null

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissions.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        )
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        rideViewModel?.refreshStravaConnection()
    }

    @Composable
    private fun App() {
        val vm: RideViewModel = viewModel()
        rideViewModel = vm
        val settings by vm.settings.collectAsState()
        var rideActive by remember { mutableStateOf(false) }

        // Orientation is applied from the persisted setting, not from how the phone is held, and
        // re-applied whenever the rotate button flips it. The manifest declares
        // configChanges="orientation|..." so this recomposes without recreating the activity.
        LaunchedEffect(rideActive, settings.rideOrientation) {
            requestedOrientation = if (!rideActive) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else when (settings.rideOrientation) {
                RideOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                RideOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        RidemanTheme(theme = settings.theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RidemanNav(
                    vm = vm,
                    onRideActiveChanged = { active ->
                        rideActive = active
                        if (active) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                )
            }
        }
    }
}
