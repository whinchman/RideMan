package com.two17industries.rideman

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
        RidemanTheme(theme = settings.theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RidemanNav(vm = vm, onRideActiveChanged = ::keepScreenOn)
            }
        }
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
