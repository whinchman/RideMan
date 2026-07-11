package com.two17industries.rideman.dash

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure encoder for the 7-byte BLE time-sync payload (little-endian):
 *
 *   [0]     version           u8  = 1
 *   [1..4]  epochUtc          u32 (UNSIGNED — valid to 2106, not 2038)
 *   [5..6]  utcOffsetMinutes  i16 (signed; minutes, so :30/:45 zones work)
 *
 * The board computes localEpoch = epochUtc + utcOffsetMinutes * 60, so it needs no
 * timezone database — the phone, which knows the real zone and DST, does that work.
 * We send the UTC instant plus the offset (rather than a pre-converted local time) so
 * the instant and its presentation stay separable.
 */
object TimeSyncPacket {
    const val SIZE = 7
    const val VERSION = 1

    fun encode(epochUtcSec: Long, utcOffsetMinutes: Int): ByteArray =
        ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(VERSION.toByte())
            // toInt() truncates to the low 32 bits — which IS the unsigned u32 wire
            // value. The firmware decodes it unsigned, so epochs past Int.MAX_VALUE
            // (2038) transit correctly.
            putInt(epochUtcSec.toInt())
            putShort(utcOffsetMinutes.toShort())
        }.array()

    /** Convert phone-native units (millis, millis-offset) to wire units. */
    fun now(nowMillis: Long, offsetMillis: Int): ByteArray =
        encode(nowMillis / 1000L, offsetMillis / 60_000)
}
