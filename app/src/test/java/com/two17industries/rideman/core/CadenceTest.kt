package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CadenceTest {
    @Test fun full_stroke_60rpm_is_one_click_per_second() {
        assertEquals(1000L, Cadence.clickPeriodMs(60, CadenceMode.FULL))
    }
    @Test fun full_stroke_90rpm() {
        assertEquals(667L, Cadence.clickPeriodMs(90, CadenceMode.FULL))
    }
    @Test fun half_stroke_doubles_click_rate() {
        assertEquals(500L, Cadence.clickPeriodMs(60, CadenceMode.HALF))
    }
    @Test fun clamps_rpm_to_valid_range() {
        assertEquals(40, Cadence.clampRpm(0))
        assertEquals(150, Cadence.clampRpm(9999))
        assertEquals(85, Cadence.clampRpm(85))
    }
}
