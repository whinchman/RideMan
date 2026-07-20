package com.two17industries.rideman.core

/** One heart rate reading from the strap. */
data class HeartRateSample(
    val epochMillis: Long,
    val bpm: Int,
    val contactOk: Boolean,
)

/**
 * A GPS fix plus whatever heart rate was current at the time.
 *
 * [LocationSample] deliberately stays pure GPS — it is documented as "one GPS fix" and is
 * consumed by TelemetryBuilder, RideTracker and their tests — so heart rate rides alongside
 * it rather than inside it.
 */
data class TrackedPoint(
    val sample: LocationSample,
    val heartRateBpm: Int?,
)

/**
 * Decides whether a heart rate reading is current enough to attach to a GPS fix.
 *
 * This is what implements the "gaps stay null" rule: a dead or dislodged strap simply stops
 * producing fresh samples, so points go null on their own with no special-case code. There is
 * deliberately no hold-last-value behaviour — a flat line from mile 2 would look like real
 * data.
 */
object HeartRateStamp {

    /** A reading older than this is not attached to a fix. */
    const val MAX_AGE_MS = 5_000L

    fun bpmFor(fixMillis: Long, hr: HeartRateSample?): Int? {
        if (hr == null) return null
        if (!hr.contactOk) return null
        val age = fixMillis - hr.epochMillis
        if (age < 0 || age > MAX_AGE_MS) return null
        return hr.bpm
    }
}
