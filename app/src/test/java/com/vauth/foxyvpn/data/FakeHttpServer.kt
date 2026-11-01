package com.vauth.foxyvpn.data

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The smallest possible HTTP/1.1 server for JVM unit tests: one canned
 * response per connection, every request recorded. Not a general server —
 * just enough for OkHttp clients on loopback.
 */
class FakeHttpServer(
    private val responder: (RecordedRequest) -> FakeResponse,
) : Closeable {

    data class RecordedRequest(
        val method: String,
        /** Raw request target: path + query, as sent. */
        val target: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    data class FakeResponse(
        val code: Int,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
    )

    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    val requests = CopyOnWriteArrayList<RecordedRequest>()

    @Volatile
    private var closed = false

    init {
        thread(name = "fake-http-$port", isDaemon = true) { acceptLoop() }
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: SocketException) {
                return
            }
            thread(isDaemon = true) { handle(socket) }
        }
    }

    private fun handle(socket: java.net.Socket) {
        socket.use { s ->
            s.soTimeout = 10_000
            val request = readRequest(s.getInputStream()) ?: return
            requests += request
            val response = responder(request)
            val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ${response.code} X\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Content-Type: application/json\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Connection: close\r\n")
                append("\r\n")
            }
            val out = s.getOutputStream()
            out.write(head.toByteArray(Charsets.UTF_8))
            out.write(bodyBytes)
            out.flush()
        }
    }

    private fun readRequest(input: InputStream): RecordedRequest? {
        val raw = ByteArrayOutputStream()
        val window = IntArray(4)
        var filled = 0
        while (true) {
            val b = input.read()
            if (b == -1) return null
            raw.write(b)
            window[0] = window[1]; window[1] = window[2]; window[2] = window[3]; window[3] = b
            if (filled < 4) filled++
            if (filled == 4 && window.contentEquals(HEADER_END)) break
        }
        val headText = raw.toByteArray().toString(Charsets.UTF_8)
        val lines = headText.split("\r\n")
        val requestLine = lines[0].split(" ")
        if (requestLine.size < 2) return null
        val headers = lines.drop(1)
            .filter { it.contains(":") }
            .associate {
                it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim()
            }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n == -1) break
            read += n
        }
        return RecordedRequest(requestLine[0], requestLine[1], headers, body)
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
    }

    private companion object {
        val HEADER_END = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
    }
}
