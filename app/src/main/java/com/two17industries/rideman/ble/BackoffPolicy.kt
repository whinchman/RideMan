package com.two17industries.rideman.ble

/**
 * Reconnect backoff for [BleCentral]. Pure so it can be unit tested; the BLE client itself
 * cannot be. Exponential from 1s, capped at 30s, giving up after MAX_ATTEMPTS
 * (~9.5 minutes of trying) so a strap left at home does not scan for the whole ride.
 */
object BackoffPolicy {

    const val MAX_ATTEMPTS = 20
    private const val BASE_MS = 1_000L
    private const val CAP_MS = 30_000L

    /** Delay before retry number [attempt] (0-based), or null once the cap is exhausted. */
    fun delayMsFor(attempt: Int): Long? {
        if (attempt >= MAX_ATTEMPTS) return null
        val n = attempt.coerceAtLeast(0)
        if (n >= 5) return CAP_MS
        return (BASE_MS shl n).coerceAtMost(CAP_MS)
    }
}
