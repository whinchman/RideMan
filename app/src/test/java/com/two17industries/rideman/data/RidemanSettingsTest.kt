package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RidemanSettingsTest {

    @Test
    fun `heart rate is off and unconfigured by default`() {
        val s = RidemanSettings()
        assertEquals(false, s.hrmEnabled)
        assertNull(s.hrmAddress)
        assertNull(s.birthYear)
        assertNull(s.maxHeartRateBpm)
        assertNull(s.baselineHeartRateBpm)
        assertNull(s.baselineCalibratedAtMillis)
    }

    @Test
    fun `an explicit max heart rate wins over the age estimate`() {
        val s = RidemanSettings(birthYear = 1986, maxHeartRateBpm = 191)
        assertEquals(191, s.effectiveMaxHeartRate(currentYear = 2026))
    }

    @Test
    fun `max heart rate falls back to the age estimate`() {
        val s = RidemanSettings(birthYear = 1986)
        assertEquals(180, s.effectiveMaxHeartRate(currentYear = 2026))
    }

    @Test
    fun `max heart rate is null when nothing is configured`() {
        assertNull(RidemanSettings().effectiveMaxHeartRate(currentYear = 2026))
    }
}
