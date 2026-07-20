package com.two17industries.rideman.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [SettingsStore.applyNullableFields] — the per-key mutation `save()` performs inside
 * its `DataStore.edit { }` block — against a plain in-memory [androidx.datastore.preferences.core.MutablePreferences],
 * with no [android.content.Context] or DataStore instance required.
 *
 * The key behaviour under test: clearing a nullable field (setting it back to null) must remove
 * the underlying preference key, not merely skip writing it. If it only skipped the write, a
 * previously-set key would stick around forever — e.g. a rider could never unpair a heart rate
 * strap once one had been remembered.
 */
class SettingsStoreTest {

    // Mirrors SettingsStore.Keys exactly. Preferences.Key equality is by name (and type), so a
    // separately constructed key with the same name reads/writes the same underlying entry.
    private val hrmAddressKey = stringPreferencesKey("hrm_address")
    private val birthYearKey = intPreferencesKey("birth_year")
    private val maxHrKey = intPreferencesKey("max_hr_bpm")
    private val baselineHrKey = intPreferencesKey("baseline_hr_bpm")
    private val baselineAtKey = longPreferencesKey("baseline_calibrated_at")

    @Test
    fun `a non-null hrmAddress is written and reads back`() {
        val p = mutablePreferencesOf()
        SettingsStore.applyNullableFields(p, RidemanSettings(hrmAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", p[hrmAddressKey])
    }

    @Test
    fun `a null hrmAddress removes a previously-set key`() {
        val p = mutablePreferencesOf(hrmAddressKey to "AA:BB:CC:DD:EE:FF")
        assertTrue(p.contains(hrmAddressKey))
        SettingsStore.applyNullableFields(p, RidemanSettings(hrmAddress = null))
        assertFalse(p.contains(hrmAddressKey))
    }

    @Test
    fun `a non-null birthYear is written and reads back`() {
        val p = mutablePreferencesOf()
        SettingsStore.applyNullableFields(p, RidemanSettings(birthYear = 1986))
        assertEquals(1986, p[birthYearKey])
    }

    @Test
    fun `a null birthYear removes a previously-set key`() {
        val p = mutablePreferencesOf(birthYearKey to 1986)
        assertTrue(p.contains(birthYearKey))
        SettingsStore.applyNullableFields(p, RidemanSettings(birthYear = null))
        assertFalse(p.contains(birthYearKey))
    }

    @Test
    fun `a non-null maxHeartRateBpm is written and reads back`() {
        val p = mutablePreferencesOf()
        SettingsStore.applyNullableFields(p, RidemanSettings(maxHeartRateBpm = 191))
        assertEquals(191, p[maxHrKey])
    }

    @Test
    fun `a null maxHeartRateBpm removes a previously-set key`() {
        val p = mutablePreferencesOf(maxHrKey to 191)
        assertTrue(p.contains(maxHrKey))
        SettingsStore.applyNullableFields(p, RidemanSettings(maxHeartRateBpm = null))
        assertFalse(p.contains(maxHrKey))
    }

    @Test
    fun `a non-null baselineHeartRateBpm is written and reads back`() {
        val p = mutablePreferencesOf()
        SettingsStore.applyNullableFields(p, RidemanSettings(baselineHeartRateBpm = 142))
        assertEquals(142, p[baselineHrKey])
    }

    @Test
    fun `a null baselineHeartRateBpm removes a previously-set key`() {
        val p = mutablePreferencesOf(baselineHrKey to 142)
        assertTrue(p.contains(baselineHrKey))
        SettingsStore.applyNullableFields(p, RidemanSettings(baselineHeartRateBpm = null))
        assertFalse(p.contains(baselineHrKey))
    }

    @Test
    fun `a non-null baselineCalibratedAtMillis is written and reads back`() {
        val p = mutablePreferencesOf()
        SettingsStore.applyNullableFields(p, RidemanSettings(baselineCalibratedAtMillis = 1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, p[baselineAtKey])
    }

    @Test
    fun `a null baselineCalibratedAtMillis removes a previously-set key`() {
        val p = mutablePreferencesOf(baselineAtKey to 1_700_000_000_000L)
        assertTrue(p.contains(baselineAtKey))
        SettingsStore.applyNullableFields(p, RidemanSettings(baselineCalibratedAtMillis = null))
        assertFalse(p.contains(baselineAtKey))
    }
}
