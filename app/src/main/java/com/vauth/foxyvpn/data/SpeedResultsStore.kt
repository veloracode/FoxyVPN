package com.vauth.foxyvpn.data

import android.content.Context
import org.json.JSONObject

/**
 * Last speed-test result per server (Phase B2). Stored as a small
 * JSON map in its own prefs file: server key (the candidate authority,
 * or "direct") → {down, up, ping, at}. Results expire after 24 h —
 * [isFresh] is the pure check the UI applies before showing one.
 */
internal object SpeedResultsStore {
    private const val PREFS_NAME = "foxyvpn_speed_results"
    private const val KEY_RESULTS = "results"
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    data class Entry(val downMbps: Double?, val upMbps: Double?, val pingMs: Double?, val atMs: Long)

    fun isFresh(atMs: Long, nowMs: Long): Boolean =
        atMs > 0L && nowMs - atMs in 0..MAX_AGE_MS

    fun save(context: Context, serverKey: String, entry: Entry) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_RESULTS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        root.put(
            serverKey,
            JSONObject().apply {
                entry.downMbps?.let { put("down", it) }
                entry.upMbps?.let { put("up", it) }
                entry.pingMs?.let { put("ping", it) }
                put("at", entry.atMs)
            },
        )
        prefs.edit().putString(KEY_RESULTS, root.toString()).apply()
    }

    fun load(context: Context, serverKey: String, nowMs: Long = System.currentTimeMillis()): Entry? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_RESULTS, "{}") ?: "{}") }.getOrNull()
            ?: return null
        val item = root.optJSONObject(serverKey) ?: return null
        val at = item.optLong("at", 0L)
        if (!isFresh(at, nowMs)) return null
        return Entry(
            downMbps = if (item.has("down")) item.optDouble("down") else null,
            upMbps = if (item.has("up")) item.optDouble("up") else null,
            pingMs = if (item.has("ping")) item.optDouble("ping") else null,
            atMs = at,
        )
    }
}
