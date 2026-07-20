package com.two17industries.rideman.dash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSyncSchedulerTest {

    @Test fun first_sync_is_due_immediately() {
        val s = TimeSyncScheduler()
        assertTrue(s.due(nowMs = 0L))
    }

    @Test fun not_due_again_until_the_interval_elapses() {
        val s = TimeSyncScheduler(intervalMs = 60_000L)
        s.markSent(nowMs = 1_000L)
        assertFalse(s.due(nowMs = 1_000L))
        assertFalse(s.due(nowMs = 30_000L))
        assertFalse(s.due(nowMs = 60_999L))   // 59_999 ms since sent
    }

    @Test fun due_again_exactly_at_the_interval() {
        val s = TimeSyncScheduler(intervalMs = 60_000L)
        s.markSent(nowMs = 1_000L)
        assertTrue(s.due(nowMs = 61_000L))    // 60_000 ms since sent
    }

    @Test fun reconnect_rearms_an_immediate_sync() {
        // The board deep-sleeps and cold-boots with millis() reset, losing its clock
        // entirely — so every reconnect MUST re-sync, regardless of the interval.
        val s = TimeSyncScheduler(intervalMs = 60_000L)
        s.markSent(nowMs = 1_000L)
        assertFalse(s.due(nowMs = 2_000L))
        s.onConnected()
        assertTrue(s.due(nowMs = 2_000L))
    }

    @Test fun interval_restarts_from_the_sync_after_a_reconnect() {
        val s = TimeSyncScheduler(intervalMs = 60_000L)
        s.onConnected()
        s.markSent(nowMs = 5_000L)
        assertFalse(s.due(nowMs = 40_000L))
        assertTrue(s.due(nowMs = 65_000L))
    }

    @Test fun backward_clock_jump_forces_an_immediate_resync() {
        // System.currentTimeMillis() is wall-clock, not monotonic: an NTP correction can step it
        // backwards. A negative delta must re-sync, not blind the scheduler for the jump's duration.
        val s = TimeSyncScheduler(intervalMs = 60_000L)
        s.markSent(nowMs = 600_000L)
        assertTrue(s.due(nowMs = 30_000L))
    }
}
