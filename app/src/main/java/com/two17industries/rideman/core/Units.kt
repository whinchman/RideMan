package com.two17industries.rideman.core

import java.util.Locale

enum class UnitSystem { AMERICAN, METRIC }

object Units {
    private const val MPS_TO_MPH = 2.2369362920544
    private const val MPS_TO_KMH = 3.6
    private const val M_TO_MI = 1.0 / 1609.344
    private const val M_TO_KM = 1.0 / 1000.0
    private const val M_TO_FT = 3.280839895

    fun speed(mps: Float, sys: UnitSystem): Float =
        if (sys == UnitSystem.AMERICAN) (mps * MPS_TO_MPH).toFloat() else (mps * MPS_TO_KMH).toFloat()

    fun distance(meters: Double, sys: UnitSystem): Double =
        if (sys == UnitSystem.AMERICAN) meters * M_TO_MI else meters * M_TO_KM

    fun altitude(meters: Double, sys: UnitSystem): Double =
        if (sys == UnitSystem.AMERICAN) meters * M_TO_FT else meters

    fun speedLabel(sys: UnitSystem) = if (sys == UnitSystem.AMERICAN) "MPH" else "KM/H"
    fun distanceLabel(sys: UnitSystem) = if (sys == UnitSystem.AMERICAN) "MI" else "KM"
    fun altitudeLabel(sys: UnitSystem) = if (sys == UnitSystem.AMERICAN) "FT" else "M"

    /**
     * Elapsed ride time. MM:SS under an hour, H:MM:SS at an hour and over.
     * Partial seconds truncate; negatives clamp to zero.
     */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
