package com.two17industries.rideman.hrm

import com.two17industries.rideman.core.HeartRateSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single-process channel carrying heart rate readings (mirrors LocationBus).
 *
 * Conflation is harmless here: the live readout only wants the newest value, and the
 * persistence path samples whatever is current at each GPS fix rather than consuming a stream.
 */
object HrmBus {
    private val _latest = MutableStateFlow<HeartRateSample?>(null)
    val latest: StateFlow<HeartRateSample?> = _latest

    fun publish(s: HeartRateSample) { _latest.value = s }

    fun reset() { _latest.value = null }
}
