package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.vpn.upstream.TlsClientHelloSplitter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * TLS ClientHello fragmentation: the pure splitter's segment layout, the
 * splitting stream's write segmentation (deterministic — counting TCP
 * arrivals instead proved flaky under load, because a loopback receiver
 * may coalesce two flushed writes into one read even though they left as
 * separate writes), and one real-socket pass proving the bytes cross a
 * socket losslessly.
 */
class TlsSplitSocketTest {

    /** A minimal, parser-valid ClientHello with an SNI name. */
    private fun clientHello(hostname: String): ByteArray {
        val name = hostname.toByteArray(Charsets.US_ASCII)
        val extBodyLen = 2 + 1 + 2 + name.size
        val extLen = 4 + extBodyLen
        val total = 52 + extLen
        val b = ByteArray(total)
        var i = 0
        fun u8(v: Int) { b[i++] = v.toByte() }
        fun u16(v: Int) { b[i++] = (v shr 8).toByte(); b[i++] = v.toByte() }
        fun u24(v: Int) { b[i++] = (v shr 16).toByte(); b[i++] = (v shr 8).toByte(); b[i++] = v.toByte() }
        u8(0x16); u16(0x0301); u16(total - 5)       // record header (type, version, length)
        u8(0x01); u24(total - 9)                    // handshake header
        u16(0x0303)                                 // client version
        repeat(32) { u8(0xAB) }                     // random
        u8(0)                                       // session id
        u16(2); u16(0x1301)                         // cipher suites
        u8(1); u8(0)                                // compression methods
        u16(extLen)                                 // extensions length
        u16(0); u16(extBodyLen)                     // server_name extension
        u16(1 + 2 + name.size)                      // server_name list length
        u8(0); u16(name.size)                       // host_name
        name.copyInto(b, i)
        return b
    }

    /** An OutputStream that records every individual write call. */
    private class RecordingOutputStream : OutputStream() {
        val writes = mutableListOf<ByteArray>()
        override fun write(b: Int) {
            writes += byteArrayOf(b.toByte())
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            writes += b.copyOfRange(off, off + len)
        }
        fun concatenated(): ByteArray = writes.reduce { acc, w -> acc + w }
    }

    private fun splitWrites(mode: SettingsStore.TlsSplitMode, bytes: ByteArray): List<ByteArray> {
        val recording = RecordingOutputStream()
        val stream = SplitClientHelloOutputStream(recording, { mode })
        stream.write(bytes)
        stream.flush()
        return recording.writes
    }

    @Test
    fun splitter_sniOffsetAndSegments() {
        val hello = clientHello("api.accounts.firefox.com")
        val sni = TlsClientHelloSplitter.findSniOffset(hello)
        assertNotNull(sni)
        assertEquals(61, sni!!.first)
        assertEquals(61 + "api.accounts.firefox.com".length, sni.second)

        val firstByte = TlsClientHelloSplitter.splitBytes(hello, 0, hello.size, SettingsStore.TlsSplitMode.FIRST_BYTE)
        assertNotNull(firstByte)
        assertEquals(2, firstByte!!.size)
        assertEquals(1, firstByte[0].size)
        assertArrayEquals(hello, firstByte.reduce { acc, seg -> acc + seg })

        val sniSplit = TlsClientHelloSplitter.splitBytes(hello, 0, hello.size, SettingsStore.TlsSplitMode.SNI)
        assertNotNull(sniSplit)
        assertEquals(3, sniSplit!!.size)
        assertEquals(61, sniSplit[0].size)
        assertEquals(1, sniSplit[1].size)
        assertEquals('a'.code.toByte(), sniSplit[1][0])
        assertArrayEquals(hello, sniSplit.reduce { acc, seg -> acc + seg })

        assertNull(TlsClientHelloSplitter.splitBytes(hello, 0, hello.size, SettingsStore.TlsSplitMode.OFF))
        assertTrue(TlsClientHelloSplitter.looksLikeClientHello(hello, 0, hello.size))
    }

    @Test
    fun stream_offMode_helloLeavesInOneWrite() {
        val hello = clientHello("api.accounts.firefox.com")
        val writes = splitWrites(SettingsStore.TlsSplitMode.OFF, hello)
        assertEquals(1, writes.size)
        assertArrayEquals(hello, writes[0])
    }

    @Test
    fun stream_firstByteMode_helloLeavesInTwoWrites() {
        val hello = clientHello("api.accounts.firefox.com")
        val writes = splitWrites(SettingsStore.TlsSplitMode.FIRST_BYTE, hello)
        assertEquals(2, writes.size)
        assertEquals(1, writes[0].size)
        assertArrayEquals(hello, writes.reduce { acc, w -> acc + w })
    }

    @Test
    fun stream_sniMode_helloLeavesInThreeWrites() {
        val hello = clientHello("api.accounts.firefox.com")
        val writes = splitWrites(SettingsStore.TlsSplitMode.SNI, hello)
        assertEquals(3, writes.size)
        assertEquals(61, writes[0].size)
        assertEquals(1, writes[1].size)
        assertArrayEquals(hello, writes.reduce { acc, w -> acc + w })
    }

    @Test
    fun stream_splitsOnlyTheFirstHello() {
        val hello = clientHello("api.accounts.firefox.com")
        val recording = RecordingOutputStream()
        val stream = SplitClientHelloOutputStream(recording, { SettingsStore.TlsSplitMode.FIRST_BYTE })
        stream.write(hello)
        stream.write(hello)
        stream.flush()
        // First hello: two writes. Second hello on the same stream: whole.
        assertEquals(3, recording.writes.size)
        assertEquals(hello.size, recording.writes[2].size)
        assertArrayEquals(hello + hello, recording.concatenated())
    }

    @Test
    fun stream_nonHelloBytesPassThroughUntouched() {
        val data = "GET / HTTP/1.1\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val writes = splitWrites(SettingsStore.TlsSplitMode.SNI, data)
        assertEquals(1, writes.size)
        assertArrayEquals(data, writes[0])
    }

    @Test
    fun socket_sniMode_helloCrossesLosslessly() {
        val hello = clientHello("api.accounts.firefox.com")
        val received = ByteArrayOutputStream()
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val reader = thread(isDaemon = true) {
            listener.accept().use { socket ->
                val input = socket.getInputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    synchronized(received) { received.write(buffer, 0, n) }
                }
            }
        }
        val socket = SplitClientHelloSocket { SettingsStore.TlsSplitMode.SNI }
        socket.connect(InetSocketAddress("127.0.0.1", listener.localPort), 2_000)
        val out = socket.getOutputStream()
        out.write(hello)
        out.flush()
        socket.close()
        reader.join(4_000)
        listener.close()
        assertArrayEquals(hello, synchronized(received) { received.toByteArray() })
    }
}
