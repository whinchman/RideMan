package com.two17industries.rideman.location

import com.two17industries.rideman.core.LocationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Single-process channel between the foreground service (producer) and the VM (consumer). */
object LocationBus {
    private val _latest = MutableStateFlow<LocationSample?>(null)
    val latest: StateFlow<LocationSample?> = _latest

    fun publish(sample: LocationSample) { _latest.value = sample }
    fun reset() { _latest.value = null }
}
