package com.two17industries.rideman.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `onScanFailed` used to be dropped entirely, leaving the picker showing "Searching… the strap
 * must be worn and damp to transmit." forever — telling the rider to check a strap when the scan
 * never started. Every message here must name Bluetooth, not the strap.
 *
 * Literal error codes are used deliberately: these are the platform's stable
 * `ScanCallback.SCAN_FAILED_*` values, and asserting on the literals keeps this a pure JVM test.
 */
class HrmScanFailureTest {

    private val allCodes = 1..6

    @Test
    fun `every documented failure code gets its own message`() {
        val messages = allCodes.map { scanFailureMessage(it) }
        assertTrue("messages must be distinct per code", messages.toSet().size == messages.size)
    }

    @Test
    fun `no failure message blames the strap`() {
        (allCodes.map { scanFailureMessage(it) } + scanFailureMessage(99)).forEach { msg ->
            val lower = msg.lowercase()
            assertTrue("must not mention the strap: $msg", !lower.contains("strap"))
            assertTrue("must not mention electrodes: $msg", !lower.contains("electrode"))
            assertTrue("must not tell the rider to wear or moisten anything: $msg",
                !lower.contains("worn") && !lower.contains("damp") && !lower.contains("moisten"))
        }
    }

    @Test
    fun `every failure message names bluetooth so the rider knows where to look`() {
        (allCodes.map { scanFailureMessage(it) } + scanFailureMessage(99)).forEach { msg ->
            assertTrue("must name Bluetooth: $msg", msg.lowercase().contains("bluetooth"))
        }
    }

    @Test
    fun `scanning too frequently tells the rider to wait rather than retry immediately`() {
        // Code 6 is SCAN_FAILED_SCANNING_TOO_FREQUENTLY, reachable when a background scan burns
        // the 5-starts-per-30-seconds quota.
        val msg = scanFailureMessage(6).lowercase()
        assertTrue("must ask the rider to wait: $msg", msg.contains("wait"))
    }

    @Test
    fun `an unknown code still surfaces something actionable and carries the code`() {
        val msg = scanFailureMessage(4242)
        assertTrue("must include the raw code for diagnosis: $msg", msg.contains("4242"))
        assertNotEquals(scanFailureMessage(1), msg)
    }
}
