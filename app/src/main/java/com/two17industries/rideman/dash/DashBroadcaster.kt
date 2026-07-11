package com.two17industries.rideman.dash

import android.content.Context
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideTracker
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.location.LocationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the T-Display during a ride: owns its own RideTracker fed from LocationBus (so its
 * distance matches the ViewModel's identical accumulation), and every second builds a
 * telemetry packet and writes it to the board. Runs inside the location foreground service.
 */
class DashBroadcaster(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val client = DashBleClient(appContext)
    private val settingsStore = SettingsStore(appContext)

    private val startMillis = System.currentTimeMillis()
    private val tracker = RideTracker(startMillis)
    @Volatile private var latest: LocationSample? = null
    @Volatile private var unitsUS: Boolean = true
    @Volatile private var themeIndex: Int = 0

    private val jobs = mutableListOf<Job>()

    fun start() {
        client.start()
        jobs += scope.launch {
            LocationBus.latest.collect { sample ->
                sample ?: return@collect
                tracker.add(sample)
                latest = sample
            }
        }
        jobs += scope.launch {
            settingsStore.settings.map { it.units }.collect { unitsUS = it == UnitSystem.AMERICAN }
        }
        jobs += scope.launch {
            settingsStore.settings.map { it.theme.ordinal }.collect { themeIndex = it }
        }
        jobs += scope.launch {
            while (isActive) {
                val elapsedSec = (System.currentTimeMillis() - startMillis) / 1000
                val telemetry = TelemetryBuilder.build(latest, tracker.distanceM, elapsedSec, unitsUS, themeIndex)
                client.write(TelemetryPacket.encode(telemetry))
                delay(1000)
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        client.stop()
    }
}
