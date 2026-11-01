package com.vauth.foxyvpn.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.vauth.foxyvpn.vpn.upstream.TlsClientHelloSplitter
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

private const val TAG = "ControlPlaneHttp"

const val MOZILLA_VPN_USER_AGENT = "MozillaVPN/2.35.0 (sys:android 14; iap:true)"

fun Request.Builder.applyMozillaVpnHeaders(): Request.Builder {
    header("User-Agent", MOZILLA_VPN_USER_AGENT)
    header("Accept", "application/json")
    return this
}

const val FIREFOX_ANDROID_USER_AGENT =
    "Mozilla/5.0 (Android 14; Mobile; rv:133.0) Gecko/133.0 Firefox/133.0"

/**
 * Headers for Firefox Accounts calls. The FxA edge's bot protection
 * rejects the MozillaVPN client User-Agent with an empty 406 (verified
 * live: the same /oauth/token request returns 400 with a Firefox
 * User-Agent), which forced every FxA call into the JS challenge solver.
 * Guardian and the server list keep the VPN-client identity above; only
 * FxA calls present as Firefox.
 */
fun Request.Builder.applyFxaHeaders(): Request.Builder {
    header("User-Agent", FIREFOX_ANDROID_USER_AGENT)
    header("Accept", "application/json")
    return this
}

class SimpleCookieJar : CookieJar {
    private val byDomain = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val bucket = byDomain.getOrPut(cookie.domain) { mutableListOf() }
            synchronized(bucket) {
                bucket.removeAll { it.name == cookie.name }
                bucket.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        for ((domain, cookies) in byDomain) {
            if (url.host == domain || url.host.endsWith(".$domain")) {
                synchronized(cookies) {
                    result.addAll(cookies.filter { it.expiresAt > now })
                }
            }
        }
        return result
    }

    fun set(name: String, value: String, domain: String) {
        val bucket = byDomain.getOrPut(domain) { mutableListOf() }
        synchronized(bucket) {
            bucket.removeAll { it.name == name }
            bucket.add(
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .build(),
            )
        }
    }

    fun snapshotAll(): List<Cookie> = byDomain.values.flatten()
}

// Internal (not private) so unit tests can drive the stream directly;
// visibility only — no behavior depends on it.
internal class SplitClientHelloOutputStream(
    out: OutputStream,
    private val modeProvider: () -> SettingsStore.TlsSplitMode,
    private val onSplit: () -> Unit = {},
) : FilterOutputStream(out) {
    private var splitDone = false

    override fun write(b: ByteArray, off: Int, len: Int) {
        val mode = modeProvider()
        if (splitDone || mode == SettingsStore.TlsSplitMode.OFF) {
            out.write(b, off, len)
            return
        }

        if (!TlsClientHelloSplitter.looksLikeClientHello(b, off, len)) {
            out.write(b, off, len)
            return
        }

        splitDone = true
        val segments = try {
            TlsClientHelloSplitter.splitBytes(b, off, len, mode)
        } catch (e: Throwable) {
            null
        }

        if (segments.isNullOrEmpty()) {
            out.write(b, off, len)
            return
        }

        onSplit()
        for (seg in segments) {
            out.write(seg)
            out.flush()
        }
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }
}

// Internal (not private) so unit tests can drive the socket directly;
// visibility only — no behavior depends on it.
internal class SplitClientHelloSocket(
    private val modeProvider: () -> SettingsStore.TlsSplitMode,
) : Socket() {
    // One wrapper per socket, as the split state must be shared by every
    // getOutputStream() call (previously the state lived on the socket).
    private var stream: OutputStream? = null

    override fun getOutputStream(): OutputStream =
        stream ?: SplitClientHelloOutputStream(super.getOutputStream(), modeProvider) {
            runCatching { tcpNoDelay = true }
        }.also { stream = it }
}

private class ProtectingSocketFactory : SocketFactory() {

    private fun createRawSocket(): Socket =
        SplitClientHelloSocket { ControlPlaneHttp.tlsSplitModeProvider?.invoke() ?: SettingsStore.TlsSplitMode.OFF }

    private fun protect(socket: Socket, localAddress: InetSocketAddress?): Socket {
        runCatching {
            if (!socket.isBound) socket.bind(localAddress ?: InetSocketAddress(0))
        }.onFailure {
            AppLogger.w(TAG, "could not bind a control-plane socket before protecting it", it)
        }
        val protector = ControlPlaneHttp.socketProtector
        if (protector != null) {
            val protectedOk = runCatching { protector.invoke(socket) }.getOrDefault(false)
            if (!protectedOk) {
                AppLogger.w(
                    TAG,
                    "VpnService.protect() refused a control-plane socket; this request would be routed " +
                        "back into the tunnel instead of out to the network",
                )
            }
        }
        return socket
    }

    override fun createSocket(): Socket = protect(createRawSocket(), null)

    override fun createSocket(host: String, port: Int): Socket =
        protect(createRawSocket(), null).apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localAddr: InetAddress, localPort: Int): Socket =
        protect(createRawSocket(), InetSocketAddress(localAddr, localPort))
            .apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        protect(createRawSocket(), null).apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: InetAddress, port: Int, localAddr: InetAddress, localPort: Int): Socket =
        protect(createRawSocket(), InetSocketAddress(localAddr, localPort))
            .apply { connect(InetSocketAddress(host, port)) }
}

object ControlPlaneHttp {
    val cookieJar = SimpleCookieJar()

    @Volatile
    var socketProtector: ((Socket) -> Boolean)? = null

    @Volatile
    var tlsSplitModeProvider: (() -> SettingsStore.TlsSplitMode)? = null

    /** The configured account (control-plane) SOCKS5 proxy, or null. Set by the app. */
    @Volatile
    var apiProxyProvider: (() -> SettingsStore.ApiProxySettings?)? = null

    /** The configured API relay base URL (normalized https), or null. Set by the app. */
    @Volatile
    var apiRelayProvider: (() -> String?)? = null

    /** Identity of the current network, for the relay's relay-first window. Set by the app. */
    @Volatile
    var networkKeyProvider: (() -> String?)? = null

    private val proxySelector = ControlPlaneProxySelector {
        apiProxyProvider?.invoke()
    }

    private val relayInterceptor = RelayFallbackInterceptor(
        relayProvider = {
            apiRelayProvider?.invoke()?.let { url ->
                runCatching { url.toHttpUrl() }.getOrNull()
            }
        },
        networkKeyProvider = { networkKeyProvider?.invoke() },
    )

    @Volatile
    private var socksAuthenticatorInstalled = false

    /**
     * Installs the SOCKS5 credential provider as the process-wide
     * [java.net.Authenticator]. It answers only for the configured
     * account proxy and returns null for everything else.
     */
    fun installSocksAuthenticator() {
        if (socksAuthenticatorInstalled) return
        synchronized(this) {
            if (socksAuthenticatorInstalled) return
            ControlPlaneSocksAuthenticator.proxyProvider = { apiProxyProvider?.invoke() }
            runCatching { java.net.Authenticator.setDefault(ControlPlaneSocksAuthenticator) }
            socksAuthenticatorInstalled = true
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .socketFactory(ProtectingSocketFactory())
            .proxySelector(proxySelector)
            .addInterceptor(relayInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
