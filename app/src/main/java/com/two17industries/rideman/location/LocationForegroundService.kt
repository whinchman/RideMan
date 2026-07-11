package com.two17industries.rideman.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.two17industries.rideman.R
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.dash.DashBroadcaster
import com.two17industries.rideman.data.SettingsStore
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

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            LocationBus.publish(
                LocationSample(
                    epochMillis = loc.time,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    speedMps = if (loc.hasSpeed()) loc.speed else 0f,
                    headingDeg = if (loc.hasBearing()) loc.bearing else 0f,
                    gpsAltitudeM = if (loc.hasAltitude()) loc.altitude else null,
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        requestUpdates()
        scope.launch {
            if (SettingsStore(applicationContext).settings.first().dashEnabled) {
                broadcaster = DashBroadcaster(applicationContext, scope).also { it.start() }
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
