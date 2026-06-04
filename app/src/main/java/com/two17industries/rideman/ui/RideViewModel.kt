package com.two17industries.rideman.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.RideTracker
import com.two17industries.rideman.data.RidemanDatabase
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.RideRepository
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.data.PlanLoader
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.location.LocationBus
import com.two17industries.rideman.location.LocationForegroundService
import com.two17industries.rideman.sensor.SensorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    /** Parsed once at startup; null if the asset is missing/malformed (plan features disable). */
    val plan: Plan? = runCatching { PlanLoader.load(app) }
        .onFailure { Log.w("RideViewModel", "plan.json failed to load; plan features disabled", it) }
        .getOrNull()

    /** All rides, newest first, for the History screen. */
    val allRides: StateFlow<List<RideEntity>> =
        repo.allRides().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Derived plan progress, or null if there is no loaded plan. */
    val progress: StateFlow<PlanProgress?> =
        repo.planTaggedRides()
            .map { rides ->
                plan?.let { p ->
                    PlanProgress(p, rides.mapNotNull { r ->
                        r.planRideId?.let { PlanAttempt(it, r.distanceM) }
                    })
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Plan slot for the in-progress ride, or null for a free ride. */
    private var activePlanRideId: String? = null

    val settings: StateFlow<RidemanSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, RidemanSettings())

    private val _ui = MutableStateFlow(RideUiState())
    val ui: StateFlow<RideUiState> = _ui.asStateFlow()

    private var tracker: RideTracker? = null
    private var startMillis: Long = 0L
    private val track = mutableListOf<LocationSample>()
    private var lastSummary: RideSummary? = null
    private val collectorJobs = mutableListOf<Job>()

    // Altitude fusion: the barometer gives responsive *relative* altitude (standard
    // atmosphere), GPS gives a noisy but *absolute* reference. We learn the offset
    // between them from GPS fixes and display barometric altitude + offset, so the
    // number is both stable and anchored to true elevation.
    private var baroAltitudeRaw: Double? = null
    private var lastGpsAltitude: Double? = null
    private var altitudeOffset: Double = 0.0
    private var altitudeOffsetInit = false

    fun startRide(planRideId: String? = null) {
        activePlanRideId = planRideId
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        startMillis = System.currentTimeMillis()
        tracker = RideTracker(startMillis)
        track.clear()
        baroAltitudeRaw = null
        lastGpsAltitude = null
        altitudeOffset = 0.0
        altitudeOffsetInit = false
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
            sample.gpsAltitudeM?.let { gpsAlt ->
                lastGpsAltitude = gpsAlt
                baroAltitudeRaw?.let { baro ->
                    val newOffset = gpsAlt - baro
                    altitudeOffset = if (!altitudeOffsetInit) {
                        altitudeOffsetInit = true
                        newOffset
                    } else {
                        // Low-pass so a single bad GPS altitude can't jerk the reading.
                        altitudeOffset * 0.8 + newOffset * 0.2
                    }
                }
            }
            _ui.value = _ui.value.copy(
                speedMps = sample.speedMps,
                distanceM = t.distanceM,
                headingDeg = sample.headingDeg,
                altitudeM = displayedAltitude(),
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
                baroAltitudeRaw = alt
                _ui.value = _ui.value.copy(altitudeM = displayedAltitude())
            }
        },
    )

    /** Best available altitude: GPS-anchored barometer, raw barometer, or GPS alone. */
    private fun displayedAltitude(): Double {
        val baro = baroAltitudeRaw
        return when {
            baro != null && altitudeOffsetInit -> baro + altitudeOffset
            baro != null -> baro
            else -> lastGpsAltitude ?: 0.0
        }
    }

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
        viewModelScope.launch { repo.saveRide(summary, snapshot, activePlanRideId) }
    }

    fun saveSettings(updated: RidemanSettings) {
        viewModelScope.launch { settingsStore.save(updated) }
    }
}
