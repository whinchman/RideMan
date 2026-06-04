package com.two17industries.rideman.core

import kotlin.math.roundToLong

enum class CadenceMode { FULL, HALF }

object Cadence {
    const val MIN_RPM = 40
    const val MAX_RPM = 150

    fun clampRpm(rpm: Int): Int = rpm.coerceIn(MIN_RPM, MAX_RPM)

    /** Milliseconds between clicks. HALF mode clicks twice per crank revolution. */
    fun clickPeriodMs(rpm: Int, mode: CadenceMode): Long {
        val clamped = clampRpm(rpm)
        val clicksPerMin = if (mode == CadenceMode.HALF) clamped * 2 else clamped
        return (60_000.0 / clicksPerMin).roundToLong()
    }
}
