package com.two17industries.rideman.dash

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** SI-unit snapshot of the ride, ready to serialize onto the wire. */
data class Telemetry(
    val speedMps: Float,
    val distanceM: Double,
    val elapsedSec: Long,
    val headingDeg: Float,
    val altitudeM: Double,
    val unitsUS: Boolean,
    val rideActive: Boolean,
    val gpsValid: Boolean,
)

/** Encodes [Telemetry] into the frozen 16-byte little-endian packet. */
object TelemetryPacket {
    const val SIZE = 16
    const val VERSION = 1

    private const val FLAG_UNITS_US = 0x01
    private const val FLAG_RIDE_ACTIVE = 0x02
    private const val FLAG_GPS_VALID = 0x04

    fun encode(t: Telemetry): ByteArray {
        var flags = 0
        if (t.unitsUS) flags = flags or FLAG_UNITS_US
        if (t.rideActive) flags = flags or FLAG_RIDE_ACTIVE
        if (t.gpsValid) flags = flags or FLAG_GPS_VALID

        val speedCmps = (t.speedMps * 100f).roundToInt().coerceIn(0, 0xFFFF)
        val distance = t.distanceM.roundToLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val elapsed = t.elapsedSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val heading = (((t.headingDeg.roundToInt() % 360) + 360) % 360) // 0..359
        val altitude = t.altitudeM.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

        return ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(VERSION.toByte())
            put(flags.toByte())
            putShort((speedCmps and 0xFFFF).toShort())
            putInt(distance)
            putInt(elapsed)
            putShort((heading and 0xFFFF).toShort())
            putShort(altitude.toShort())
        }.array()
    }
}
