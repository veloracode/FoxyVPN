package com.vauth.foxyvpn.ui.speedtest

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Speed test engine (Phase B2, DESIGN-SYSTEM §10) — a clean-room
 * implementation of the reference behavior on FoxyVPN's own transport:
 *
 *  - Target: Cloudflare's speed test (speed.cloudflare.com /__down,
 *    /__up, /__meta), the same endpoints the reference uses.
 *  - Transport: when the VPN is connected, the client is the app's
 *    local-proxy client (the tunnel's own local proxy — every byte
 *    rides the tunnel, like ExitCheck's proxied lookup); when not
 *    connected, a plain direct client, and the UI labels the route
 *    "Direct" — a measurement outside the tunnel is never presented
 *    as VPN speed.
 *  - Escalation 100KB → 1MB → 10MB → 25MB per direction with the
 *    finish rule (a request slower than 1000 ms ends escalation);
 *    bandwidth = 90th percentile of per-request samples ≥ 10 ms;
 *    latency = median of (TTFB − cfReqDur).
 *  - FoxyVPN adaptation (documented in DESIGN-SYSTEM §10.2): hard
 *    total caps — 60 MB down / 12 MB up per run — because the tunnel
 *    runs on a metered Mozilla quota; the screen additionally gates
 *    the run on the sealed quota model before the engine starts.
 *  - Cancel is real: in-flight calls are cancelled and the run stops.
 */
enum class SpeedPhase { Idle, Ping, Download, Upload, Done, Stopped, Error }

data class SpeedTestState(
    val phase: SpeedPhase = SpeedPhase.Idle,
    val pingMs: Double? = null,
    val liveMbps: Double = 0.0,
    val liveStepLabel: String = "",
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val bytesUsed: Long = 0,
    val elapsedSeconds: Int = 0,
    val meta: SpeedMeta? = null,
    val error: String? = null,
)

data class SpeedMeta(
    val clientIp: String?,
    val colo: String?,
    val country: String?,
    val city: String?,
    val asOrganization: String?,
)

object SpeedTestMath {
    val DOWN_STEPS: List<Long> = listOf(100_000L, 1_000_000L, 10_000_000L, 25_000_000L)
    val UP_STEPS: List<Long> = listOf(10_000L, 100_000L, 500_000L, 1_000_000L)
    const val FINISH_REQUEST_MS = 1000.0
    const val MAX_DOWN_TOTAL = 60_000_000L
    const val MAX_UP_TOTAL = 12_000_000L

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** 90th percentile (nearest-rank), the reference's bandwidth rule. */
    fun p90(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = kotlin.math.ceil(0.90 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Parse cfReqDur (ms) out of a Server-Timing header value. */
    fun serverTimingMs(header: String?): Double? {
        if (header == null) return null
        val match = Regex("""cfReqDur;dur=([0-9.]+)""").find(header) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun formatMbps(mbps: Double?): String =
        if (mbps == null) "—" else if (mbps >= 100) "%.0f".format(mbps) else "%.1f".format(mbps)
}

class SpeedTestEngine(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val host: String = "speed.cloudflare.com",
) {
    private val _state = MutableStateFlow(SpeedTestState())
    val state: StateFlow<SpeedTestState> = _state

    @Volatile private var cancelled = false
    private val inFlight = CopyOnWriteArrayList<okhttp3.Call>()
    private var job: Job? = null

    fun start(allowTransfers: Boolean) {
        cancel()
        cancelled = false
        job = scope.launch {
            _state.value = SpeedTestState(phase = SpeedPhase.Ping)
            val startedAt = System.nanoTime()
            try {
                val ping = measurePing()
                _state.value = _state.value.copy(
                    pingMs = ping,
                    elapsedSeconds = elapsedSeconds(startedAt),
                )
                var down: Double? = null
                var up: Double? = null
                if (allowTransfers) {
                    _state.value = _state.value.copy(phase = SpeedPhase.Download)
                    down = measureDownload(startedAt)
                    throwIfCancelled()
                    _state.value = _state.value.copy(
                        phase = SpeedPhase.Upload, downMbps = down, liveMbps = 0.0,
                        elapsedSeconds = elapsedSeconds(startedAt),
                    )
                    up = measureUpload(startedAt)
                }
                val meta = fetchMeta()
                _state.value = _state.value.copy(
                    phase = SpeedPhase.Done,
                    downMbps = down,
                    upMbps = up,
                    meta = meta,
                    liveMbps = 0.0,
                    elapsedSeconds = elapsedSeconds(startedAt),
                )
            } catch (cancelledError: CancellationException) {
                _state.value = _state.value.copy(phase = SpeedPhase.Stopped, liveMbps = 0.0)
            } catch (error: Exception) {
                if (cancelled) {
                    _state.value = _state.value.copy(phase = SpeedPhase.Stopped, liveMbps = 0.0)
                } else {
                    _state.value = _state.value.copy(
                        phase = SpeedPhase.Error,
                        error = error.message ?: "Speed test failed",
                        liveMbps = 0.0,
                    )
                }
            }
        }
    }

    fun cancel() {
        cancelled = true
        inFlight.forEach { runCatching { it.cancel() } }
        job?.cancel()
    }

    private fun throwIfCancelled() {
        if (cancelled) throw CancellationException("cancelled")
    }

    private fun elapsedSeconds(startedAtNanos: Long): Int =
        ((System.nanoTime() - startedAtNanos) / 1_000_000_000L).toInt()

    private fun addBytes(n: Long) {
        _state.value = _state.value.copy(bytesUsed = _state.value.bytesUsed + n)
    }

    private suspend fun measurePing(): Double? {
        val samples = ArrayList<Double>()
        repeat(5) {
            throwIfCancelled()
            val sample = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://$host/__down?bytes=0")
                    .build()
                val call = client.newCall(request)
                inFlight += call
                try {
                    val t0 = System.nanoTime()
                    call.execute().use { response ->
                        val t1 = System.nanoTime()
                        if (!response.isSuccessful) return@use null
                        val elapsedMs = (t1 - t0) / 1_000_000.0
                        val serverMs = SpeedTestMath.serverTimingMs(response.header("Server-Timing")) ?: 0.0
                        (elapsedMs - serverMs).coerceAtLeast(0.0)
                    }
                } finally {
                    inFlight -= call
                }
            }
            if (sample != null) samples += sample
        }
        return SpeedTestMath.median(samples)
    }

    private suspend fun measureDownload(startedAt: Long): Double? {
        val samples = ArrayList<Double>()
        var total = 0L
        for (step in SpeedTestMath.DOWN_STEPS) {
            throwIfCancelled()
            if (total + step > SpeedTestMath.MAX_DOWN_TOTAL) break
            val (bytes, durationMs) = downloadOnce(step)
            total += bytes
            addBytes(bytes)
            if (durationMs >= 10.0 && bytes > 0) {
                val mbps = bytes * 8.0 / (durationMs / 1000.0) / 1_000_000.0
                samples += mbps
                _state.value = _state.value.copy(
                    liveMbps = mbps,
                    liveStepLabel = stepLabel(step),
                    elapsedSeconds = elapsedSeconds(startedAt),
                )
            }
            if (durationMs > SpeedTestMath.FINISH_REQUEST_MS) break
        }
        return SpeedTestMath.p90(samples)
    }

    private suspend fun downloadOnce(bytes: Long): Pair<Long, Double> =
        withContext(Dispatchers.IO) {
            throwIfCancelled()
            val request = Request.Builder()
                .url("https://$host/__down?bytes=$bytes")
                .build()
            val call = client.newCall(request)
            inFlight += call
            try {
                val t0 = System.nanoTime()
                var read = 0L
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response")
                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { stream ->
                        while (true) {
                            if (cancelled) throw CancellationException("cancelled")
                            val n = stream.read(buffer)
                            if (n < 0) break
                            read += n
                        }
                    }
                }
                val durationMs = (System.nanoTime() - t0) / 1_000_000.0
                read to durationMs
            } finally {
                inFlight -= call
            }
        }

    private suspend fun measureUpload(startedAt: Long): Double? {
        val samples = ArrayList<Double>()
        var total = 0L
        for (step in SpeedTestMath.UP_STEPS) {
            throwIfCancelled()
            if (total + step > SpeedTestMath.MAX_UP_TOTAL) break
            val durationMs = uploadOnce(step)
            total += step
            addBytes(step)
            if (durationMs >= 10.0) {
                val mbps = step * 8.0 / (durationMs / 1000.0) / 1_000_000.0
                samples += mbps
                _state.value = _state.value.copy(
                    liveMbps = mbps,
                    liveStepLabel = stepLabel(step),
                    elapsedSeconds = elapsedSeconds(startedAt),
                )
            }
            if (durationMs > SpeedTestMath.FINISH_REQUEST_MS) break
        }
        return SpeedTestMath.p90(samples)
    }

    private suspend fun uploadOnce(bytes: Long): Double =
        withContext(Dispatchers.IO) {
            throwIfCancelled()
            val payload = ByteArray(bytes.toInt())
            val request = Request.Builder()
                .url("https://$host/__up")
                .post(payload.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            val call = client.newCall(request)
            inFlight += call
            try {
                val t0 = System.nanoTime()
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    response.body?.close()
                }
                (System.nanoTime() - t0) / 1_000_000.0
            } finally {
                inFlight -= call
            }
        }

    private suspend fun fetchMeta(): SpeedMeta? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("https://$host/__meta").build()
                val call = client.newCall(request)
                inFlight += call
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val text = response.body?.string() ?: return@use null
                        val json = JSONObject(text)
                        SpeedMeta(
                            clientIp = json.optString("clientIp").ifBlank { null },
                            colo = json.optString("colo").ifBlank { null },
                            country = json.optString("country").ifBlank { null },
                            city = json.optString("city").ifBlank { null },
                            asOrganization = json.optString("asOrganization").ifBlank { null },
                        )
                    }
                } finally {
                    inFlight -= call
                }
            }.getOrNull()
        }

    private fun stepLabel(bytes: Long): String = when {
        bytes < 1_000_000L -> "${bytes / 1_000} KB"
        else -> "${bytes / 1_000_000} MB"
    }
}
