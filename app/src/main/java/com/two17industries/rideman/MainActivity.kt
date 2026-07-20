package com.two17industries.rideman

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.two17industries.rideman.data.RideOrientation
import com.two17industries.rideman.ui.RideViewModel
import com.two17industries.rideman.ui.RidemanNav
import com.two17industries.rideman.ui.theme.RidemanTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    private var rideViewModel: RideViewModel? = null

    private val _permissionsGranted = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /**
     * Whether both BLE permissions are held, threaded down to Settings so it can offer GRANT.
     *
     * Settings cannot infer this from HrmStatus: that only reports NO_PERMISSION while a BLE
     * client is actually running, and no client runs while the rider is sitting on the Settings
     * screen. This flow is the only signal available at the point the rider is in a position to
     * act on it — and being a flow, the GRANT affordance disappears the moment the permission is
     * granted, which a checkSelfPermission read inside composition would not do.
     */
    private val blePermissionsGranted: StateFlow<Boolean> = _permissionsGranted
        .map { granted -> BLE_PERMISSIONS.all { granted[it] == true } }
        .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Previously empty: a denial was silently swallowed, and the BLE clients degraded with
        // no explanation. The launcher's own result is not used — checkSelfPermission is ground
        // truth and agrees with it here, while also covering permissions granted outside the app.
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissionState()
        permissionLauncher.launch(REQUESTED_PERMISSIONS)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        // Catches a permission granted from the system Settings app while we were backgrounded,
        // which produces no launcher result.
        refreshPermissionState()
        rideViewModel?.refreshStravaConnection()
    }

    private fun refreshPermissionState() {
        _permissionsGranted.value = REQUESTED_PERMISSIONS.associateWith {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
                    blePermissionsGranted = blePermissionsGranted,
                    onRideActiveChanged = { active ->
                        rideActive = active
                        if (active) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                    onRequestBlePermissions = { permissionLauncher.launch(BLE_PERMISSIONS) },
                )
            }
        }
    }

    private companion object {
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ) + BLE_PERMISSIONS
    }
}
