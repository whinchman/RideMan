package com.two17industries.rideman.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryPacketTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun v1_moving_us_units() {
        val t = Telemetry(
            speedMps = 6.7f, distanceM = 12437.0, elapsedSec = 2847,
            headingDeg = 315f, altitudeM = 221.0,
            unitsUS = true, rideActive = true, gpsValid = true,
        )
        assertArrayEquals(
            bytes(0x01, 0x07, 0x9E, 0x02, 0x95, 0x30, 0x00, 0x00, 0x1F, 0x0B, 0x00, 0x00, 0x3B, 0x01, 0xDD, 0x00),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun v2_zero_metric_no_fix() {
        val t = Telemetry(0f, 0.0, 0, 0f, 0.0, unitsUS = false, rideActive = true, gpsValid = false)
        assertArrayEquals(
            bytes(0x01, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun v3_clamps_and_wraps() {
        val t = Telemetry(
            speedMps = 13.9f, distanceM = 50000.0, elapsedSec = 7325,
            headingDeg = 372f, altitudeM = -45.0, // heading wraps to 12
            unitsUS = false, rideActive = true, gpsValid = true,
        )
        assertArrayEquals(
            bytes(0x01, 0x06, 0x6E, 0x05, 0x50, 0xC3, 0x00, 0x00, 0x9D, 0x1C, 0x00, 0x00, 0x0C, 0x00, 0xD3, 0xFF),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun theme_index_encodes_into_flags_bits_3_4() {
        // VT (shared with the firmware decoder test): US+ride+fix + theme 2 → flags 0x17.
        val t = Telemetry(
            0f, 0.0, 0, 0f, 0.0,
            unitsUS = true, rideActive = true, gpsValid = true, theme = 2,
        )
        val out = TelemetryPacket.encode(t)
        assertEquals(0x01.toByte(), out[0])
        assertEquals(0x17.toByte(), out[1])
    }

    @Test fun packet_is_16_bytes() {
        val t = Telemetry(1f, 1.0, 1, 1f, 1.0, unitsUS = true, rideActive = true, gpsValid = true)
        assertEquals(16, TelemetryPacket.encode(t).size)
    }

    @Test fun speed_clamps_at_u16_max() {
        val t = Telemetry(1000f, 0.0, 0, 0f, 0.0, unitsUS = false, rideActive = false, gpsValid = false)
        val out = TelemetryPacket.encode(t)
        // 1000 m/s -> 100000 cm/s, clamps to 65535 = 0xFFFF
        assertEquals(0xFF.toByte(), out[2])
        assertEquals(0xFF.toByte(), out[3])
    }
}
