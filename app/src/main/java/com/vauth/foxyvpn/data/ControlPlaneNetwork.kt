package com.vauth.foxyvpn.data

import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException

/**
 * Hosts whose empty HTTP 406 answer means the edge refused this network,
 * not an application-level answer: the two Firefox Accounts API hosts
 * (see FxaAuthRepository's FXA_AUTH_SERVER / FXA_OAUTH_SERVER constants).
 * FxA's own errors are JSON bodies with an errno; the edge block is an
 * empty 406 from inside a completed TLS session, i.e. the origin never
 * executed the request and replaying it through the relay is safe.
 */
internal val RELAY_406_HOSTS = setOf("api.accounts.firefox.com", "oauth.accounts.firefox.com")

/**
 * DNS for control-plane clients.
 *
 * With no account proxy configured this is the platform resolver,
 * exactly as before. While a proxy is configured it returns a single
 * placeholder address that CARRIES the origin hostname: the real
 * connection is made to the SOCKS5 proxy inside the socket (see
 * SplitClientHelloSocket.connect), which names the origin in the
 * handshake (ATYP=domain, so the proxy resolves it — poisoned local DNS
 * is never consulted for Mozilla hosts), while OkHttp still uses the
 * real hostname for SNI and certificate verification. The placeholder
 * address itself (0.0.0.0) is never dialed.
 */
class ControlPlaneDns(
    private val proxyProvider: () -> SettingsStore.ApiProxySettings?,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (proxyProvider() != null) {
            return listOf(InetAddress.getByAddress(hostname, PLACEHOLDER))
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private companion object {
        val PLACEHOLDER = byteArrayOf(0, 0, 0, 0)
    }
}

/**
 * Resolves the account proxy's own address. The proxy host is never
 * resolved through system DNS (it may be poisoned, and the answer must
 * not depend on the tunnel state): IP literals and "localhost" are used
 * as-is; anything else goes through DNS-over-HTTPS against an
 * IP-literal endpoint on a bare, protected client. Results are cached
 * for the process lifetime (cleared when the settings change).
 */
internal object ProxyHostResolver {

    private val cache = ConcurrentHashMap<String, InetAddress>()

    /** Injectable for tests; the default performs the DoH lookup. */
    @Volatile
    var dohLookup: (String) -> InetAddress? = { host -> queryDoh(host) }

    fun resolve(host: String): InetAddress {
        literalOrLocalhost(host)?.let { return it }
        cache[host]?.let { return it }
        val resolved = dohLookup(host)
            ?: throw UnknownHostException("could not resolve proxy host $host")
        cache[host] = resolved
        return resolved
    }

    fun clearCache() {
        cache.clear()
    }

    internal fun literalOrLocalhost(host: String): InetAddress? {
        if (host.equals("localhost", ignoreCase = true)) {
            return InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        }
        val parts = host.split('.')
        if (parts.size == 4) {
            val bytes = ByteArray(4)
            for (i in 0..3) {
                val part = parts[i]
                val value = part.toIntOrNull() ?: return null
                if (part.isEmpty() || part.length > 3 || value !in 0..255) return null
                bytes[i] = value.toByte()
            }
            return InetAddress.getByAddress(bytes)
        }
        return null
    }

    private fun queryDoh(host: String): InetAddress? {
        // A bare client: protected sockets, but no proxy, no relay and
        // no placeholder DNS (the endpoint is an IP literal) — resolving
        // the proxy's address must never recurse into the proxy itself.
        val client = OkHttpClient.Builder()
            .socketFactory(ProtectingSocketFactory(useApiProxy = false))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        for (endpoint in DOH_ENDPOINTS) {
            val address = runCatching {
                val url = "$endpoint?name=$host&type=A"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string() ?: return@use null)
                    val answers = json.optJSONArray("Answer") ?: return@use null
                    var found: InetAddress? = null
                    for (i in 0 until answers.length()) {
                        val answer = answers.getJSONObject(i)
                        if (answer.optInt("type") == 1) {
                            found = literalOrLocalhost(answer.optString("data"))
                            if (found != null) break
                        }
                    }
                    found
                }
            }.getOrNull()
            if (address != null) return address
        }
        return null
    }

    private val DOH_ENDPOINTS = listOf(
        "https://1.1.1.1/dns-query",
        "https://1.0.0.1/dns-query",
    )
}

/**
 * The SOCKS5 handshake (RFC 1928, with RFC 1929 username/password when
 * credentials are configured), run on a socket already connected to the
 * proxy: asks the proxy to CONNECT to [originHost] (sent as a domain
 * name, so the proxy resolves it) on [originPort]. On return the socket
 * is a plain byte stream to the origin. Any failure is an [IOException]
 * — a normal connection error for the caller.
 */
internal fun socks5Handshake(
    input: InputStream,
    output: OutputStream,
    originHost: String,
    originPort: Int,
    proxy: SettingsStore.ApiProxySettings,
) {
    val hostBytes = originHost.toByteArray(Charsets.UTF_8)
    if (hostBytes.isEmpty() || hostBytes.size > 255) {
        throw IOException("SOCKS5: bad target host")
    }
    val username = proxy.username.toByteArray(Charsets.UTF_8)
    val password = proxy.password.toByteArray(Charsets.UTF_8)
    val hasCredentials = username.isNotEmpty()
    if (username.size > 255 || password.size > 255) {
        throw IOException("SOCKS5: credentials too long")
    }

    if (hasCredentials) {
        output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
    } else {
        output.write(byteArrayOf(0x05, 0x01, 0x00))
    }
    output.flush()
    val greeting = readExactly(input, 2)
    if (greeting[0] != 0x05.toByte()) throw IOException("SOCKS5: bad greeting from proxy")
    when (greeting[1]) {
        0x00.toByte() -> Unit
        0x02.toByte() -> {
            if (!hasCredentials) throw IOException("SOCKS5: proxy requires authentication")
            output.write(byteArrayOf(0x01, username.size.toByte()))
            output.write(username)
            output.write(byteArrayOf(password.size.toByte()))
            output.write(password)
            output.flush()
            val authReply = readExactly(input, 2)
            if (authReply[1] != 0x00.toByte()) throw IOException("SOCKS5: authentication failed")
        }
        else -> throw IOException("SOCKS5: proxy accepted no authentication method")
    }

    output.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
    output.write(hostBytes)
    output.write(byteArrayOf((originPort shr 8).toByte(), originPort.toByte()))
    output.flush()
    val head = readExactly(input, 4)
    if (head[0] != 0x05.toByte()) throw IOException("SOCKS5: bad reply from proxy")
    val status = head[1].toInt() and 0xFF
    if (status != 0) throw IOException("SOCKS5: proxy connect failed (status $status)")
    when (head[3]) {
        0x01.toByte() -> readExactly(input, 4)
        0x03.toByte() -> {
            val length = readExactly(input, 1)[0].toInt() and 0xFF
            readExactly(input, length)
        }
        0x04.toByte() -> readExactly(input, 16)
        else -> throw IOException("SOCKS5: bad reply address type")
    }
    readExactly(input, 2)
}

private fun readExactly(input: InputStream, count: Int): ByteArray {
    val result = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = input.read(result, offset, count - offset)
        if (read == -1) throw EOFException("SOCKS5: proxy closed the connection")
        offset += read
    }
    return result
}

/**
 * Tracks, per call, whether the request was fully written to the
 * connection. The relay interceptor uses it as the "request sent" guard:
 * a failure after the request went out (e.g. a read timeout) is never
 * replayed through the relay, because the origin may have processed it.
 */
class RequestSentTracker : EventListener() {

    private val sent = ConcurrentHashMap<Call, Boolean>()

    fun wasRequestSent(call: Call): Boolean = sent[call] == true

    /** Test seam. */
    internal fun markSent(call: Call) {
        sent[call] = true
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        if (request.body == null) sent[call] = true
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        sent[call] = true
    }

    override fun callEnd(call: Call) {
        sent.remove(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        sent.remove(call)
    }
}

/**
 * Whether a failed direct attempt may be retried through the relay:
 * only when the request provably never reached the origin — a connect
 * failure (refused, unresolvable, connect timeout), a TLS handshake
 * failure, or any failure before the request was sent. A read timeout
 * after the request was sent is never replayed.
 */
internal fun isRelayableTransportFailure(error: Throwable, requestSent: Boolean): Boolean =
    when (error) {
        is SocketTimeoutException -> !requestSent
        is ConnectException, is UnknownHostException, is SSLException -> true
        is InterruptedIOException -> !requestSent
        else -> false
    }

/**
 * Whether a response body is empty, without consuming it: a known zero
 * length, or — for an unknown length — not even one byte available.
 */
internal fun isEmptyBody(response: Response): Boolean {
    val body = response.body ?: return true
    val length = body.contentLength()
    if (length == 0L) return true
    if (length > 0L) return false
    return !body.source().peek().request(1)
}

/**
 * Whether a direct answer means "try the relay": the desktop client's
 * HTTP 451 (region refused), and — the deviation our case needs — an
 * HTTP 406 **with an empty body** from a Firefox Accounts host. A 406
 * carrying a body (FxA's answers are JSON) is an application answer and
 * passes through untouched.
 */
internal fun isRelayTrigger(response: Response, request: Request): Boolean = when (response.code) {
    451 -> true
    406 -> request.url.host in RELAY_406_HOSTS && isEmptyBody(response)
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
        .removeHeader("Cookie")
        .build()
}

/** A body-less copy of a blocked response, returned when the relay also fails. */
internal fun bodylessCopy(response: Response, request: Request): Response =
    Response.Builder()
        .request(request)
        .protocol(response.protocol)
        .code(response.code)
        .message(response.message)
        .headers(response.headers)
        .body(ByteArray(0).toResponseBody(response.body?.contentType()))
        .sentRequestAtMillis(response.sentRequestAtMillis)
        .receivedResponseAtMillis(response.receivedResponseAtMillis)
        .build()

/**
 * Sends each control-plane request directly, and through the user's own
 * API relay when the direct way is refused — the desktop client's
 * relayFallback, adapted to OkHttp:
 *
 * - The direct attempt gets short timeouts (connect
 *   [DIRECT_CONNECT_TIMEOUT_SECONDS]s, read [DIRECT_READ_TIMEOUT_SECONDS]s)
 *   so a blackholed route falls back quickly; the relay hop runs on a
 *   sibling client (no cookie jar, no redirect following — cookies are
 *   copied by hand against the ORIGINAL URL both ways, because the
 *   relay's host must never become the cookies' owner) with its own
 *   bounded timeouts, keeping the whole fallback well inside FxA's
 *   timestamp-skew tolerance.
 * - Fallback happens only when nothing was sent (see
 *   [isRelayableTransportFailure]) or when the direct answer is a relay
 *   trigger (see [isRelayTrigger]).
 * - After a fallback worked, requests to the SAME HOST go through the
 *   relay first for [RELAY_FIRST_MS]; the state is per-host, guarded by
 *   a generation counter (captured at request start, committed only if
 *   unchanged) and cleared when the network or the settings change. If
 *   going relay-first fails, that host returns to direct.
 * - Exactly one relay attempt per request. When the relay fails, the
 *   original result (a body-less copy of the blocked response, or the
 *   direct error) is what the caller sees.
 * - Requests with a one-shot body are never replayed.
 *
 * With no relay configured the interceptor is a single plain proceed —
 * the pre-existing behavior, byte for byte.
 */
class RelayFallbackInterceptor(
    private val relayProvider: () -> HttpUrl?,
    private val cookieJarProvider: () -> CookieJar,
    private val relayClientProvider: () -> Call.Factory,
    private val requestSent: (Call) -> Boolean = { false },
    private val nowMs: () -> Long = System::currentTimeMillis,
) : Interceptor {

    private data class Sticky(val generation: Long, val untilMs: Long)

    private val stickyByHost = ConcurrentHashMap<String, Sticky>()
    private val generation = AtomicLong(0)

    /** Drops all relay-first state (network changed, settings changed). */
    fun clearStickiness() {
        generation.incrementAndGet()
        stickyByHost.clear()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val relayBase = relayProvider() ?: return chain.proceed(request)
        if (request.body?.isOneShot() == true) return chain.proceed(request)
        val gen = generation.get()
        val host = request.url.host
        return if (isSticky(host, gen)) {
            relayFirst(chain, request, relayBase, gen)
        } else {
            directFirst(chain, request, relayBase, gen)
        }
    }

    private fun directFirst(
        chain: Interceptor.Chain,
        request: Request,
        relayBase: HttpUrl,
        gen: Long,
    ): Response {
        var originalError: IOException? = null
        var originalResponse: Response? = null
        try {
            val response = chain
                .withConnectTimeout(DIRECT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withReadTimeout(DIRECT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .proceed(request)
            if (!isRelayTrigger(response, request)) return response
            logTrigger(response, request)
            originalResponse = bodylessCopy(response, request)
            response.close()
        } catch (e: IOException) {
            if (!isRelayableTransportFailure(e, requestSent(chain.call()))) throw e
            originalError = e
        }
        return when (val outcome = tryRelay(request, relayBase)) {
            is RelayResult.Success -> {
                markSticky(request.url.host, gen)
                outcome.response
            }
            is RelayResult.Failure -> {
                AppLogger.w(TAG, "API relay ${relayBase.host} failed; surfacing the direct result", outcome.error)
                originalResponse ?: throw (originalError ?: outcome.error)
            }
        }
    }

    private fun relayFirst(
        chain: Interceptor.Chain,
        request: Request,
        relayBase: HttpUrl,
        gen: Long,
    ): Response {
        when (val outcome = tryRelay(request, relayBase)) {
            is RelayResult.Success -> {
                markSticky(request.url.host, gen)
                return outcome.response
            }
            is RelayResult.Failure ->
                AppLogger.w(TAG, "API relay ${relayBase.host} failed; trying directly", outcome.error)
        }
        unmarkSticky(request.url.host, gen)
        val response = chain.proceed(request)
        if (!isRelayTrigger(response, request)) return response
        logTrigger(response, request)
        val copy = bodylessCopy(response, request)
        response.close()
        return copy
    }

    private fun tryRelay(request: Request, relayBase: HttpUrl): RelayResult {
        val jar = cookieJarProvider()
        var relayRequest = buildRelayRequest(request, relayBase)
        val cookies = jar.loadForRequest(request.url)
        if (cookies.isNotEmpty()) {
            relayRequest = relayRequest.newBuilder()
                .header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
                .build()
        }
        val response = try {
            relayClientProvider().newCall(relayRequest).execute()
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
        val setCookies = Cookie.parseAll(request.url, response.headers)
        if (setCookies.isNotEmpty()) {
            jar.saveFromResponse(request.url, setCookies)
        }
        return RelayResult.Success(response)
    }

    private fun logTrigger(response: Response, request: Request) {
        // The relay base URL itself (which may carry a secret) is never
        // logged — only its host in the failure messages above. The
        // trigger's own headers are logged to tell edge blocks apart
        // from application answers.
        AppLogger.i(
            TAG,
            "relay trigger ${response.code} for ${request.url.host} via ${response.header("Server") ?: "?"}; " +
                "content-type=${response.header("Content-Type") ?: "-"}",
        )
    }

    private fun isSticky(host: String, gen: Long): Boolean {
        val sticky = stickyByHost[host] ?: return false
        if (sticky.generation != gen || nowMs() > sticky.untilMs) {
            stickyByHost.remove(host, sticky)
            return false
        }
        return true
    }

    private fun markSticky(host: String, gen: Long) {
        if (generation.get() != gen) return
        stickyByHost[host] = Sticky(gen, nowMs() + RELAY_FIRST_MS)
    }

    private fun unmarkSticky(host: String, gen: Long) {
        val sticky = stickyByHost[host] ?: return
        if (sticky.generation == gen) stickyByHost.remove(host, sticky)
    }

    private sealed interface RelayResult {
        class Success(val response: Response) : RelayResult
        class Failure(val error: IOException) : RelayResult
    }

    companion object {
        private const val TAG = "RelayFallback"

        /** How long the direct way gets to connect before the relay is tried. */
        const val DIRECT_CONNECT_TIMEOUT_SECONDS = 4

        /** How long the direct way gets to answer before the relay is tried. */
        const val DIRECT_READ_TIMEOUT_SECONDS = 8

        /** After a fallback, requests go through the relay first for this long. */
        const val RELAY_FIRST_MS = 10 * 60 * 1000L

        /** A relay that refuses a request answers with this header. */
        const val RELAY_ERROR_HEADER = "X-Relay-Error"
    }
}
