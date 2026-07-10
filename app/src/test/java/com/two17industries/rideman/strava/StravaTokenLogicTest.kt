package com.two17industries.rideman.strava

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaTokenLogicTest {
    @Test fun fresh_token_does_not_need_refresh() {
        assertFalse(StravaTokenLogic.needsRefresh(nowEpochSec = 1000, expiresAtEpochSec = 5000))
    }

    @Test fun expired_token_needs_refresh() {
        assertTrue(StravaTokenLogic.needsRefresh(nowEpochSec = 6000, expiresAtEpochSec = 5000))
    }

    @Test fun token_within_skew_window_needs_refresh() {
        // expires in 30s, default skew 60s → refresh now.
        assertTrue(StravaTokenLogic.needsRefresh(nowEpochSec = 4970, expiresAtEpochSec = 5000))
    }
}
