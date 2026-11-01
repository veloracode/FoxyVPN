package com.vauth.foxyvpn.data

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The smallest possible SOCKS5 server for JVM unit tests: performs the
 * greeting (optionally requiring username/password auth), records the
 * CONNECT target, then pipes the connection to a fixed loopback origin
 * port regardless of the requested target (the recording is what the
 * tests assert on).
 */
class FakeSocks5Server(
    private val originPort: Int,
    private val requiredAuth: Pair<String, String>? = null,
) : Closeable {

    data class ConnectRequest(val atyp: Int, val host: String, val port: Int)

    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    val connections = AtomicInteger(0)
    val offeredMethods = CopyOnWriteArrayList<List<Int>>()
    val authentications = CopyOnWriteArrayList<Pair<String, String>>()
    val connectRequests = CopyOnWriteArrayList<ConnectRequest>()

    @Volatile
    private var closed = false

    init {
        thread(name = "fake-socks5-$port", isDaemon = true) { acceptLoop() }
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: SocketException) {
                return
            }
            connections.incrementAndGet()
            thread(isDaemon = true) { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            // Greeting.
            val greeting = readN(input, 2)
            check(greeting[0].toInt() == 5) { "bad version" }
            val methods = readN(input, greeting[1].toInt() and 0xFF).map { it.toInt() and 0xFF }
            offeredMethods += methods
            if (requiredAuth != null) {
                output.write(byteArrayOf(5, 2))
                output.flush()
                readN(input, 1) // auth version
                val userLength = readN(input, 1)[0].toInt() and 0xFF
                val user = readN(input, userLength).toString(Charsets.UTF_8)
                val passLength = readN(input, 1)[0].toInt() and 0xFF
                val pass = readN(input, passLength).toString(Charsets.UTF_8)
                authentications += user to pass
                val ok = user == requiredAuth.first && pass == requiredAuth.second
                output.write(byteArrayOf(1, if (ok) 0 else 1))
                output.flush()
                if (!ok) {
                    socket.close()
                    return
                }
            } else {
                output.write(byteArrayOf(5, 0))
                output.flush()
            }
            // CONNECT.
            val head = readN(input, 4)
            check(head[0].toInt() == 5 && head[1].toInt() == 1) { "bad connect" }
            val atyp = head[3].toInt() and 0xFF
            val host = when (atyp) {
                1 -> readN(input, 4).joinToString(".") { (it.toInt() and 0xFF).toString() }
                3 -> {
                    val length = readN(input, 1)[0].toInt() and 0xFF
                    readN(input, length).toString(Charsets.UTF_8)
                }
                4 -> readN(input, 16).joinToString(":") { "%02x".format(it) }
                else -> error("bad atyp $atyp")
            }
            val portBytes = readN(input, 2)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
            connectRequests += ConnectRequest(atyp, host, targetPort)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            output.flush()
            // Pipe to the origin.
            val origin = Socket(InetAddress.getByName("127.0.0.1"), originPort)
            val t1 = thread(isDaemon = true) { copy(input, origin.getOutputStream()) }
            val t2 = thread(isDaemon = true) { copy(origin.getInputStream(), output) }
            t1.join()
            t2.join()
            origin.close()
            socket.close()
        } catch (e: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (e: Exception) {
            // Peer went away; the other direction ends on its own.
        }
        runCatching { output.flush() }
    }

    private fun readN(input: InputStream, count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(result, offset, count - offset)
            check(n != -1) { "EOF" }
            offset += n
        }
        return result
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
    }
}
