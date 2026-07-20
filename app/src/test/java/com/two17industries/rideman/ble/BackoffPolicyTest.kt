package com.two17industries.rideman.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackoffPolicyTest {

    @Test
    fun `first attempt waits one second`() {
        assertEquals(1_000L, BackoffPolicy.delayMsFor(0))
    }

    @Test
    fun `delay doubles each attempt`() {
        assertEquals(2_000L, BackoffPolicy.delayMsFor(1))
        assertEquals(4_000L, BackoffPolicy.delayMsFor(2))
        assertEquals(8_000L, BackoffPolicy.delayMsFor(3))
        assertEquals(16_000L, BackoffPolicy.delayMsFor(4))
    }

    @Test
    fun `delay caps at thirty seconds`() {
        assertEquals(30_000L, BackoffPolicy.delayMsFor(5))
        assertEquals(30_000L, BackoffPolicy.delayMsFor(6))
        assertEquals(30_000L, BackoffPolicy.delayMsFor(19))
    }

    @Test
    fun `null once the retry cap is exhausted`() {
        assertNull(BackoffPolicy.delayMsFor(20))
        assertNull(BackoffPolicy.delayMsFor(21))
    }

    @Test
    fun `negative attempt is treated as the first`() {
        assertEquals(1_000L, BackoffPolicy.delayMsFor(-1))
    }
}
