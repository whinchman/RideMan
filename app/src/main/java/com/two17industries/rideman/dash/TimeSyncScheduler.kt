package com.two17industries.rideman.dash

/**
 * Decides when the phone should (re)write the time-sync characteristic.
 *
 * On connect, then every [intervalMs]. The periodic re-sync costs 7 bytes/minute and buys:
 *  - a dropped write-without-response self-heals within one interval (nothing ACKs these writes);
 *  - a post-deep-sleep cold boot re-syncs on reconnect (the board loses its clock entirely);
 *  - DST changes, midnight rollover, and a user changing the phone clock are all picked up.
 *
 * That is precisely why no write-confirmation plumbing is needed: the next sync is never
 * more than one interval away.
 */
class TimeSyncScheduler(private val intervalMs: Long = 60_000L) {

    private var lastSentMs: Long? = null

    /** Call on every transition into CONNECTED — re-arms an immediate sync. */
    fun onConnected() { lastSentMs = null }

    fun due(nowMs: Long): Boolean {
        val last = lastSentMs ?: return true
        return nowMs - last >= intervalMs
    }

    fun markSent(nowMs: Long) { lastSentMs = nowMs }
}
