package com.vauth.foxyvpn.ui.designs

import androidx.compose.runtime.State

/** Snapshot of the live traffic counters, mapped off-composition. */
data class SessionStats(
    val rxBps: Long = 0,
    val txBps: Long = 0,
    val sessionBytes: Long = 0,
)

/**
 * Live session values for the Pixel/Old designs (rates, totals,
 * elapsed, sparkline history).
 *
 * Rescue (v1.1.1): this holder is STATE-BACKED — every property is a
 * getter that reads snapshot state at the exact composable that uses
 * the value. The pre-rescue version stored plain values that were
 * read inside rememberLiveSession, which subscribed the CALLER's
 * whole recompose scope (the Pixel door, the Old hero, the details
 * column) to the 1 Hz elapsed ticker and to every traffic-stats
 * emission. Now a tick recomposes only the leaf displaying it.
 * (rememberLiveSession itself lives in DesignCommon.kt.)
 */
class LiveSession internal constructor(
    private val live: Boolean,
    private val stats: State<SessionStats>,
    private val connectedAt: State<Long?>,
    private val elapsed: State<Int>,
    private val downHistoryState: State<List<Float>>,
    private val upHistoryState: State<List<Float>>,
) {
    val downBps: Long get() = if (live) stats.value.rxBps else 0
    val upBps: Long get() = if (live) stats.value.txBps else 0
    val sessionBytes: Long get() = if (live) stats.value.sessionBytes else 0
    val elapsedSeconds: Int get() = elapsed.value
    /** Stable per connection; keys the session gradient palette. */
    val sessionKey: Long get() = connectedAt.value ?: 0L
    val downHistory: List<Float> get() = downHistoryState.value
    val upHistory: List<Float> get() = upHistoryState.value
}
