package com.vauth.foxyvpn.data

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end: RelayFallbackInterceptor against local fake servers: the "Mozilla" side
 * is a plain-HTTP loopback server reached through a Dns override under the
 * real FxA/Guardian host names (the interceptor keys its 406 rule off the
 * host), the relay is a second loopback server. The interceptor logic is
 * also covered without sockets by RelayFallbackInterceptorTest; this class
 * adds the real OkHttp transport on top. Needs ordinary loopback
 * networking (CI); some sandboxes block JVM loopback entirely.
 */
class RelayEndToEndTest {

    private val fxaHost = "api.accounts.firefox.com"
    private val guardianHost = "vpn.mozilla.org"

    private val loopbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = when (hostname) {
            fxaHost, "oauth.accounts.firefox.com", guardianHost ->
                listOf(InetAddress.getByName("127.0.0.1"))
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }

    private var relayBase: HttpUrl? = null

    private fun interceptor() = RelayFallbackInterceptor(relayProvider = { relayBase })

    private fun client(interceptor: RelayFallbackInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .dns(loopbackDns)
            .addInterceptor(interceptor)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    private fun closedPort(): Int {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = socket.localPort
        socket.close()
        return port
    }

    private fun get(client: OkHttpClient, url: String): Pair<Int, String> =
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            resp.code to (resp.body?.string().orEmpty())
        }

    // ------------------------------------------------------------------
    // Off / unchanged behavior
    // ------------------------------------------------------------------

    @Test
    fun relayOff_requestPassesThroughOnce() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, """{"ok":true}""") }.use { direct ->
            relayBase = null
            val client = client(interceptor())
            val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/test")
            assertEquals(200, code)
            assertEquals("""{"ok":true}""", body)
            assertEquals(1, direct.requests.size)
        }
    }

    @Test
    fun directSuccess_relayNeverTouched() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, "direct") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/test")
                assertEquals(200, code)
                assertEquals("direct", body)
                assertEquals(0, relay.requests.size)
            }
        }
    }

    // ------------------------------------------------------------------
    // Fallback triggers
    // ------------------------------------------------------------------

    @Test
    fun directConnectRefused_fallsBackToRelay_preservingEverything() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, """{"via":"relay"}""") }.use { relay ->
            relayBase = "http://127.0.0.1:${relay.port}/relay?key=secret".toHttpUrl()
            val client = client(interceptor())
            val targetUrl = "http://$fxaHost:${closedPort()}/v1/oauth/token?grant=1"
            val request = Request.Builder()
                .url(targetUrl)
                .header("Authorization", "Bearer abc")
                .post("""{"code":"xyz"}""".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                assertEquals(200, resp.code)
                assertEquals("""{"via":"relay"}""", resp.body?.string())
            }
            assertEquals(1, relay.requests.size)
            val relayed = relay.requests[0]
            assertEquals("POST", relayed.method)
            assertEquals("""{"code":"xyz"}""", relayed.body.toString(Charsets.UTF_8))
            assertEquals("Bearer abc", relayed.header("authorization"))
            val relayUrl = "http://relay${relayed.target}".toHttpUrl()
            assertEquals("secret", relayUrl.queryParameter("key"))
            assertEquals(targetUrl, relayUrl.queryParameter("url"))
        }
    }

    @Test
    fun direct406FromFxaHost_fallsBackToRelay() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/account/login")
                assertEquals(200, code)
                assertEquals("relay", body)
                assertEquals(1, direct.requests.size)
                assertEquals(1, relay.requests.size)
            }
        }
    }

    @Test
    fun direct406FromNonFxaHost_isNotRelayed() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$guardianHost:${direct.port}/api/v2/vpn/login")
                assertEquals(406, code)
                assertEquals("blocked", body)
                assertEquals(0, relay.requests.size)
            }
        }
    }

    @Test
    fun direct451_fallsBackToRelay() {
        FakeHttpServer { FakeHttpServer.FakeResponse(451, "region") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$guardianHost:${direct.port}/api/v2/vpn/login")
                assertEquals(200, code)
                assertEquals("relay", body)
                assertEquals(1, relay.requests.size)
            }
        }
    }

    // ------------------------------------------------------------------
    // Relay failure surfaces the original result
    // ------------------------------------------------------------------

    @Test
    fun relayRefusal_surfacesOriginalResponseWithBody() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, """{"error":"fastly"}""") }.use { direct ->
            FakeHttpServer {
                FakeHttpServer.FakeResponse(200, "", mapOf("X-Relay-Error" to "nope"))
            }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/oauth/token")
                assertEquals(406, code)
                assertEquals("""{"error":"fastly"}""", body)
                assertEquals(1, relay.requests.size)
            }
        }
    }

    @Test
    fun relayUnreachable_surfacesOriginalResponse() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked-body") }.use { direct ->
            relayBase = "http://127.0.0.1:${closedPort()}/relay".toHttpUrl()
            val client = client(interceptor())
            val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/oauth/token")
            assertEquals(406, code)
            assertEquals("blocked-body", body)
        }
    }

    @Test
    fun relayUnreachableAfterConnectFailure_surfacesOriginalError() {
        relayBase = "http://127.0.0.1:${closedPort()}/relay".toHttpUrl()
        val client = client(interceptor())
        try {
            get(client, "http://$fxaHost:${closedPort()}/v1/oauth/token")
            fail("expected the direct connection error to surface")
        } catch (e: IOException) {
            assertTrue("expected a connect-phase error, got $e", e is ConnectException)
        }
    }

    @Test
    fun relayAnswer_isFinal_exactlyOneRelayAttempt() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(500, "relay-error") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val (code, body) = get(client, "http://$fxaHost:${direct.port}/v1/oauth/token")
                assertEquals(500, code)
                assertEquals("relay-error", body)
                assertEquals(1, relay.requests.size)
                assertEquals(1, direct.requests.size)
            }
        }
    }

    // ------------------------------------------------------------------
    // Relay-first window
    // ------------------------------------------------------------------

    @Test
    fun afterFallback_nextRequestsGoThroughRelayFirst() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val url = "http://$fxaHost:${direct.port}/v1/oauth/token"
                assertEquals(200, get(client, url).first)
                assertEquals(1, direct.requests.size)
                assertEquals(1, relay.requests.size)
                // Second request: relay first, direct untouched.
                assertEquals(200, get(client, url).first)
                assertEquals(1, direct.requests.size)
                assertEquals(2, relay.requests.size)
            }
        }
    }

    @Test
    fun relayFirstWindow_clearsWhenRelayFailsAndDirectAnswers() {
        val directResponse = AtomicReference(FakeHttpServer.FakeResponse(406, "blocked"))
        val relayResponse = AtomicReference(FakeHttpServer.FakeResponse(200, "relay"))
        FakeHttpServer { directResponse.get() }.use { direct ->
            FakeHttpServer { relayResponse.get() }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val url = "http://$fxaHost:${direct.port}/v1/oauth/token"
                assertEquals(200, get(client, url).first) // fallback; window opens
                // Relay now refuses; direct now answers: relay-first attempt
                // fails, direct answers, window closes.
                relayResponse.set(FakeHttpServer.FakeResponse(200, "", mapOf("X-Relay-Error" to "down")))
                directResponse.set(FakeHttpServer.FakeResponse(200, "direct"))
                val second = get(client, url)
                assertEquals(200, second.first)
                assertEquals("direct", second.second)
                // Third request goes directly again.
                val directHitsBefore = direct.requests.size
                assertEquals(200, get(client, url).first)
                assertEquals(directHitsBefore + 1, direct.requests.size)
            }
        }
    }

    // ------------------------------------------------------------------
    // Replay safety
    // ------------------------------------------------------------------

    @Test
    fun oneShotBody_isNeverRelayed() {
        FakeHttpServer { FakeHttpServer.FakeResponse(406, "blocked") }.use { direct ->
            FakeHttpServer { FakeHttpServer.FakeResponse(200, "relay") }.use { relay ->
                relayBase = "http://127.0.0.1:${relay.port}/relay".toHttpUrl()
                val client = client(interceptor())
                val oneShot = object : RequestBody() {
                    override fun contentType() = "application/json".toMediaType()
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) {
                        sink.writeUtf8("{}")
                    }
                }
                val request = Request.Builder()
                    .url("http://$fxaHost:${direct.port}/v1/oauth/token")
                    .post(oneShot)
                    .build()
                client.newCall(request).execute().use { resp ->
                    assertEquals(406, resp.code)
                }
                assertEquals(0, relay.requests.size)
            }
        }
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
