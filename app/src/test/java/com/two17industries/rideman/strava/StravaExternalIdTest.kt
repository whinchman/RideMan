package com.two17industries.rideman.strava

import org.junit.Assert.assertEquals
import org.junit.Test

class StravaExternalIdTest {
    @Test fun encodes_ride_id_and_start_time() {
        assertEquals("rideman-42-1720600000000", StravaExternalId.forRide(42, 1720600000000))
    }

    @Test fun is_stable_for_same_inputs() {
        assertEquals(
            StravaExternalId.forRide(7, 111),
            StravaExternalId.forRide(7, 111),
        )
    }
}
