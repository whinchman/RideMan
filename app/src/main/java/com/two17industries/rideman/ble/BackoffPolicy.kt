package com.two17industries.rideman.ble

/**
 * Reconnect backoff for [BleCentral]. Pure so it can be unit tested; the BLE client itself
 * cannot be. Exponential from 1s (1, 2, 4, 8, 16) then pinned at the 30s ceiling FOREVER.
 *
 * There is deliberately no retry cap. An earlier version gave up after 20 attempts, which made
 * a board brownout or a long stop terminal for the whole ride: nothing re-arms the dash, because
 * LocationForegroundService reads dashEnabled once per service lifetime, so the "toggle off and
 * on to retry" advice in Settings did nothing. Retrying at 30s costs one scan window every half
 * minute — cheap next to a dead dash for the rest of a ride.
 */
object BackoffPolicy {

    private const val BASE_MS = 1_000L
    private const val CAP_MS = 30_000L

    /** Delay before retry number [attempt] (0-based). Never null: retries continue indefinitely. */
    fun delayMsFor(attempt: Int): Long {
        val n = attempt.coerceAtLeast(0)
        if (n >= 5) return CAP_MS
        return (BASE_MS shl n).coerceAtMost(CAP_MS)
    }
}
