package com.two17industries.rideman.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.RideTracker
import com.two17industries.rideman.data.RidemanDatabase
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.RideRepository
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.location.LocationBus
import com.two17industries.rideman.location.LocationForegroundService
import com.two17industries.rideman.sensor.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RideUiState(
    val speedMps: Float = 0f,
    val distanceM: Double = 0.0,
    val headingDeg: Float = 0f,
    val altitudeM: Double = 0.0,
    val elapsedMs: Long = 0L,
)

class RideViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val sensors = SensorRepository(app)
    private val repo = RideRepository(RidemanDatabase.get(app).rideDao())

    val settings: StateFlow<RidemanSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, RidemanSettings())

    private val _ui = MutableStateFlow(RideUiState())
    val ui: StateFlow<RideUiState> = _ui.asStateFlow()

    private var tracker: RideTracker? = null
    private var startMillis: Long = 0L
    private val track = mutableListOf<LocationSample>()
    private var lastSummary: RideSummary? = null
    private val collectorJobs = mutableListOf<Job>()

    fun startRide() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        startMillis = System.currentTimeMillis()
        tracker = RideTracker(startMillis)
        track.clear()
        LocationBus.reset()
        LocationForegroundService.start(getApplication())
        collectorJobs += collectLocation()
        collectorJobs += collectSensors()
    }

    private fun collectLocation() = viewModelScope.launch {
        LocationBus.latest.collect { sample ->
            sample ?: return@collect
            val t = tracker ?: return@collect
            t.add(sample)
            track.add(sample)
            _ui.value = _ui.value.copy(
                speedMps = sample.speedMps,
                distanceM = t.distanceM,
                headingDeg = sample.headingDeg,
                elapsedMs = System.currentTimeMillis() - startMillis,
            )
        }
    }

    private fun collectSensors(): List<Job> = listOf(
        viewModelScope.launch {
            sensors.headingDegrees().collect { deg ->
                _ui.value = _ui.value.copy(headingDeg = deg)
            }
        },
        viewModelScope.launch {
            sensors.altitudeMeters().collect { alt ->
                _ui.value = _ui.value.copy(altitudeM = alt)
            }
        },
    )

    /** Stops tracking and returns the summary for the End screen. */
    fun endRide(): RideSummary {
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        LocationForegroundService.stop(getApplication())
        val summary = tracker?.summarize(System.currentTimeMillis())
            ?: RideSummary(startMillis, startMillis, 0L, 0.0, 0f, 0f)
        lastSummary = summary
        return summary
    }

    fun persistLastRide() {
        val summary = lastSummary ?: return
        val snapshot = track.toList()
        viewModelScope.launch { repo.saveRide(summary, snapshot) }
    }

    fun saveSettings(updated: RidemanSettings) {
        viewModelScope.launch { settingsStore.save(updated) }
    }
}
