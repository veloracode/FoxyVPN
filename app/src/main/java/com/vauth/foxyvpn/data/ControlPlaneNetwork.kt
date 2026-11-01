package com.vauth.foxyvpn.data

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Authenticator
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Hosts whose HTTP 406 answer means the edge refused this network, not an
 * application-level answer: the two Firefox Accounts API hosts (see
 * FxaAuthRepository's FXA_AUTH_SERVER / FXA_OAUTH_SERVER constants).
 */
internal val RELAY_406_HOSTS = setOf("api.accounts.firefox.com", "oauth.accounts.firefox.com")

/**
 * Routes control-plane connections through the user's SOCKS5 account proxy
 * while one is configured (SettingsStore.effectiveApiProxy), and delegates
 * to the platform default selector otherwise — so with the feature off the
 * client resolves proxies exactly as it did before. The proxy address is
 * left unresolved: the SOCKS handshake names the proxy as configured.
 */
class ControlPlaneProxySelector(
    private val proxyProvider: () -> SettingsStore.ApiProxySettings?,
) : ProxySelector() {

    private val fallback: ProxySelector? = ProxySelector.getDefault()

    override fun select(uri: URI?): List<Proxy> {
        val settings = proxyProvider()
        if (settings != null) {
            return listOf(
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved(settings.host, settings.port),
                ),
            )
        }
        return fallback?.select(uri) ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        fallback?.connectFailed(uri, sa, ioe)
    }
}

/**
 * SOCKS5 username/password for the account proxy. OkHttp creates SOCKS
 * sockets through the platform (`Socket(proxy)`), whose SOCKS5 handshake
 * asks the process-wide [Authenticator] for credentials; this one answers
 * only for our configured proxy (matched by host and port) and returns
 * null for everything else, so it never leaks the credentials to another
 * proxy the platform may use.
 */
object ControlPlaneSocksAuthenticator : Authenticator() {

    @Volatile
    var proxyProvider: (() -> SettingsStore.ApiProxySettings?)? = null

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.PROXY) return null
        val settings = proxyProvider?.invoke() ?: return null
        if (settings.username.isEmpty()) return null
        val host = requestingHost ?: return null
        if (!host.equals(settings.host, ignoreCase = true)) return null
        if (requestingPort != settings.port) return null
        return PasswordAuthentication(settings.username, settings.password.toCharArray())
    }
}

/**
 * Whether a failed direct attempt may be retried another way because
 * nothing of the HTTP request was ever sent: the failure happened while
 * connecting (refused, unresolvable, timed out) or during the TLS
 * handshake. Mirrors the desktop client's rule of only falling back when
 * no connection was established.
 */
internal fun isConnectPhaseFailure(error: Throwable): Boolean =
    error is ConnectException ||
        error is UnknownHostException ||
        error is InterruptedIOException ||
        error is SSLException

/**
 * Whether a direct answer means "try the relay": the desktop client's
 * HTTP 451 (region refused), and — the deviation our case needs — an
 * HTTP 406 from a Firefox Accounts host, where the edge's bot protection
 * refuses this network outright.
 */
internal fun isRelayTrigger(response: Response, request: Request): Boolean = when (response.code) {
    451 -> true
    406 -> request.url.host in RELAY_406_HOSTS
    else -> false
}

/**
 * The request as the relay receives it: the relay's own URL (which may
 * carry its own query parameters, such as a secret) plus a `url`
 * parameter holding the full target URL; method, headers and body are
 * preserved as they are. This is the desktop client's relay scheme
 * (a Cloudflare Worker or PHP file that forwards and answers with the
 * target's response).
 */
internal fun buildRelayRequest(request: Request, relayBase: HttpUrl): Request {
    val target = relayBase.newBuilder()
        .setQueryParameter("url", request.url.toString())
        .build()
    return request.newBuilder()
        .url(target)
        .removeHeader("Host")
        .build()
}

/**
 * Up to [limitBytes] of a response's body without consuming it, or null
 * when the body is larger (such a response is never replayed through the
 * relay; the original is returned untouched instead).
 */
internal fun peekBodyBytes(response: Response, limitBytes: Long): ByteArray? {
    val body = response.body ?: return ByteArray(0)
    val peeked = body.source().peek()
    if (peeked.request(limitBytes + 1)) return null
    return peeked.buffer.readByteArray()
}

/** A response rebuilt from a peeked body, for when the relay also failed. */
internal fun rebuildResponse(original: Response, request: Request, bodyBytes: ByteArray): Response =
    Response.Builder()
        .request(request)
        .protocol(original.protocol)
        .code(original.code)
        .message(original.message)
        .headers(original.headers)
        .body(bodyBytes.toResponseBody(original.body?.contentType()))
        .sentRequestAtMillis(original.sentRequestAtMillis)
        .receivedResponseAtMillis(original.receivedResponseAtMillis)
        .build()

/**
 * Sends each control-plane request directly, and through the user's own
 * API relay when the direct way cannot work — the desktop client's
 * relayFallback, adapted to OkHttp:
 *
 * - The direct attempt gets a short connect timeout ([DIRECT_CONNECT_TIMEOUT_SECONDS])
 *   so a blackholed route falls back quickly instead of waiting out the
 *   client's normal connect timeout. (OkHttp bounds only the TCP connect
 *   phase this way; a stalled TLS handshake still falls back, after the
 *   normal read timeout, via [isConnectPhaseFailure].)
 * - Fallback happens only when nothing was sent (a connect-phase
 *   transport failure) or when the direct answer is a relay trigger
 *   (451, or 406 from a Firefox Accounts host).
 * - After a fallback, requests go through the relay first for
 *   [RELAY_FIRST_MS] (or until the network changes), so only the first
 *   request pays the direct attempt; then directly again. A relay that
 *   fails falls back to the direct way the same way.
 * - Exactly one relay attempt per request. When the relay fails, the
 *   original result (response or error) is what the caller sees.
 * - Requests with a one-shot body are never replayed.
 *
 * With no relay configured the interceptor is a single plain proceed —
 * the pre-existing behavior, byte for byte.
 */
class RelayFallbackInterceptor(
    private val relayProvider: () -> HttpUrl?,
    private val networkKeyProvider: () -> String? = { null },
    private val nowMs: () -> Long = System::currentTimeMillis,
) : Interceptor {

    @Volatile
    private var relayFirstUntilMs: Long = 0L

    @Volatile
    private var relayFirstNetworkKey: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val relayBase = relayProvider() ?: return chain.proceed(request)
        val body = request.body
        if (body != null && body.isOneShot()) return chain.proceed(request)
        return if (isRelayFirst()) {
            relayFirst(chain, request, relayBase)
        } else {
            directFirst(chain, request, relayBase)
        }
    }

    private fun directFirst(chain: Interceptor.Chain, request: Request, relayBase: HttpUrl): Response {
        var originalError: IOException? = null
        var originalResponse: Response? = null
        try {
            val response = chain
                .withConnectTimeout(DIRECT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .proceed(request)
            if (!isRelayTrigger(response, request)) return response
            val peeked = peekBodyBytes(response, TRIGGER_BODY_LIMIT_BYTES)
            if (peeked == null) return response
            originalResponse = rebuildResponse(response, request, peeked)
            response.close()
        } catch (e: IOException) {
            if (!isConnectPhaseFailure(e)) throw e
            originalError = e
        }
        markRelayFirst()
        return when (val outcome = tryRelay(chain, request, relayBase)) {
            is RelayResult.Success -> outcome.response
            is RelayResult.Failure -> {
                AppLogger.w(TAG, "API relay ${relayBase.host} failed; surfacing the direct result", outcome.error)
                originalResponse ?: throw (originalError ?: outcome.error)
            }
        }
    }

    private fun relayFirst(chain: Interceptor.Chain, request: Request, relayBase: HttpUrl): Response {
        when (val outcome = tryRelay(chain, request, relayBase)) {
            is RelayResult.Success -> return outcome.response
            is RelayResult.Failure ->
                AppLogger.w(TAG, "API relay ${relayBase.host} failed; trying directly", outcome.error)
        }
        clearRelayFirst()
        val response = chain.proceed(request)
        if (!isRelayTrigger(response, request)) return response
        val peeked = peekBodyBytes(response, TRIGGER_BODY_LIMIT_BYTES) ?: return response
        val rebuilt = rebuildResponse(response, request, peeked)
        response.close()
        return rebuilt
    }

    private fun tryRelay(chain: Interceptor.Chain, request: Request, relayBase: HttpUrl): RelayResult {
        val response = try {
            chain.proceed(buildRelayRequest(request, relayBase))
        } catch (e: IOException) {
            return RelayResult.Failure(e)
        }
        val refusal = response.header(RELAY_ERROR_HEADER)
        if (refusal != null) {
            response.close()
            return RelayResult.Failure(
                IOException("API relay ${relayBase.host} refused ${request.url.host}: $refusal"),
            )
        }
        return RelayResult.Success(response)
    }

    private fun isRelayFirst(): Boolean {
        val until = relayFirstUntilMs
        if (until == 0L) return false
        if (nowMs() > until) {
            clearRelayFirst()
            return false
        }
        val key = relayFirstNetworkKey
        val current = networkKeyProvider()
        if (key != null && current != null && key != current) {
            clearRelayFirst()
            return false
        }
        return true
    }

    private fun markRelayFirst() {
        relayFirstNetworkKey = networkKeyProvider()
        relayFirstUntilMs = nowMs() + RELAY_FIRST_MS
    }

    private fun clearRelayFirst() {
        relayFirstUntilMs = 0L
        relayFirstNetworkKey = null
    }

    private sealed interface RelayResult {
        class Success(val response: Response) : RelayResult
        class Failure(val error: IOException) : RelayResult
    }

    companion object {
        private const val TAG = "RelayFallback"

        /** How long the direct way gets to connect before the relay is tried. */
        const val DIRECT_CONNECT_TIMEOUT_SECONDS = 4

        /** After a fallback, requests go through the relay first for this long. */
        const val RELAY_FIRST_MS = 10 * 60 * 1000L

        /** A relay that refuses a request answers with this header. */
        const val RELAY_ERROR_HEADER = "X-Relay-Error"

        /** Trigger responses are only replayed when their body fits this. */
        const val TRIGGER_BODY_LIMIT_BYTES = 64 * 1024L
    }
}
