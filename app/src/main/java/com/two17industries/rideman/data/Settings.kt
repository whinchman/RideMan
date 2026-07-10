package com.two17industries.rideman.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The five ride sub-screens, in their default order. Stored as a CSV of names. */
enum class RideScreen { SPEED, ODOMETER, COMPASS, ALTITUDE, CADENCE }

enum class ThemeChoice { AMBER, ACID_GREEN, ELECTRIC_CYAN, HOT_MAGENTA }

data class RidemanSettings(
    val units: UnitSystem = UnitSystem.AMERICAN,
    val screenOrder: List<RideScreen> = RideScreen.entries.toList(),
    val cadenceMode: CadenceMode = CadenceMode.FULL,
    val targetRpm: Int = 80,
    val theme: ThemeChoice = ThemeChoice.AMBER,
    val stravaUploadEnabled: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val ORDER = stringPreferencesKey("screen_order")
        val CADENCE_MODE = stringPreferencesKey("cadence_mode")
        val TARGET_RPM = intPreferencesKey("target_rpm")
        val THEME = stringPreferencesKey("theme")
        val STRAVA_UPLOAD = booleanPreferencesKey("strava_upload_enabled")
    }

    val settings: Flow<RidemanSettings> = context.dataStore.data.map { p ->
        RidemanSettings(
            units = p[Keys.UNITS]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: UnitSystem.AMERICAN,
            screenOrder = p[Keys.ORDER]?.split(",")
                ?.mapNotNull { runCatching { RideScreen.valueOf(it) }.getOrNull() }
                ?.ifEmpty { RideScreen.entries.toList() }
                ?: RideScreen.entries.toList(),
            cadenceMode = p[Keys.CADENCE_MODE]?.let { runCatching { CadenceMode.valueOf(it) }.getOrNull() }
                ?: CadenceMode.FULL,
            targetRpm = p[Keys.TARGET_RPM] ?: 80,
            theme = p[Keys.THEME]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() }
                ?: ThemeChoice.AMBER,
            stravaUploadEnabled = p[Keys.STRAVA_UPLOAD] ?: true,
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
        }
    }
}
