package com.vauth.foxyvpn.ui.speedtest

import com.vauth.foxyvpn.data.SpeedResultsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Speed test (B2): the math, the quota gate, and result expiry. */
class SpeedTestMathTest {

    @Test
    fun median_oddEvenEmpty() {
        assertNull(SpeedTestMath.median(emptyList()))
        assertEquals(3.0, SpeedTestMath.median(listOf(5.0, 1.0, 3.0))!!, 1e-9)
        assertEquals(2.5, SpeedTestMath.median(listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
    }

    @Test
    fun p90_nearestRank() {
        assertNull(SpeedTestMath.p90(emptyList()))
        // 10 samples -> rank ceil(0.9*10)=9 -> 9th smallest.
        val values = (1..10).map { it.toDouble() }
        assertEquals(9.0, SpeedTestMath.p90(values)!!, 1e-9)
        assertEquals(42.0, SpeedTestMath.p90(listOf(42.0))!!, 1e-9)
    }

    @Test
    fun serverTiming_parse() {
        assertEquals(
            12.5,
            SpeedTestMath.serverTimingMs("cfCacheStatus;desc=\"MISS\", cfReqDur;dur=12.5")!!,
            1e-9,
        )
        assertNull(SpeedTestMath.serverTimingMs("cfCacheStatus;desc=\"HIT\""))
        assertNull(SpeedTestMath.serverTimingMs(null))
    }

    @Test
    fun formatMbps_shapes() {
        assertEquals("—", SpeedTestMath.formatMbps(null))
        assertEquals("12.3", SpeedTestMath.formatMbps(12.34))
        assertEquals("250", SpeedTestMath.formatMbps(250.4))
    }

    @Test
    fun quotaGate_truthTable() {
        // Unlimited plan -> full test regardless.
        assertEquals(SpeedGate.Full, speedGateFor(unlimited = true, remainingBytes = null))
        assertEquals(SpeedGate.Full, speedGateFor(unlimited = true, remainingBytes = 1L))
        // Unknown quota -> ping only, never transfers.
        assertEquals(SpeedGate.PingOnly, speedGateFor(unlimited = false, remainingBytes = null))
        // Comfortably above the reserve -> full.
        assertEquals(SpeedGate.Full, speedGateFor(unlimited = false, remainingBytes = 250_000_000L))
        // Below the reserve -> confirm, naming remaining + true max cost.
        val gate = speedGateFor(unlimited = false, remainingBytes = 100_000_000L)
        assertTrue(gate is SpeedGate.Confirm)
        gate as SpeedGate.Confirm
        assertEquals(100_000_000L, gate.remainingBytes)
        assertEquals(
            SpeedTestMath.MAX_DOWN_TOTAL + SpeedTestMath.MAX_UP_TOTAL,
            gate.maxCostBytes,
        )
    }

    @Test
    fun resultFreshness_window() {
        val now = 1_700_000_000_000L
        assertTrue(SpeedResultsStore.isFresh(now - 60_000L, now))
        assertTrue(SpeedResultsStore.isFresh(now - SpeedResultsStore.MAX_AGE_MS, now))
        assertFalse(SpeedResultsStore.isFresh(now - SpeedResultsStore.MAX_AGE_MS - 1L, now))
        assertFalse(SpeedResultsStore.isFresh(0L, now))
        assertFalse(SpeedResultsStore.isFresh(now + 60_000L, now))
    }
}
