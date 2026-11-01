package com.vauth.foxyvpn.data

import okhttp3.Call
import okhttp3.Callback
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
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * RelayFallbackInterceptor driven through a scripted [Interceptor.Chain]
 * (the direct attempt) plus a scripted [Call.Factory] (the relay hop on
 * its sibling client): one shared outcome queue, consumed in call order,
 * so every branch — fallback triggers, relay refusal, per-host relay-first
 * stickiness, replay safety, cookie copying — is exercised
 * deterministically, without sockets. The same flows over a real OkHttp
 * transport are covered by RelayEndToEndTest.
 */
class RelayFallbackInterceptorTest {

    private val fxaUrl = "https://api.accounts.firefox.com/v1/oauth/token"
    private val guardianUrl = "https://vpn.mozilla.org/api/v2/vpn/login"

    private class Script {
        val outcomes = ArrayDeque<(Request) -> Response>()
        val requests = mutableListOf<Request>()
        val connectTimeoutsMs = mutableListOf<Int>()
        val readTimeoutsMs = mutableListOf<Int>()

        fun respond(code: Int, body: String = "", headers: Headers = Headers.Builder().build()) {
            outcomes.addLast { request -> response(request, code, body, headers) }
        }

        fun fail(error: IOException) {
            outcomes.addLast { throw error }
        }

        fun next(request: Request): Response {
            val outcome = outcomes.removeFirstOrNull()
                ?: throw AssertionError("call made with no scripted outcome left")
            return outcome(request)
        }
    }

    private class FakeCall(
        private val callRequest: Request,
        private val executor: (Request) -> Response = {
            throw UnsupportedOperationException("execute() not expected")
        },
    ) : Call {
        override fun request(): Request = callRequest
        override fun execute(): Response = executor(callRequest)
        override fun enqueue(responseCallback: Callback) =
            throw UnsupportedOperationException("enqueue() not expected")

        override fun cancel() = Unit
        override fun isExecuted(): Boolean = true
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this
    }

    private class FakeRelayFactory(private val script: Script) : Call.Factory {
        override fun newCall(request: Request): Call {
            script.requests += request
            return FakeCall(request) { script.next(it) }
        }
    }

    private class FakeChain(
        private val script: Script,
        private val initialRequest: Request,
        private val connectTimeoutMs: Int = 30_000,
        private val readTimeoutMs: Int = 30_000,
    ) : Interceptor.Chain {
        val call: Call = FakeCall(initialRequest)

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            script.requests += request
            script.connectTimeoutsMs += connectTimeoutMs
            script.readTimeoutsMs += readTimeoutMs
            return script.next(request)
        }

        override fun connection(): Connection? = null

        override fun call(): Call = call

        override fun connectTimeoutMillis(): Int = connectTimeoutMs

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
            FakeChain(script, initialRequest, unit.toMillis(timeout.toLong()).toInt(), readTimeoutMs)

        override fun readTimeoutMillis(): Int = readTimeoutMs

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
            FakeChain(script, initialRequest, connectTimeoutMs, unit.toMillis(timeout.toLong()).toInt())

        override fun writeTimeoutMillis(): Int = 30_000

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private var relayBase: HttpUrl? = "https://relay.example.com/r?key=secret".toHttpUrl()
    private val script = Script()
    private val jar = SimpleCookieJar()
    private val sentCalls = mutableSetOf<Call>()
    private val interceptor = RelayFallbackInterceptor(
        relayProvider = { relayBase },
        cookieJarProvider = { jar },
        relayClientProvider = { FakeRelayFactory(script) },
        requestSent = { call -> call in sentCalls },
    )

    private fun execute(request: Request, markSent: Boolean = false): Response {
        val chain = FakeChain(script, request)
        if (markSent) sentCalls += chain.call
        return interceptor.intercept(chain)
    }

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
        // The direct attempt ran with the short timeouts.
        assertEquals(
            RelayFallbackInterceptor.DIRECT_CONNECT_TIMEOUT_SECONDS * 1000,
            script.connectTimeoutsMs[0],
        )
        assertEquals(
            RelayFallbackInterceptor.DIRECT_READ_TIMEOUT_SECONDS * 1000,
            script.readTimeoutsMs[0],
        )
    }

    @Test
    fun directEmpty406FromFxaHost_fallsBackToRelay() {
        script.respond(406, "")
        script.respond(200, "relay")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("relay", body)
        assertEquals(2, script.requests.size)
        assertEquals("relay.example.com", script.requests[1].url.host)
    }

    @Test
    fun direct406WithJsonBody_isAnApplicationAnswer_notRelayed() {
        // FxA's own errors are JSON; only the edge's EMPTY 406 is a block.
        script.respond(406, """{"errno":101,"error":"bad request"}""")
        val (code, body) = get(fxaUrl)
        assertEquals(406, code)
        assertEquals("""{"errno":101,"error":"bad request"}""", body)
        assertEquals(1, script.requests.size)
    }

    @Test
    fun direct406FromNonFxaHost_isNotRelayed() {
        script.respond(406, "")
        val (code, body) = get(guardianUrl)
        assertEquals(406, code)
        assertEquals("", body)
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

    @Test
    fun connectTimeout_fallsBackToRelay() {
        script.fail(SocketTimeoutException("connect timed out"))
        script.respond(200, "relay")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("relay", body)
        assertEquals(2, script.requests.size)
    }

    @Test
    fun readTimeoutAfterRequestSent_isNeverRelayed() {
        script.fail(SocketTimeoutException("Read timed out"))
        try {
            execute(Request.Builder().url(fxaUrl).get().build(), markSent = true)
            fail("expected the timeout to propagate")
        } catch (e: SocketTimeoutException) {
            assertEquals("Read timed out", e.message)
        }
        assertEquals(1, script.requests.size)
    }

    // ------------------------------------------------------------------
    // Relay failure surfaces the original result
    // ------------------------------------------------------------------

    @Test
    fun relayRefusal_surfacesOriginal406() {
        script.respond(406, "")
        script.respond(200, "", Headers.Builder().set("X-Relay-Error", "nope").build())
        val (code, body) = get(fxaUrl)
        assertEquals(406, code)
        assertEquals("", body)
        assertEquals(2, script.requests.size)
    }

    @Test
    fun relayFailureAfter406_surfacesOriginal406() {
        script.respond(406, "")
        script.fail(ConnectException("relay unreachable"))
        val (code, body) = get(fxaUrl)
        assertEquals(406, code)
        assertEquals("", body)
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
        script.respond(406, "")
        script.respond(500, "relay-error")
        val (code, body) = get(fxaUrl)
        assertEquals(500, code)
        assertEquals("relay-error", body)
        assertEquals(2, script.requests.size)
    }

    // ------------------------------------------------------------------
    // Relay-first stickiness (per host)
    // ------------------------------------------------------------------

    @Test
    fun afterFallback_nextRequestsToSameHostGoThroughRelayFirst() {
        script.respond(406, "")
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
    fun stickinessIsPerHost_otherHostsStillGoDirect() {
        script.respond(406, "")
        script.respond(200, "relay")
        assertEquals(200, get(fxaUrl).first) // FxA host becomes sticky
        script.respond(200, "direct")
        val (code, body) = get(guardianUrl) // Guardian must NOT go relay-first
        assertEquals(200, code)
        assertEquals("direct", body)
        assertEquals(3, script.requests.size)
        assertEquals("vpn.mozilla.org", script.requests[2].url.host)
    }

    @Test
    fun clearStickiness_returnsToDirectFirst() {
        script.respond(406, "")
        script.respond(200, "relay")
        assertEquals(200, get(fxaUrl).first)
        interceptor.clearStickiness()
        script.respond(200, "direct")
        val (code, body) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals("direct", body)
        assertEquals("api.accounts.firefox.com", script.requests[2].url.host)
    }

    @Test
    fun relayFirst_clearsWhenRelayFailsAndDirectAnswers() {
        script.respond(406, "")
        script.respond(200, "relay")
        assertEquals(200, get(fxaUrl).first)
        script.respond(200, "", Headers.Builder().set("X-Relay-Error", "down").build())
        script.respond(200, "direct")
        val second = get(fxaUrl)
        assertEquals(200, second.first)
        assertEquals("direct", second.second)
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
    // Cookies cross the relay against the original URL
    // ------------------------------------------------------------------

    @Test
    fun cookies_areCopiedToTheRelayRequestAndBack() {
        jar.set("sess", "abc", "api.accounts.firefox.com")
        script.respond(406, "")
        script.respond(
            200,
            "relay",
            Headers.Builder().set("Set-Cookie", "uid=xyz; Path=/").build(),
        )
        val (code, _) = get(fxaUrl)
        assertEquals(200, code)
        assertEquals(2, script.requests.size)
        assertEquals("sess=abc", script.requests[1].header("Cookie"))
        val stored = jar.loadForRequest(fxaUrl.toHttpUrl())
        assertTrue(stored.any { it.name == "uid" && it.value == "xyz" })
    }

    // ------------------------------------------------------------------
    // Replay safety
    // ------------------------------------------------------------------

    @Test
    fun oneShotBody_isNeverRelayed() {
        script.fail(ConnectException("connection refused"))
        val oneShot = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("{}")
            }
        }
        val request = Request.Builder().url(fxaUrl).post(oneShot).build()
        try {
            execute(request)
            fail("expected the direct failure to propagate")
        } catch (e: ConnectException) {
            assertEquals("connection refused", e.message)
        }
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
