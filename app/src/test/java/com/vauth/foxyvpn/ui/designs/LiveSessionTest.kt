package com.vauth.foxyvpn.ui.designs

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression gate for the v1.1 "not smooth" verdict on Pixel/Old:
 * the pre-rescue LiveSession stored plain values read inside
 * rememberLiveSession, so every 1 Hz elapsed tick and every traffic
 * emission recomposed the whole caller (door, hero, details). The
 * state-backed holder must (a) gate values on live, (b) track state
 * mutations through the SAME instance — leaves read fresh values
 * without any parent rebuilding the holder.
 */
class LiveSessionTest {

    private fun holder(
        live: Boolean,
        stats: androidx.compose.runtime.MutableState<SessionStats> =
            mutableStateOf(SessionStats()),
        connectedAt: androidx.compose.runtime.MutableState<Long?> =
            mutableStateOf(null),
        elapsed: androidx.compose.runtime.MutableState<Int> =
            mutableIntStateOf(0),
        down: androidx.compose.runtime.MutableState<List<Float>> =
            mutableStateOf(emptyList()),
        up: androidx.compose.runtime.MutableState<List<Float>> =
            mutableStateOf(emptyList()),
    ) = LiveSession(live, stats, connectedAt, elapsed, down, up)

    @Test
    fun notLive_reportsZerosEvenWithStatsPresent() {
        val stats = mutableStateOf(SessionStats(rxBps = 500, txBps = 250, sessionBytes = 999))
        val s = holder(live = false, stats = stats)
        assertEquals(0, s.downBps)
        assertEquals(0, s.upBps)
        assertEquals(0, s.sessionBytes)
    }

    @Test
    fun sameInstance_tracksStateMutations() {
        val stats = mutableStateOf(SessionStats())
        val connectedAt = mutableStateOf<Long?>(null)
        val elapsed = mutableIntStateOf(0)
        val down = mutableStateOf<List<Float>>(emptyList())
        val s = holder(live = true, stats = stats, connectedAt = connectedAt, elapsed = elapsed, down = down)
        val same = s
        stats.value = SessionStats(rxBps = 1200, txBps = 300, sessionBytes = 4096)
        connectedAt.value = 777L
        elapsed.intValue = 42
        down.value = listOf(1f, 2f, 3f)
        assertSame(same, s)
        assertEquals(1200, s.downBps)
        assertEquals(300, s.upBps)
        assertEquals(4096, s.sessionBytes)
        assertEquals(777L, s.sessionKey)
        assertEquals(42, s.elapsedSeconds)
        assertEquals(listOf(1f, 2f, 3f), s.downHistory)
    }

    @Test
    fun sessionKey_isZeroWithoutConnectionStart() {
        val s = holder(live = true)
        assertEquals(0L, s.sessionKey)
    }
}
