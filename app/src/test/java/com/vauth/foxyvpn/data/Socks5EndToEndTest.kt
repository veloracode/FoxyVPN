package com.vauth.foxyvpn.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The control-plane SOCKS5 proxy, end to end over loopback: an OkHttp
 * client built exactly like ControlPlaneHttp's (protecting socket
 * factory + placeholder DNS) talks through FakeSocks5Server to a fake
 * origin. Verifies the handshake happens INSIDE our socket (the origin
 * is named by domain, credentials are offered and checked), and that
 * with the proxy off the connection is a plain direct one. CI-gated
 * like the other loopback suites.
 */
class Socks5EndToEndTest {

    private fun clientFor(settings: SettingsStore.ApiProxySettings?): OkHttpClient =
        OkHttpClient.Builder()
            .socketFactory(
                ProtectingSocketFactory(useApiProxy = true),
            )
            .dns(ControlPlaneDns { settings })
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    private fun withProxyProvider(settings: SettingsStore.ApiProxySettings?, block: () -> Unit) {
        val previous = ControlPlaneHttp.apiProxyProvider
        ControlPlaneHttp.apiProxyProvider = { settings }
        try {
            block()
        } finally {
            ControlPlaneHttp.apiProxyProvider = previous
        }
    }

    @Test
    fun noAuth_connectNamesTheOriginByDomain() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, "via-proxy") }.use { origin ->
            FakeSocks5Server(originPort = origin.port).use { socks ->
                val settings = SettingsStore.ApiProxySettings("127.0.0.1", socks.port, "", "")
                withProxyProvider(settings) {
                    val client = clientFor(settings)
                    val url = "http://origin.test:${origin.port}/hello"
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("via-proxy", resp.body?.string())
                    }
                }
                assertEquals(1, socks.connectRequests.size)
                val connect = socks.connectRequests[0]
                assertEquals(3, connect.atyp) // domain name, proxy resolves
                assertEquals("origin.test", connect.host)
                assertEquals(origin.port, connect.port)
                assertEquals(listOf(0), socks.offeredMethods[0])
            }
        }
    }

    @Test
    fun userPassAuth_credentialsAreSentAndAccepted() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, "authed") }.use { origin ->
            FakeSocks5Server(originPort = origin.port, requiredAuth = "user" to "pass").use { socks ->
                val settings = SettingsStore.ApiProxySettings("127.0.0.1", socks.port, "user", "pass")
                withProxyProvider(settings) {
                    val client = clientFor(settings)
                    val url = "http://origin.test:${origin.port}/hello"
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("authed", resp.body?.string())
                    }
                }
                assertEquals(listOf("user" to "pass"), socks.authentications.toList())
                assertTrue(socks.offeredMethods[0].contains(2))
            }
        }
    }

    @Test
    fun wrongPassword_surfacesAsConnectionError() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, "never") }.use { origin ->
            FakeSocks5Server(originPort = origin.port, requiredAuth = "user" to "pass").use { socks ->
                val settings = SettingsStore.ApiProxySettings("127.0.0.1", socks.port, "user", "wrong")
                withProxyProvider(settings) {
                    val client = clientFor(settings)
                    val url = "http://origin.test:${origin.port}/hello"
                    try {
                        client.newCall(Request.Builder().url(url).get().build()).execute()
                        fail("expected the failed handshake to surface as an IOException")
                    } catch (expected: IOException) {
                        // A normal connection error, not a crash or a hang.
                    }
                }
                assertEquals(0, origin.requests.size)
            }
        }
    }

    @Test
    fun proxyOff_connectsDirectlyAndNeverTouchesTheProxy() {
        FakeHttpServer { FakeHttpServer.FakeResponse(200, "direct") }.use { origin ->
            FakeSocks5Server(originPort = origin.port).use { socks ->
                withProxyProvider(null) {
                    val client = clientFor(null)
                    val url = "http://127.0.0.1:${origin.port}/hello"
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("direct", resp.body?.string())
                    }
                }
                assertEquals(0, socks.connections.get())
                assertEquals(1, origin.requests.size)
            }
        }
    }
}
