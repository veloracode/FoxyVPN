package com.vauth.foxyvpn.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/**
 * Fragmentation through the REAL TLS stack: an actual SSLSocket (the
 * platform JSSE here; Conscrypt on Android) layered over a socket whose
 * output stream is the production SplitClientHelloOutputStream, talking
 * to a plain loopback capture server. This is the level at which the
 * splitter could silently no-op — if the TLS implementation presented
 * the ClientHello in a shape the splitter doesn't recognize (or didn't
 * use the Java stream at all), the segmentation assertions below fail.
 *
 * What this proves: with the JVM TLS stack, the ClientHello reaches the
 * splitter as one write, is recognized, and leaves in the mode's
 * segments, byte-identical. What it cannot prove from here: Android's
 * Conscrypt may write TLS records natively on the file descriptor on
 * some versions, bypassing the Java stream entirely — see REVIEW.md;
 * that path is only observable on a device.
 */
class TlsStackSplitTest {

    private class TeeOutputStream(
        private val first: OutputStream,
        private val second: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) {
            first.write(b); second.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            first.write(b, off, len); second.write(b, off, len)
        }

        override fun flush() {
            first.flush(); second.flush()
        }
    }

    private class InstrumentedSocket(
        private val mode: SettingsStore.TlsSplitMode,
    ) : Socket() {
        val recording = RecordingOutputStream()
        private var stream: OutputStream? = null

        override fun getOutputStream(): OutputStream =
            stream ?: SplitClientHelloOutputStream(
                TeeOutputStream(super.getOutputStream(), recording),
                { mode },
            ).also { stream = it }
    }

    private data class Capture(
        val wireBytes: ByteArray,
        val writeSizes: List<Int>,
        val recordedBytes: ByteArray,
    )

    private fun captureHello(mode: SettingsStore.TlsSplitMode): Capture {
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val captured = ByteArrayOutputStream()
        val server = thread(isDaemon = true) {
            listener.accept().use { socket ->
                socket.soTimeout = 3_000
                val input = socket.getInputStream()
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        synchronized(captured) { captured.write(buffer, 0, n) }
                    }
                } catch (e: Exception) {
                    // Timeout or reset: the capture is complete either way.
                }
            }
        }
        val socket = InstrumentedSocket(mode)
        socket.connect(InetSocketAddress("127.0.0.1", listener.localPort), 3_000)
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(socket, "api.accounts.firefox.com", 443, true) as SSLSocket
        val handshake = thread(isDaemon = true) {
            // The server never speaks TLS, so the handshake cannot
            // complete; the ClientHello leaving the client is the point.
            runCatching { ssl.startHandshake() }
        }
        handshake.join(5_000)
        runCatching { ssl.close() }
        runCatching { socket.close() }
        server.join(4_000)
        listener.close()
        return Capture(
            wireBytes = synchronized(captured) { captured.toByteArray() },
            writeSizes = synchronized(socket.recording) { socket.recording.writes.map { it.size } },
            recordedBytes = socket.recording.concatenated(),
        )
    }

    @Test
    fun sniMode_realTlsStack_helloLeavesSplitInsideTheSni() {
        val capture = captureHello(SettingsStore.TlsSplitMode.SNI)
        val hello = capture.wireBytes
        assertTrue("no ClientHello captured", hello.size > 60)
        assertEquals(0x16, hello[0].toInt() and 0xFF)
        assertTrue(
            "SNI name on the wire",
            hello.toString(Charsets.ISO_8859_1).contains("api.accounts.firefox.com"),
        )
        // The splitter's segments are the first writes: 61 bytes up to
        // the first SNI byte, the first hostname byte alone, then the
        // rest of the hello (the reassembled stream is the wire bytes).
        assertTrue("write sizes were ${capture.writeSizes}", capture.writeSizes.size >= 3)
        assertEquals(61, capture.writeSizes[0])
        assertEquals(1, capture.writeSizes[1])
        val segmentsBytes = capture.writeSizes.take(3).sum()
        assertTrue(segmentsBytes <= hello.size)
        // The split stream is lossless: what the splitter wrote is
        // byte-identical to what arrived on the wire.
        assertArrayEquals(hello, capture.recordedBytes)
    }

    @Test
    fun offMode_realTlsStack_helloLeavesUnmodified() {
        val capture = captureHello(SettingsStore.TlsSplitMode.OFF)
        val hello = capture.wireBytes
        assertTrue("no ClientHello captured", hello.size > 60)
        assertEquals(0x16, hello[0].toInt() and 0xFF)
        // What passed through the (inactive) splitter is byte-identical
        // to what arrived on the wire.
        assertArrayEquals(hello, capture.recordedBytes)
    }
}
