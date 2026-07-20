package com.two17industries.rideman.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.two17industries.rideman.core.CalibrationSample
import com.two17industries.rideman.core.HeartRateStamp
import com.two17industries.rideman.core.MaxHeartRate
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.RideTracker
import com.two17industries.rideman.core.TrackedPoint
import com.two17industries.rideman.data.RidemanDatabase
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.RideRepository
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.data.effectiveMaxHeartRate
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.data.PlanLoader
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.hrm.HrmBus
import com.two17industries.rideman.location.LocationBus
import com.two17industries.rideman.location.LocationForegroundService
import com.two17industries.rideman.sensor.SensorRepository
import com.two17industries.rideman.strava.OkHttpStravaHttp
import com.two17industries.rideman.strava.StravaAuth
import com.two17industries.rideman.strava.StravaTokenStore
import com.two17industries.rideman.strava.StravaUploadScheduler
import com.two17industries.rideman.BuildConfig
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RideUiState(
    val speedMps: Float = 0f,
    val distanceM: Double = 0.0,
    val headingDeg: Float = 0f,
    val altitudeM: Double = 0.0,
    val elapsedMs: Long = 0L,
    /** Live BPM, or null when no strap is connected or its reading has gone stale. */
    val heartRateBpm: Int? = null,
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

    /**
     * (timestamp, bpm) for **every** fix in [rideId], with a null bpm where the strap gave no
     * reading. The nulls are deliberately preserved rather than filtered out: dropping them
     * would close the gaps up and leave the consumer unable to tell a continuous stretch from
     * one interrupted by a dead strap, which is how a dropout would get credited as training
     * time. [HeartRateZones.timeInZoneMs] is what reads the nulls.
     *
     * Loaded on demand rather than stored, because an auto-raised max HR moves every zone
     * boundary and would make a cached breakdown wrong for past rides.
     */
    suspend fun heartRateSamples(rideId: Long): List<Pair<Long, Int?>> =
        repo.trackPoints(rideId).map { p -> p.timestamp to p.heartRateBpm }

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
    private val track = mutableListOf<TrackedPoint>()
    /** Every strap reading this ride, for the max-HR auto-raise check at save time. */
    private val hrSamples = mutableListOf<CalibrationSample>()
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
        HrmBus.reset()
        hrSamples.clear()
        // A raise the rider dismissed by navigating away rather than by an explicit action would
        // otherwise stay latched and re-fire at the end of this ride.
        _maxHrRaised.value = null
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
            val bpm = HeartRateStamp.bpmFor(sample.epochMillis, HrmBus.latest.value)
            track.add(TrackedPoint(sample, bpm))
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
                heartRateBpm = bpm,
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
            val now = System.currentTimeMillis()
            _ui.value = _ui.value.copy(
                elapsedMs = now - startMillis,
                // Re-evaluated every tick so the readout ages out on its own. HrmBus.latest is a
                // StateFlow, so a strap that goes quiet without a GATT disconnect never re-emits
                // and its collector never fires again; collectLocation would normally correct
                // this within a second, but not when GPS is unavailable too — the tunnel case.
                heartRateBpm = HeartRateStamp.bpmFor(now, HrmBus.latest.value),
            )
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
        viewModelScope.launch {
            HrmBus.latest.collect { hr ->
                hr ?: return@collect
                hrSamples.add(CalibrationSample(hr.epochMillis, hr.bpm, hr.contactOk))
                // Same staleness rule as the stamping path: heartRateBpm promises null once a
                // reading has gone stale, and a bare contactOk check does not implement that.
                _ui.value = _ui.value.copy(
                    heartRateBpm = HeartRateStamp.bpmFor(System.currentTimeMillis(), hr),
                )
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
        // Snapshot alongside the track, for the same reason. Nav navigates to START in the same
        // handler that calls this, and the next tap is startRide(), which clears hrSamples and
        // installs a new collector on Main — reading the live field from Dispatchers.Default
        // would both break per-ride isolation and race an unsynchronised mutableListOf.
        val hrSnapshot = hrSamples.toList()
        viewModelScope.launch {
            val rideId = repo.saveRide(summary, snapshot, activePlanRideId)
            maybeRaiseMaxHeartRate(hrSnapshot)
            if (settings.value.stravaUploadEnabled && stravaStore.isConnected) {
                val ride = repo.getRide(rideId) ?: return@launch
                repo.markQueued(rideId, ride)
                StravaUploadScheduler.enqueue(getApplication(), rideId)
            }
        }
    }

    data class MaxHrRaise(val from: Int?, val to: Int)

    private val _maxHrRaised = MutableStateFlow<MaxHrRaise?>(null)
    val maxHrRaised: StateFlow<MaxHrRaise?> = _maxHrRaised.asStateFlow()

    fun clearMaxHrRaised() { _maxHrRaised.value = null }

    /**
     * Raise the stored max HR if this ride corroborated a higher one. Never lowers. Raising
     * moves every zone boundary, including on past rides, so the rider is told.
     */
    private suspend fun maybeRaiseMaxHeartRate(samples: List<CalibrationSample>) {
        // corroboratedPeak is O(n^2) in the worst case and a real strap notifies on the
        // heartbeat, not at 1 Hz — a two-hour ride is 15-20k samples. viewModelScope defaults
        // to the Main dispatcher, so this MUST be moved off it or ride save janks the UI.
        val candidate = withContext(Dispatchers.Default) {
            MaxHeartRate.corroboratedPeak(samples)
        } ?: return
        // Read through rather than off the cached snapshot: this is the one non-user-initiated
        // caller of read-modify-write, and `settings` seeds Eagerly with an all-defaults
        // RidemanSettings until DataStore first emits, which a save here would write back.
        val current = settingsStore.settings.first()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val existing = current.effectiveMaxHeartRate(year)
        if (existing != null && candidate <= existing) return
        settingsStore.save(current.copy(maxHeartRateBpm = candidate))
        _maxHrRaised.value = MaxHrRaise(from = existing, to = candidate)
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
