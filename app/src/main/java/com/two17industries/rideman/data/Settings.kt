package com.two17industries.rideman.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.MaxHeartRate
import com.two17industries.rideman.core.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The ride sub-screens, in their default order.
 *
 * GRID is the 2x2 "Dash" readout (Speed/Distance/Duration/Heading). It is named GRID, not DASH,
 * because `dash/` is already the T-Display BLE handlebar dashboard — different feature entirely.
 *
 * Stored as a CSV of names; presence in the CSV also means "enabled".
 */
enum class RideScreen { GRID, SPEED, ODOMETER, COMPASS, ALTITUDE, CADENCE, HEART_RATE }

enum class ThemeChoice { AMBER, ACID_GREEN, ELECTRIC_CYAN, HOT_MAGENTA }

/** Ride display orientation. Sticky across rides; toggled only by the ride screen's rotate button. */
enum class RideOrientation {
    PORTRAIT,
    LANDSCAPE;

    fun flipped(): RideOrientation = if (this == PORTRAIT) LANDSCAPE else PORTRAIT
}

data class RidemanSettings(
    val units: UnitSystem = UnitSystem.AMERICAN,
    val screenOrder: List<RideScreen> = RideScreen.entries.toList(),
    val cadenceMode: CadenceMode = CadenceMode.FULL,
    val targetRpm: Int = 80,
    val theme: ThemeChoice = ThemeChoice.AMBER,
    val stravaUploadEnabled: Boolean = true,
    val dashEnabled: Boolean = false,
    val rideOrientation: RideOrientation = RideOrientation.PORTRAIT,
    /** Whether the app should scan for and connect to a heart rate strap. */
    val hrmEnabled: Boolean = false,
    /** MAC of the remembered strap, or null to connect to the first one found. */
    val hrmAddress: String? = null,
    /** Rider's birth year, used for the age-estimate fallback. Null when not configured. */
    val birthYear: Int? = null,
    /** Explicit or auto-raised max HR. Null means fall back to the age estimate. */
    val maxHeartRateBpm: Int? = null,
    /** Result of the last calibration. Null means zones use percent-of-max. */
    val baselineHeartRateBpm: Int? = null,
    /** Epoch millis of the last calibration. Null when no calibration has been run. */
    val baselineCalibratedAtMillis: Long? = null,
)

/**
 * Max HR to use for zones: the stored value, else the age estimate, else null when the rider
 * has configured neither (zones are unavailable until one exists).
 */
fun RidemanSettings.effectiveMaxHeartRate(currentYear: Int): Int? =
    maxHeartRateBpm ?: birthYear?.let { MaxHeartRate.estimateFromAge(it, currentYear) }

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val ORDER = stringPreferencesKey("screen_order")
        val CADENCE_MODE = stringPreferencesKey("cadence_mode")
        val TARGET_RPM = intPreferencesKey("target_rpm")
        val THEME = stringPreferencesKey("theme")
        val STRAVA_UPLOAD = booleanPreferencesKey("strava_upload_enabled")
        val DASH_ENABLED = booleanPreferencesKey("dash_enabled")
        val GRID_MIGRATED = booleanPreferencesKey("grid_migrated")
        val HR_MIGRATED = booleanPreferencesKey("hr_migrated")
        val RIDE_ORIENTATION = stringPreferencesKey("ride_orientation")
        val HRM_ENABLED = booleanPreferencesKey("hrm_enabled")
        val HRM_ADDRESS = stringPreferencesKey("hrm_address")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val MAX_HR = intPreferencesKey("max_hr_bpm")
        val BASELINE_HR = intPreferencesKey("baseline_hr_bpm")
        val BASELINE_AT = longPreferencesKey("baseline_calibrated_at")
    }

    val settings: Flow<RidemanSettings> = context.dataStore.data.map { p ->
        RidemanSettings(
            units = p[Keys.UNITS]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: UnitSystem.AMERICAN,
            screenOrder = ScreenOrder.migrate(
                saved = p[Keys.ORDER]?.split(",")
                    ?.mapNotNull { runCatching { RideScreen.valueOf(it) }.getOrNull() },
                gridMigrated = p[Keys.GRID_MIGRATED] ?: false,
                hrMigrated = p[Keys.HR_MIGRATED] ?: false,
            ),
            cadenceMode = p[Keys.CADENCE_MODE]?.let { runCatching { CadenceMode.valueOf(it) }.getOrNull() }
                ?: CadenceMode.FULL,
            targetRpm = p[Keys.TARGET_RPM] ?: 80,
            theme = p[Keys.THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.AMBER,
            stravaUploadEnabled = p[Keys.STRAVA_UPLOAD] ?: true,
            dashEnabled = p[Keys.DASH_ENABLED] ?: false,
            rideOrientation = p[Keys.RIDE_ORIENTATION]
                ?.let { runCatching { RideOrientation.valueOf(it) }.getOrNull() }
                ?: RideOrientation.PORTRAIT,
            hrmEnabled = p[Keys.HRM_ENABLED] ?: false,
            hrmAddress = p[Keys.HRM_ADDRESS],
            birthYear = p[Keys.BIRTH_YEAR],
            maxHeartRateBpm = p[Keys.MAX_HR],
            baselineHeartRateBpm = p[Keys.BASELINE_HR],
            baselineCalibratedAtMillis = p[Keys.BASELINE_AT],
        )
    }

    suspend fun save(s: RidemanSettings) {
        context.dataStore.edit { p ->
            p[Keys.UNITS] = s.units.name
            p[Keys.ORDER] = s.screenOrder.joinToString(",") { it.name }
            p[Keys.CADENCE_MODE] = s.cadenceMode.name
            p[Keys.TARGET_RPM] = s.targetRpm
            p[Keys.THEME] = s.theme.name
            p[Keys.STRAVA_UPLOAD] = s.stravaUploadEnabled
            p[Keys.DASH_ENABLED] = s.dashEnabled
            p[Keys.GRID_MIGRATED] = true
            p[Keys.HR_MIGRATED] = true
            p[Keys.RIDE_ORIENTATION] = s.rideOrientation.name
            p[Keys.HRM_ENABLED] = s.hrmEnabled
            applyNullableFields(p, s)
        }
    }

    companion object {
        // These are the app's first nullable settings, and save() writes every key
        // unconditionally — so clearing one needs remove(), not assignment.
        internal fun applyNullableFields(p: MutablePreferences, s: RidemanSettings) {
            s.hrmAddress?.let { p[Keys.HRM_ADDRESS] = it } ?: p.remove(Keys.HRM_ADDRESS)
            s.birthYear?.let { p[Keys.BIRTH_YEAR] = it } ?: p.remove(Keys.BIRTH_YEAR)
            s.maxHeartRateBpm?.let { p[Keys.MAX_HR] = it } ?: p.remove(Keys.MAX_HR)
            s.baselineHeartRateBpm?.let { p[Keys.BASELINE_HR] = it } ?: p.remove(Keys.BASELINE_HR)
            s.baselineCalibratedAtMillis?.let { p[Keys.BASELINE_AT] = it } ?: p.remove(Keys.BASELINE_AT)
        }
    }
}
