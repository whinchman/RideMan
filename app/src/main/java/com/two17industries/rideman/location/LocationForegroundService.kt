package com.two17industries.rideman.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.two17industries.rideman.R
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.dash.DashBroadcaster
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.hrm.HrmBleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var broadcaster: DashBroadcaster? = null
    private var hrm: HrmBleClient? = null

    // Synchronous idempotence guard for broadcaster/HRM client creation. onStartCommand can be
    // re-entered (e.g. a double-tap of "start ride" fires startForegroundService twice on an
    // already-running service); settings.first() suspends, so a null-check on `broadcaster` or
    // `hrm` from inside the launch below is itself racy — both launches could pass the check
    // before either assigns. This flag must be set synchronously, before the coroutine
    // suspends, to actually prevent a second DashBroadcaster (and thus a second 1 Hz GATT
    // writer) or a second HrmBleClient from being created.
    private var dashRequested = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            LocationBus.publish(
                LocationSample(
                    epochMillis = loc.time,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    // null (not 0f) when the fix carries no Doppler speed — RideTracker treats
                    // unknown speed as "not stationary", so a speed-less fix is never gated out.
                    speedMps = if (loc.hasSpeed()) loc.speed else null,
                    headingDeg = if (loc.hasBearing()) loc.bearing else 0f,
                    gpsAltitudeM = if (loc.hasAltitude()) loc.altitude else null,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bluetoothGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        val fgsType = if (bluetoothGranted) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        try {
            startForeground(NOTIF_ID, buildNotification(), fgsType)
        } catch (e: Exception) {
            // Fall back to location-only if the connectedDevice type is rejected,
            // so core ride tracking never fails to start because of the dash feature.
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        requestUpdates()
        if (!dashRequested) {
            dashRequested = true
            scope.launch {
                val settings = SettingsStore(applicationContext).settings.first()
                if (settings.dashEnabled) {
                    broadcaster = DashBroadcaster(applicationContext, scope).also { it.start() }
                }
                if (settings.hrmEnabled) {
                    hrm = HrmBleClient(applicationContext, scope).also { it.start(settings.hrmAddress) }
                }
            }
        }
        // START_NOT_STICKY: a ride is a user-driven session. If the process is killed,
        // do NOT silently resurrect a headless tracking service with no UI to stop it.
        return START_NOT_STICKY
    }

    private fun requestUpdates(): Boolean {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        return try {
            client.requestLocationUpdates(request, callback, mainLooper)
            true
        } catch (e: SecurityException) {
            stopSelf()
            false
        }
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        broadcaster?.stop()
        broadcaster = null
        hrm?.stop()
        hrm = null
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ride tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Rideman")
            .setContentText("Recording your ride")
            .setSmallIcon(R.drawable.ic_ride)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "ride_tracking"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.startForegroundService(intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }
}
