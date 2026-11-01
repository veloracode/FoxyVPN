package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.vpn.upstream.TlsClientHelloSplitter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * TLS ClientHello fragmentation: the pure splitter's segment layout, and a
 * socket-level check that SplitClientHelloSocket puts the hello on the
 * wire in more than one write when a mode is on, and untouched when off.
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
    fun socket_offMode_helloLeavesInOneWrite() {
        repeat(3) {
            val arrivals = runSocketScenario(SettingsStore.TlsSplitMode.OFF)
            assertEquals("OFF must not split (arrivals: ${arrivals.size})", 1, arrivals.size)
        }
    }

    @Test
    fun socket_firstByteMode_helloLeavesInMultipleWrites() {
        repeat(3) {
            val arrivals = runSocketScenario(SettingsStore.TlsSplitMode.FIRST_BYTE)
            assertTrue("FIRST_BYTE must split (arrivals: ${arrivals.size})", arrivals.size >= 2)
            assertEquals(1, arrivals.first().size)
        }
    }

    @Test
    fun socket_sniMode_helloLeavesInMultipleWrites() {
        repeat(3) {
            val arrivals = runSocketScenario(SettingsStore.TlsSplitMode.SNI)
            assertTrue("SNI must split (arrivals: ${arrivals.size})", arrivals.size >= 2)
        }
    }

    /**
     * Sends one ClientHello through a SplitClientHelloSocket to a loopback
     * listener and returns the byte chunks as they arrived. The reader is
     * parked in read() before the client writes, and the splitter flushes
     * each segment with TCP_NODELAY, so segment boundaries are arrival
     * boundaries on loopback.
     */
    private fun runSocketScenario(mode: SettingsStore.TlsSplitMode): List<ByteArray> {
        val hello = clientHello("api.accounts.firefox.com")
        val arrivals = CopyOnWriteArrayList<ByteArray>()
        val received = ByteArrayOutputStream()
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val readerReady = java.util.concurrent.CountDownLatch(1)
        val reader = thread(isDaemon = true) {
            listener.accept().use { socket ->
                socket.soTimeout = 3_000
                val input = socket.getInputStream()
                val buffer = ByteArray(8192)
                readerReady.countDown()
                while (true) {
                    val n = try {
                        input.read(buffer)
                    } catch (e: Exception) {
                        break
                    }
                    if (n == -1) break
                    val chunk = buffer.copyOf(n)
                    arrivals += chunk
                    synchronized(received) { received.write(chunk) }
                }
            }
        }
        val socket = SplitClientHelloSocket { mode }
        socket.connect(InetSocketAddress("127.0.0.1", listener.localPort), 2_000)
        readerReady.await(2, java.util.concurrent.TimeUnit.SECONDS)
        Thread.sleep(150) // let the reader park inside read()
        val out = socket.getOutputStream()
        out.write(hello)
        out.flush()
        Thread.sleep(400)
        socket.close()
        reader.join(4_000)
        listener.close()
        assertArrayEquals(hello, synchronized(received) { received.toByteArray() })
        return arrivals
    }
}
