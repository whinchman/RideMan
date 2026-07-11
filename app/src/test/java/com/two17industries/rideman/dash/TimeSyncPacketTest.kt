package com.two17industries.rideman.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSyncPacketTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun golden_vector_matches_the_firmware_decoder() {
        // VT (shared with the firmware's test_clock.cpp):
        // 2026-07-11 19:47:00Z at UTC-5 -> local 2026-07-11 14:47 -> "2:47 PM"
        assertArrayEquals(
            bytes(0x01, 0xB4, 0x9D, 0x52, 0x6A, 0xD4, 0xFE),
            TimeSyncPacket.encode(epochUtcSec = 1783799220L, utcOffsetMinutes = -300),
        )
    }

    @Test fun packet_is_7_bytes() {
        assertEquals(7, TimeSyncPacket.encode(0L, 0).size)
    }

    @Test fun version_is_first_byte() {
        assertEquals(0x01.toByte(), TimeSyncPacket.encode(1783799220L, -300)[0])
    }

    @Test fun zero_offset_encodes_as_zero() {
        val out = TimeSyncPacket.encode(1783799220L, 0)
        assertEquals(0x00.toByte(), out[5])
        assertEquals(0x00.toByte(), out[6])
    }

    @Test fun positive_offset_encodes_little_endian() {
        // UTC+5:30 = 330 min = 0x014A
        val out = TimeSyncPacket.encode(1783799220L, 330)
        assertEquals(0x4A.toByte(), out[5])
        assertEquals(0x01.toByte(), out[6])
    }

    @Test fun negative_offset_encodes_twos_complement() {
        // UTC-5 = -300 min = 0xFED4
        val out = TimeSyncPacket.encode(1783799220L, -300)
        assertEquals(0xD4.toByte(), out[5])
        assertEquals(0xFE.toByte(), out[6])
    }

    @Test fun epoch_beyond_int_max_still_encodes_unsigned() {
        // The field is an UNSIGNED u32 (valid to 2106), so an epoch past
        // Int.MAX_VALUE (2038) must not overflow into a negative/garbage value.
        val out = TimeSyncPacket.encode(4_000_000_000L, 0)  // 0xEE6B2800
        assertArrayEquals(bytes(0x01, 0x00, 0x28, 0x6B, 0xEE, 0x00, 0x00), out)
    }

    @Test fun now_converts_phone_units_to_wire_units() {
        // millis -> seconds, millis-offset -> minutes
        assertArrayEquals(
            TimeSyncPacket.encode(1783799220L, -300),
            TimeSyncPacket.now(nowMillis = 1783799220_000L, offsetMillis = -300 * 60 * 1000),
        )
    }
}
