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


    /** The first TLS record of a captured stream (a ClientHello). */
    private fun helloRecord(wire: ByteArray): ByteArray {
        assertTrue("no ClientHello captured", wire.size > 60)
        val recordLength = ((wire[3].toInt() and 0xFF) shl 8) or (wire[4].toInt() and 0xFF)
        assertTrue("record fits the capture", wire.size >= 5 + recordLength)
        return wire.copyOf(5 + recordLength)
    }

    @Test
    fun sniMode_realTlsStack_helloLeavesSplitInsideTheSni() {
        val capture = captureHello(SettingsStore.TlsSplitMode.SNI)
        val record = helloRecord(capture.wireBytes)
        assertEquals(0x16, record[0].toInt() and 0xFF)
        assertTrue(
            "SNI name on the wire",
            record.toString(Charsets.ISO_8859_1).contains("api.accounts.firefox.com"),
        )
        // The expected split points come from the splitter's own parse
        // of the captured hello (a real TLS 1.3 hello carries many
        // extensions before the server_name, so the offset is whatever
        // the parse says — 153 on this JSSE — not a fixed constant).
        val sni = com.vauth.foxyvpn.vpn.upstream.TlsClientHelloSplitter.findSniOffset(record)
        assertTrue("SNI offset found in the captured hello", sni != null)
        assertTrue("write sizes were ${capture.writeSizes}", capture.writeSizes.size >= 3)
        assertEquals(sni!!.first, capture.writeSizes[0])
        assertEquals(1, capture.writeSizes[1])
        assertEquals(record.size, capture.writeSizes.take(3).sum())
        // The split stream is lossless over the record.
        assertArrayEquals(record, capture.recordedBytes.copyOf(record.size))
    }

    @Test
    fun offMode_realTlsStack_helloLeavesUnmodified() {
        val capture = captureHello(SettingsStore.TlsSplitMode.OFF)
        val record = helloRecord(capture.wireBytes)
        assertEquals(0x16, record[0].toInt() and 0xFF)
        // The bytes pass through byte-identical, and no write boundary
        // falls at either point where SNI mode would have split.
        assertArrayEquals(record, capture.recordedBytes.copyOf(record.size))
        val sni = com.vauth.foxyvpn.vpn.upstream.TlsClientHelloSplitter.findSniOffset(record)
        assertTrue("SNI offset found in the captured hello", sni != null)
        val boundaries = capture.writeSizes.runningFold(0) { acc, size -> acc + size }.drop(1).dropLast(1)
        assertTrue("no split at ${sni!!.first} in $boundaries", sni.first !in boundaries)
        assertTrue("no split at ${sni.first + 1} in $boundaries", (sni.first + 1) !in boundaries)
    }
}
