package com.two17industries.rideman.hrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateMeasurementTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `uint8 heart rate with no optional fields`() {
        // flags 0x00: 8-bit HR, contact not supported, no energy, no RR
        val m = HeartRateMeasurement.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertNull(m.energyKj)
        assertTrue(m.rrIntervalsMs.isEmpty())
    }

    @Test
    fun `uint8 above 127 is not sign extended`() {
        // 200 bpm would be negative if the byte were read as signed
        val m = HeartRateMeasurement.parse(bytes(0x00, 200))!!
        assertEquals(200, m.bpm)
    }

    @Test
    fun `uint16 heart rate is little endian`() {
        // flags 0x01: 16-bit HR. 0x012C = 300
        val m = HeartRateMeasurement.parse(bytes(0x01, 0x2C, 0x01))!!
        assertEquals(300, m.bpm)
    }

    @Test
    fun `contact supported and detected reports contactOk true`() {
        // bits 1-2 = 0b11 -> flags 0x06
        val m = HeartRateMeasurement.parse(bytes(0x06, 72))!!
        assertTrue(m.contactOk)
    }

    @Test
    fun `contact supported but not detected reports contactOk false`() {
        // bits 1-2 = 0b10 -> flags 0x04
        val m = HeartRateMeasurement.parse(bytes(0x04, 72))!!
        assertEquals(false, m.contactOk)
    }

    @Test
    fun `contact not supported is treated as good contact`() {
        // bits 1-2 = 0b00. Straps that do not report contact must not be gated out entirely.
        val m = HeartRateMeasurement.parse(bytes(0x00, 72))!!
        assertTrue(m.contactOk)
    }

    @Test
    fun `energy expended is parsed when present`() {
        // flags 0x08: energy present. 0x0100 = 256 kJ
        val m = HeartRateMeasurement.parse(bytes(0x08, 72, 0x00, 0x01))!!
        assertEquals(256, m.energyKj)
    }

    @Test
    fun `single rr interval is converted from 1024ths to milliseconds`() {
        // flags 0x10: RR present. 1024 -> exactly 1000 ms
        val m = HeartRateMeasurement.parse(bytes(0x10, 60, 0x00, 0x04))!!
        assertEquals(1, m.rrIntervalsMs.size)
        assertEquals(1000.0, m.rrIntervalsMs[0], 0.001)
    }

    @Test
    fun `multiple rr intervals in one notification are all parsed`() {
        // Two RRs: 1024 (1000ms) and 512 (500ms). Must not stop after the first.
        val m = HeartRateMeasurement.parse(bytes(0x10, 60, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(2, m.rrIntervalsMs.size)
        assertEquals(1000.0, m.rrIntervalsMs[0], 0.001)
        assertEquals(500.0, m.rrIntervalsMs[1], 0.001)
    }

    @Test
    fun `energy and rr together are parsed in the correct order`() {
        // flags 0x18: energy then RR. energy 0x0002 = 2, RR 1024
        val m = HeartRateMeasurement.parse(bytes(0x18, 60, 0x02, 0x00, 0x00, 0x04))!!
        assertEquals(2, m.energyKj)
        assertEquals(1, m.rrIntervalsMs.size)
        assertEquals(1000.0, m.rrIntervalsMs[0], 0.001)
    }

    @Test
    fun `empty buffer returns null`() {
        assertNull(HeartRateMeasurement.parse(ByteArray(0)))
    }

    @Test
    fun `flags byte with no heart rate returns null`() {
        assertNull(HeartRateMeasurement.parse(bytes(0x00)))
    }

    @Test
    fun `truncated uint16 heart rate returns null`() {
        assertNull(HeartRateMeasurement.parse(bytes(0x01, 0x2C)))
    }

    @Test
    fun `truncated energy field returns null`() {
        assertNull(HeartRateMeasurement.parse(bytes(0x08, 72, 0x00)))
    }

    @Test
    fun `trailing odd byte in the rr list is ignored`() {
        // A malformed strap that appends a stray byte must still yield the valid RR.
        val m = HeartRateMeasurement.parse(bytes(0x10, 60, 0x00, 0x04, 0x7F))!!
        assertEquals(1, m.rrIntervalsMs.size)
    }
}
