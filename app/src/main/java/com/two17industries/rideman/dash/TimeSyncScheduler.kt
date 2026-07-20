package com.two17industries.rideman.dash

/**
 * Decides when the phone should (re)write the time-sync characteristic.
 *
 * On connect, then every [intervalMs]. The periodic re-sync costs 7 bytes/minute and buys:
 *  - a dropped write-without-response self-heals within one interval (nothing ACKs these writes);
 *  - a post-deep-sleep cold boot re-syncs on reconnect (the board loses its clock entirely);
 *  - DST changes, midnight rollover, and a user changing the phone clock forward are all picked up.
 *
 * That is precisely why no write-confirmation plumbing is needed: the next sync is never
 * more than one interval away.
 *
 * [nowMs] is wall-clock (`System.currentTimeMillis()`), not monotonic, so it can step
 * BACKWARDS (NTP/NITZ correcting a drifted RTC, or a user setting the clock back). [due]
 * treats any negative delta as an immediate re-sync — otherwise a backward jump would blind
 * the scheduler for the jump's duration, leaving the board showing a stale, wrong time for
 * as long as the jump was large.
 *
 * Thread-confinement: [lastSentMs] is NOT thread-safe. Callers must ensure all accesses occur on
 * the same thread; currently safe via DashBroadcaster running all coroutines on Dispatchers.Main.immediate.
 */
class TimeSyncScheduler(private val intervalMs: Long = 60_000L) {

    private var lastSentMs: Long? = null

    /** Call on every transition into CONNECTED — re-arms an immediate sync. */
    fun onConnected() { lastSentMs = null }

    fun due(nowMs: Long): Boolean {
        val last = lastSentMs ?: return true
        val delta = nowMs - last
        return delta >= intervalMs || delta < 0   // clock stepped backwards: re-sync now
    }

    fun markSent(nowMs: Long) { lastSentMs = nowMs }
}
