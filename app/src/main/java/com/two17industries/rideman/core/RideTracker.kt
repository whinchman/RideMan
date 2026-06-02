package com.two17industries.rideman.core

/** Stateful accumulator fed live GPS samples. Not thread-safe; call from one coroutine. */
class RideTracker(private val startMillis: Long) {
    var distanceM: Double = 0.0
        private set
    var maxSpeedMps: Float = 0f
        private set

    private var last: LocationSample? = null

    fun add(sample: LocationSample) {
        val prev = last
        if (prev != null) {
            distanceM += Geo.haversineMeters(prev.lat, prev.lng, sample.lat, sample.lng)
        }
        if (sample.speedMps > maxSpeedMps) maxSpeedMps = sample.speedMps
        last = sample
    }

    fun summarize(endMillis: Long): RideSummary {
        val elapsed = (endMillis - startMillis).coerceAtLeast(0L)
        val avg = if (elapsed > 0L) (distanceM / (elapsed / 1000.0)).toFloat() else 0f
        return RideSummary(
            startedAtMillis = startMillis,
            endedAtMillis = endMillis,
            totalTimeMs = elapsed,
            distanceM = distanceM,
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avg,
        )
    }
}
