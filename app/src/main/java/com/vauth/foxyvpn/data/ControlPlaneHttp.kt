package com.vauth.foxyvpn.data

import android.net.Network
import android.webkit.CookieManager
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

fun Request.Builder.applyFirefoxHeaders(): Request.Builder = FirefoxAccountHeaders.apply(this)

class SimpleCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies
            .filter { it.matches(url) }
            .sortedByDescending { it.domain.length }
            .distinctBy { it.name }
    }

    @Synchronized fun snapshotAll(): List<Cookie> = cookies.toList()

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    @Synchronized
    fun syncFromWebView(
        urls: List<String> =
            listOf(
                "https://accounts.firefox.com",
                "https://api.accounts.firefox.com",
                "https://oauth.accounts.firefox.com",
                "https://vpn.mozilla.org",
            ),
    ) {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        for (urlStr in urls) {
            val httpUrl = urlStr.toHttpUrlOrNull() ?: continue
            val cookieHeader = runCatching { manager.getCookie(urlStr) }.getOrNull() ?: continue
            val parsedCookies = mutableListOf<Cookie>()
            for (raw in cookieHeader.split(";")) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) continue
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) continue
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isEmpty() || value.isEmpty()) continue

                val hostCookie =
                    runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(httpUrl.host)
                            .path("/")
                            .apply { if (httpUrl.isHttps) secure() }
                            .build()
                    }.getOrNull()
                if (hostCookie != null) parsedCookies.add(hostCookie)

                if (httpUrl.host.endsWith("firefox.com") && httpUrl.host != "firefox.com") {
                    val domainCookie =
                        runCatching {
                            Cookie.Builder()
                                .name(name)
                                .value(value)
                                .domain("firefox.com")
                                .path("/")
                                .apply { if (httpUrl.isHttps) secure() }
                                .build()
                        }.getOrNull()
                    if (domainCookie != null) parsedCookies.add(domainCookie)
                    for (target in listOf("api.accounts.firefox.com", "oauth.accounts.firefox.com", "vpn.mozilla.org")) {
                        val targetCookie =
                            runCatching {
                                Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(target)
                                    .path("/")
                                    .apply { if (httpUrl.isHttps) secure() }
                                    .build()
                            }.getOrNull()
                        if (targetCookie != null) parsedCookies.add(targetCookie)
                    }
                }
            }
            if (parsedCookies.isNotEmpty()) {
                saveFromResponse(httpUrl, parsedCookies)
            }
        }
    }

    @Synchronized
    fun syncToWebView(cookies: List<Cookie>) {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        for (cookie in cookies) {
            val host = cookie.domain.trimStart('.')
            if (host.isEmpty()) continue
            val urlStr = "https://$host"
            val cookieString = StringBuilder().apply {
                append(cookie.name).append("=").append(cookie.value)
                append("; Domain=").append(cookie.domain)
                append("; Path=").append(cookie.path)
                if (cookie.secure) append("; Secure")
                if (cookie.httpOnly) append("; HttpOnly")
            }.toString()
            runCatching { manager.setCookie(urlStr, cookieString) }
        }
        runCatching { manager.flush() }
    }
}

/**
 * A control-plane socket. With an account proxy configured, the
 * connection is made to the SOCKS5 proxy instead and the handshake
 * (inside this socket, so the factory's protect() applies to the proxy
 * connection too) names the origin host — OkHttp's built-in SOCKS
 * support cannot be used, as it creates the proxy socket itself and
 * bypasses this socket factory entirely (no protect). Without a proxy
 * this is the plain platform connect, unchanged.
 */
internal class ControlPlaneSocket(
    private val proxyProvider: () -> SettingsStore.ApiProxySettings? = { null },
) : Socket() {

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val proxy = proxyProvider()
        if (proxy == null || endpoint !is InetSocketAddress) {
            super.connect(endpoint, timeout)
            return
        }
        val originHost = endpoint.hostString
        if (originHost.isNullOrEmpty() || originHost == "0.0.0.0") {
            throw java.net.UnknownHostException("no origin host name for the proxied connection")
        }
        val proxyAddress = ProxyHostResolver.resolve(proxy.host)
        super.connect(InetSocketAddress(proxyAddress, proxy.port), timeout)
        val previousTimeout = soTimeout
        try {
            if (timeout > 0) soTimeout = timeout
            socks5Handshake(
                super.getInputStream(),
                super.getOutputStream(),
                originHost,
                endpoint.port,
                proxy,
            )
        } catch (e: java.io.IOException) {
            runCatching { close() }
            throw e
        } finally {
            runCatching { soTimeout = previousTimeout }
        }
    }
}

// Internal (not private) so unit tests and the proxy-host resolver can
// build clients on it; no behavior depends on the visibility.
internal class ProtectingSocketFactory(
    private val useApiProxy: Boolean = true,
) : SocketFactory() {

    private fun createProtectedSocket(
        localAddress: InetSocketAddress? = null,
        remoteAddress: InetSocketAddress? = null,
    ): Socket {
        val socket = ControlPlaneSocket(
            proxyProvider = { if (useApiProxy) ControlPlaneHttp.apiProxyProvider?.invoke() else null },
        )
        try {
            socket.bind(localAddress ?: InetSocketAddress(0))
            val protector = ControlPlaneHttp.socketProtector
            if (protector != null && !protector(socket)) {
                throw java.io.IOException("VPN refused control-plane socket protection")
            }
            ControlPlaneHttp.underlyingNetwork()?.bindSocket(socket)
            if (remoteAddress != null) socket.connect(remoteAddress)
            return socket
        } catch (error: Exception) {
            runCatching { socket.close() }.onFailure { error.addSuppressed(it) }
            throw error
        }
    }

    override fun createSocket(): Socket = createProtectedSocket()

    override fun createSocket(host: String, port: Int): Socket =
        createProtectedSocket(remoteAddress = InetSocketAddress(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localAddr: InetAddress,
        localPort: Int,
    ): Socket =
        createProtectedSocket(
            InetSocketAddress(localAddr, localPort),
            InetSocketAddress(host, port),
        )

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createProtectedSocket(remoteAddress = InetSocketAddress(host, port))

    override fun createSocket(
        host: InetAddress,
        port: Int,
        localAddr: InetAddress,
        localPort: Int,
    ): Socket =
        createProtectedSocket(
            InetSocketAddress(localAddr, localPort),
            InetSocketAddress(host, port),
        )
}

object ControlPlaneHttp {
    val cookieJar = SimpleCookieJar()

    @Volatile var socketProtector: ((Socket) -> Boolean)? = null

    @Volatile var networkProvider: (() -> Network?)? = null

    /** The configured account (control-plane) SOCKS5 proxy, or null. Set by the app. */
    @Volatile var apiProxyProvider: (() -> SettingsStore.ApiProxySettings?)? = null

    /** The configured API relay base URL (normalized https), or null. Set by the app. */
    @Volatile var apiRelayProvider: (() -> String?)? = null

    internal fun underlyingNetwork(): Network? {
        val provider = networkProvider ?: return null
        return provider() ?: throw java.io.IOException("No underlying network is available")
    }

    /** Upstream's resolver: the underlying physical network, SYSTEM fallback. */
    private val networkDns =
        object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val network = runCatching { underlyingNetwork() }.getOrNull()
                val fromNetwork =
                    if (network != null) {
                        runCatching { network.getAllByName(hostname).toList() }.getOrNull()
                    } else {
                        null
                    }
                return if (!fromNetwork.isNullOrEmpty()) {
                    fromNetwork
                } else {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                }
            }
        }

    private val controlPlaneDns = ControlPlaneDns({ apiProxyProvider?.invoke() }, networkDns)

    /** Records whether each call's request was fully sent (relay guard). */
    val requestSentTracker = RequestSentTracker()

    private val relayInterceptor = RelayFallbackInterceptor(
        relayProvider = {
            apiRelayProvider?.invoke()?.let { url ->
                runCatching { url.toHttpUrl() }.getOrNull()
            }
        },
        cookieJarProvider = { cookieJar },
        relayClientProvider = { relayClient },
        requestSent = { call -> requestSentTracker.wasRequestSent(call) },
        nowMs = { android.os.SystemClock.elapsedRealtime() },
    )

    /** Called when the (non-VPN) network changes: relay-first state is dropped. */
    fun onNetworkChanged() {
        relayInterceptor.clearStickiness()
    }

    /**
     * Called when a control-plane network setting changes: relay-first
     * state is dropped, cached proxy addresses are forgotten, and pooled
     * connections (which predate the change) are evicted.
     */
    fun onSettingsChanged() {
        relayInterceptor.clearStickiness()
        ProxyHostResolver.clearCache()
        if (clientOrNull != null) clientOrNull!!.connectionPool.evictAll()
    }

    @Volatile private var clientOrNull: OkHttpClient? = null

    val client: OkHttpClient
        get() = clientOrNull ?: synchronized(this) {
            clientOrNull ?: buildClient().also { clientOrNull = it }
        }

    private fun buildClient(): OkHttpClient =
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .cookieJar(cookieJar)
            .socketFactory(ProtectingSocketFactory())
            .dns(controlPlaneDns)
            .eventListener(requestSentTracker)
            .addInterceptor(relayInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * The client the relay hop itself runs on: the shared client minus
     * the relay interceptor (no recursion), with no cookie jar and no
     * redirect following — cookies are copied by hand against the
     * original URL by the interceptor, and a relayed redirect is the
     * caller's to see. Socket factory, DNS, pool and dispatcher are
     * shared, so the relay hop gets the same protect() / SOCKS
     * treatment as a direct connection.
     */
    val relayClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { interceptors().remove(relayInterceptor) }
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}
