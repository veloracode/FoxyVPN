package com.vauth.foxyvpn.data

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

/**
 * RelayFallbackInterceptor driven through a scripted [Interceptor.Chain]:
 * each proceed() consumes the next scripted outcome, so every branch —
 * fallback triggers, relay refusal, the relay-first window, replay safety —
 * is exercised deterministically, without sockets. The same flows over a
 * real OkHttp transport are covered by RelayEndToEndTest.
 */
class RelayFallbackInterceptorTest {

    private val fxaUrl = "https://api.accounts.firefox.com/v1/oauth/token"
    private val guardianUrl = "https://vpn.mozilla.org/api/v2/vpn/login"

    /** Shared script across calls: outcomes in order, every request seen. */
    private class Script {
        val outcomes = ArrayDeque<(Request) -> Response>()
        val requests = mutableListOf<Request>()
        val connectTimeoutsMs = mutableListOf<Int>()

        fun respond(code: Int, body: String = "", headers: Headers = Headers.Builder().build()) {
            outcomes.addLast { request -> response(request, code, body, headers) }
        }

        fun fail(error: IOException) {
            outcomes.addLast { throw error }
        }
    }

    private class FakeChain(
        private val script: Script,
        private val initialRequest: Request,
        private val connectTimeoutMs: Int = 30_000,
    ) : Interceptor.Chain {
        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            script.requests += request
            script.connectTimeoutsMs += connectTimeoutMs
            val outcome = script.outcomes.removeFirstOrNull()
                ?: throw AssertionError("proceed() called with no scripted outcome left")
            return outcome(request)
        }

        override fun connection(): Connection? = null

        override fun call(): Call = throw UnsupportedOperationException("not used by the interceptor")

        override fun connectTimeoutMillis(): Int = connectTimeoutMs

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
            FakeChain(script, initialRequest, unit.toMillis(timeout.toLong()).toInt())

        override fun readTimeoutMillis(): Int = 30_000

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 30_000

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private var relayBase: HttpUrl? = "https://relay.example.com/r?key=secret".toHttpUrl()
    private val interceptor = RelayFallbackInterceptor(relayProvider = { relayBase })
    private val script = Script()

    private fun execute(request: Request): Response =
        interceptor.intercept(FakeChain(script, request))

    private fun get(url: String): Pair<Int, String> =
        execute(Request.Builder().url(url).get().build()).use { resp ->
            resp.code to (resp.body?.string().orEmpty())
        }

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    companion object {
        fun response(request: Request, code: Int, body: String, headers: Headers): Response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("X")
                .headers(headers)
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
    }

    // ------------------------------------------------------------------
    // Off / unchanged behavior
    // ------------------------------------------------------------------

    @Test
    fun relayOff_requestPassesThroughOnce() {
        relayBase = null
        script.respond(200, """{"ok":true}""")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("""{"ok":true}""", body)
        assertEquals(1, script.requests.size)
        assertEquals(fxaUrl, script.requests[0].url.toString())
    }

    @Test
    fun directSuccess_relayNeverTouched() {
        script.respond(200, "direct")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("direct", body)
        assertEquals(1, script.requests.size)
        assertEquals("api.accounts.firefox.com", script.requests[0].url.host)
    }

    @Test
    fun nonConnectPhaseFailure_isNotRelayed() {
        script.fail(IOException("unexpected end of stream"))
        try {
            execute(Request.Builder().url(fxaUrl).get().build())
            fail("expected the failure to propagate")
        } catch (e: IOException) {
            assertEquals("unexpected end of stream", e.message)
        }
        assertEquals(1, script.requests.size)
    }

    // ------------------------------------------------------------------
    // Fallback triggers
    // ------------------------------------------------------------------

    @Test
    fun directConnectFailure_fallsBackToRelay_preservingEverything() {
        script.fail(ConnectException("connection refused"))
        script.respond(200, """{"via":"relay"}""")
        val request = Request.Builder()
            .url("$fxaUrl?grant=1")
            .header("Authorization", "Bearer abc")
            .post("""{"code":"xyz"}""".toRequestBody("application/json".toMediaType()))
            .build()
        execute(request).use { resp ->
            assertEquals(200, resp.code)
            assertEquals("""{"via":"relay"}""", resp.body?.string())
        }
        assertEquals(2, script.requests.size)
        val relayed = script.requests[1]
        assertEquals("relay.example.com", relayed.url.host)
        assertEquals("secret", relayed.url.queryParameter("key"))
        assertEquals("$fxaUrl?grant=1", relayed.url.queryParameter("url"))
        assertEquals("POST", relayed.method)
        assertEquals("""{"code":"xyz"}""", relayed.bodyString())
        assertEquals("Bearer abc", relayed.header("Authorization"))
        // The direct attempt ran with the short connect timeout; the relay
        // attempt kept the chain's normal one.
        assertEquals(
            RelayFallbackInterceptor.DIRECT_CONNECT_TIMEOUT_SECONDS * 1000,
            script.connectTimeoutsMs[0],
        )
        assertEquals(30_000, script.connectTimeoutsMs[1])
    }

    @Test
    fun direct406FromFxaHost_fallsBackToRelay() {
        script.respond(406, "blocked")
        script.respond(200, "relay")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("relay", body)
        assertEquals(2, script.requests.size)
        assertEquals("relay.example.com", script.requests[1].url.host)
    }

    @Test
    fun direct406FromNonFxaHost_isNotRelayed() {
        script.respond(406, "blocked")
        val (code, body) = get(guardianUrl)
        assertEquals(406, code)
        assertEquals("blocked", body)
        assertEquals(1, script.requests.size)
    }

    @Test
    fun direct451_fallsBackToRelay() {
        script.respond(451, "region")
        script.respond(200, "relay")
        val (code, body) = get(guardianUrl)
        assertEquals(200, code)
        assertEquals("relay", body)
        assertEquals(2, script.requests.size)
    }

    // ------------------------------------------------------------------
    // Relay failure surfaces the original result
    // ------------------------------------------------------------------

    @Test
    fun relayRefusal_surfacesOriginalResponseWithBody() {
        script.respond(406, """{"error":"fastly"}""")
        script.respond(200, "", Headers.Builder().set("X-Relay-Error", "nope").build())
        val (code, body) = get(fxaUrl)
        assertEquals(406, code)
        assertEquals("""{"error":"fastly"}""", body)
        assertEquals(2, script.requests.size)
    }

    @Test
    fun relayFailureAfter406_surfacesOriginalResponse() {
        script.respond(406, "blocked-body")
        script.fail(ConnectException("relay unreachable"))
        val (code, body) = get(fxaUrl)
        assertEquals(406, code)
        assertEquals("blocked-body", body)
    }

    @Test
    fun bothWaysFail_directErrorSurfaces() {
        script.fail(ConnectException("direct refused"))
        script.fail(ConnectException("relay refused"))
        try {
            execute(Request.Builder().url(fxaUrl).get().build())
            fail("expected the direct error to surface")
        } catch (e: IOException) {
            assertTrue(e is ConnectException)
            assertEquals("direct refused", e.message)
        }
    }

    @Test
    fun relayAnswer_isFinal_exactlyOneRelayAttempt() {
        script.respond(406, "blocked")
        script.respond(500, "relay-error")
        val (code, body) = get(fxaUrl)
        assertEquals(500, code)
        assertEquals("relay-error", body)
        assertEquals(2, script.requests.size)
    }

    // ------------------------------------------------------------------
    // Relay-first window
    // ------------------------------------------------------------------

    @Test
    fun afterFallback_nextRequestsGoThroughRelayFirst() {
        script.respond(406, "blocked")
        script.respond(200, "relay")
        script.respond(200, "relay")
        assertEquals(200, get(fxaUrl).first)
        assertEquals(200, get(fxaUrl).first)
        assertEquals(3, script.requests.size)
        assertEquals("api.accounts.firefox.com", script.requests[0].url.host)
        assertEquals("relay.example.com", script.requests[1].url.host)
        assertEquals("relay.example.com", script.requests[2].url.host)
    }

    @Test
    fun relayFirstWindow_clearsWhenRelayFailsAndDirectAnswers() {
        script.respond(406, "blocked")
        script.respond(200, "relay")
        assertEquals(200, get(fxaUrl).first)
        // Relay now refuses, direct now answers: relay-first fails over to
        // direct and the window closes.
        script.respond(200, "", Headers.Builder().set("X-Relay-Error", "down").build())
        script.respond(200, "direct")
        val second = get(fxaUrl)
        assertEquals(200, second.first)
        assertEquals("direct", second.second)
        // Third request goes directly again.
        script.respond(200, "direct")
        assertEquals(200, get(fxaUrl).first)
        val hosts = script.requests.map { it.url.host }
        assertEquals(
            listOf(
                "api.accounts.firefox.com", "relay.example.com",
                "relay.example.com", "api.accounts.firefox.com",
                "api.accounts.firefox.com",
            ),
            hosts,
        )
    }

    // ------------------------------------------------------------------
    // Replay safety
    // ------------------------------------------------------------------

    @Test
    fun oneShotBody_isNeverRelayed() {
        script.respond(406, "blocked")
        val oneShot = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("{}")
            }
        }
        val request = Request.Builder().url(fxaUrl).post(oneShot).build()
        execute(request).use { resp -> assertEquals(406, resp.code) }
        assertEquals(1, script.requests.size)
    }

    @Test
    fun buildRelayRequest_mergesIntoRelayQueryAndDropsHostHeader() {
        val request = Request.Builder()
            .url("https://api.accounts.firefox.com/v1/oauth/token?a=b")
            .header("Host", "api.accounts.firefox.com")
            .get()
            .build()
        val relayed = buildRelayRequest(
            request,
            "https://relay.example.com/r?key=secret".toHttpUrl(),
        )
        assertEquals("secret", relayed.url.queryParameter("key"))
        assertEquals(
            "https://api.accounts.firefox.com/v1/oauth/token?a=b",
            relayed.url.queryParameter("url"),
        )
        assertEquals("relay.example.com", relayed.url.host)
        assertEquals(null, relayed.header("Host"))
    }
}
