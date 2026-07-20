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
import com.two17industries.rideman.strava.OkHttpStravaHttp
import com.two17industries.rideman.strava.StravaAuth
import com.two17industries.rideman.strava.StravaTokenStore
import com.two17industries.rideman.strava.StravaUploadScheduler
import com.two17industries.rideman.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    private val stravaStore = StravaTokenStore(app)
    private val stravaAuth = StravaAuth(
        clientId = BuildConfig.STRAVA_CLIENT_ID,
        clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
        loadTokens = { stravaStore.load() },
        saveTokens = { stravaStore.save(it) },
        clearTokens = { stravaStore.clear() },
        http = OkHttpStravaHttp(),
        nowEpochSec = { System.currentTimeMillis() / 1000 },
    )

    private val _stravaConnected = MutableStateFlow(stravaStore.isConnected)
    val stravaConnected: StateFlow<Boolean> = _stravaConnected.asStateFlow()

    private val _stravaAthleteName = MutableStateFlow(stravaAuth.athleteFirstName)
    val stravaAthleteName: StateFlow<String?> = _stravaAthleteName.asStateFlow()

    fun connectStravaUrl(): String = stravaAuth.authorizeUrl()

    /** Called by MainActivity.onResume after the OAuth callback runs. */
    fun refreshStravaConnection() {
        _stravaConnected.value = stravaStore.isConnected
        _stravaAthleteName.value = stravaAuth.athleteFirstName
    }

    fun disconnectStrava() {
        stravaAuth.disconnect()
        refreshStravaConnection()
    }

    fun retryUpload(rideId: Long) {
        viewModelScope.launch {
            val ride = repo.getRide(rideId) ?: return@launch
            repo.markQueued(rideId, ride)
            StravaUploadScheduler.enqueue(getApplication(), rideId)
        }
    }

    fun backfillUpload(rideIds: List<Long>) {
        viewModelScope.launch {
            for (id in rideIds) {
                val ride = repo.getRide(id) ?: continue
                repo.markQueued(id, ride)
                StravaUploadScheduler.enqueue(getApplication(), id)
            }
        }
    }

    /** Parsed once at startup; null if the asset is missing/malformed (plan features disable). */
    val plan: Plan? = runCatching { PlanLoader.load(app) }
        .onFailure { Log.w("RideViewModel", "plan.json failed to load; plan features disabled", it) }
        .getOrNull()

    /** All rides, newest first, for the History screen. */
    val allRides: StateFlow<List<RideEntity>> =
        repo.allRides().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Deletes rides and their GPS tracks. History and plan progress recompute from their Flows. */
    fun deleteRides(rideIds: List<Long>) {
        if (rideIds.isEmpty()) return
        viewModelScope.launch { repo.deleteRides(rideIds) }
    }

    /** Derived plan progress, or null if there is no loaded plan. */
    val progress: StateFlow<PlanProgress?> =
        repo.planTaggedRides()
            .map { rides ->
                plan?.let { p ->
                    PlanProgress(p, rides.mapNotNull { r ->
                        r.planRideId?.let { PlanAttempt(r.id, it, r.distanceM) }
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
        _ui.value = RideUiState()
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
        collectorJobs += collectElapsed()
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
                speedMps = sample.speedMps ?: 0f,
                distanceM = t.distanceM,
                headingDeg = sample.headingDeg,
                altitudeM = displayedAltitude(),
            )
        }
    }

    /**
     * Drives elapsedMs at 1 Hz, independent of the location stream.
     *
     * This used to be computed inside collectLocation(), which meant the ride clock froze
     * whenever GPS fixes stopped — under trees, in a garage, in a tunnel. Nothing rendered it
     * then; the dash does now.
     */
    private fun collectElapsed() = viewModelScope.launch {
        while (isActive) {
            _ui.value = _ui.value.copy(elapsedMs = System.currentTimeMillis() - startMillis)
            delay(1_000L)
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
        viewModelScope.launch {
            val rideId = repo.saveRide(summary, snapshot, activePlanRideId)
            if (settings.value.stravaUploadEnabled && stravaStore.isConnected) {
                val ride = repo.getRide(rideId) ?: return@launch
                repo.markQueued(rideId, ride)
                StravaUploadScheduler.enqueue(getApplication(), rideId)
            }
        }
    }

    fun saveSettings(updated: RidemanSettings) {
        viewModelScope.launch { settingsStore.save(updated) }
    }

    /**
     * Flips the ride display between portrait and landscape and persists the choice.
     *
     * Sticky by design: the next ride opens in whatever orientation you last rode in, so a
     * bar-mounted rider never has to correct it. The rotate button is the only control — this
     * is deliberately not surfaced in Settings.
     */
    fun toggleRideOrientation() {
        val current = settings.value
        saveSettings(current.copy(rideOrientation = current.rideOrientation.flipped()))
    }
}
