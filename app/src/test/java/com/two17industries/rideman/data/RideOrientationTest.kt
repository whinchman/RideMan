package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RideOrientationTest {
    @Test fun portrait_flips_to_landscape() {
        assertEquals(RideOrientation.LANDSCAPE, RideOrientation.PORTRAIT.flipped())
    }
    @Test fun landscape_flips_to_portrait() {
        assertEquals(RideOrientation.PORTRAIT, RideOrientation.LANDSCAPE.flipped())
    }
    @Test fun flipping_twice_is_identity() {
        assertEquals(RideOrientation.PORTRAIT, RideOrientation.PORTRAIT.flipped().flipped())
    }
    @Test fun default_settings_are_portrait() {
        assertEquals(RideOrientation.PORTRAIT, RidemanSettings().rideOrientation)
    }
}
