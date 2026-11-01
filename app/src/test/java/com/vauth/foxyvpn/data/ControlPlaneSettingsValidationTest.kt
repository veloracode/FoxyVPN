package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy
import java.net.URI

/** Pure validation/parsing behind the control-plane fallback settings. */
class ControlPlaneSettingsValidationTest {

    // TlsSplitMode ------------------------------------------------------

    @Test
    fun tlsSplitMode_parse_roundTripsKnownNames() {
        assertEquals(SettingsStore.TlsSplitMode.OFF, SettingsStore.TlsSplitMode.parse("OFF"))
        assertEquals(SettingsStore.TlsSplitMode.FIRST_BYTE, SettingsStore.TlsSplitMode.parse("FIRST_BYTE"))
        assertEquals(SettingsStore.TlsSplitMode.SNI, SettingsStore.TlsSplitMode.parse("SNI"))
    }

    @Test
    fun tlsSplitMode_parse_defaultsToOff() {
        assertEquals(SettingsStore.TlsSplitMode.OFF, SettingsStore.TlsSplitMode.parse(null))
        assertEquals(SettingsStore.TlsSplitMode.OFF, SettingsStore.TlsSplitMode.parse(""))
        assertEquals(SettingsStore.TlsSplitMode.OFF, SettingsStore.TlsSplitMode.parse("bogus"))
        assertEquals(SettingsStore.TlsSplitMode.OFF, SettingsStore.TlsSplitMode.parse("sni"))
    }

    // Proxy host/port ---------------------------------------------------

    @Test
    fun cleanProxyHost_stripsSchemeSpacesAndTrailingSlash() {
        assertEquals("proxy.example.com", SettingsStore.cleanProxyHost("  socks5://proxy.example.com/ "))
        assertEquals("127.0.0.1", SettingsStore.cleanProxyHost("127.0.0.1"))
        assertEquals("proxy.example.com", SettingsStore.cleanProxyHost("proxy.example.com"))
        assertEquals("", SettingsStore.cleanProxyHost("   "))
    }

    @Test
    fun proxyHostValidation() {
        assertTrue(SettingsStore.isValidProxyHost("proxy.example.com"))
        assertTrue(SettingsStore.isValidProxyHost("127.0.0.1"))
        assertTrue(SettingsStore.isValidProxyHost("localhost"))
        assertTrue(SettingsStore.isValidProxyHost("::1"))
        assertFalse(SettingsStore.isValidProxyHost(""))
        assertFalse(SettingsStore.isValidProxyHost("bad host"))
        assertFalse(SettingsStore.isValidProxyHost("host/name"))
        assertFalse(SettingsStore.isValidProxyHost(".example.com"))
        assertFalse(SettingsStore.isValidProxyHost("a".repeat(254)))
    }

    @Test
    fun proxyPortValidation() {
        assertFalse(SettingsStore.isValidProxyPort(0))
        assertTrue(SettingsStore.isValidProxyPort(1))
        assertTrue(SettingsStore.isValidProxyPort(1080))
        assertTrue(SettingsStore.isValidProxyPort(65_535))
        assertFalse(SettingsStore.isValidProxyPort(65_536))
        assertFalse(SettingsStore.isValidProxyPort(-1))
    }

    @Test
    fun proxyCredentials_bothOrNeither_andLengthLimit() {
        assertNull(SettingsStore.proxyCredentialsError("", ""))
        assertNull(SettingsStore.proxyCredentialsError("user", "pass"))
        assertNotNull(SettingsStore.proxyCredentialsError("user", ""))
        assertNotNull(SettingsStore.proxyCredentialsError("", "pass"))
        assertNull(SettingsStore.proxyCredentialsError("u".repeat(255), "p"))
        assertNotNull(SettingsStore.proxyCredentialsError("u".repeat(256), "p"))
        assertNotNull(SettingsStore.proxyCredentialsError("u", "p".repeat(256)))
    }

    // Relay URL ---------------------------------------------------------

    @Test
    fun relayUrl_normalizesValidHttps() {
        assertEquals("https://relay.example.com/", SettingsStore.normalizeRelayUrl("https://relay.example.com"))
        assertEquals(
            "https://relay.example.com/r?key=secret",
            SettingsStore.normalizeRelayUrl("  https://relay.example.com/r?key=secret  "),
        )
    }

    @Test
    fun relayUrl_rejectsNonHttpsAndGarbage() {
        assertNull(SettingsStore.normalizeRelayUrl(""))
        assertNull(SettingsStore.normalizeRelayUrl("http://relay.example.com"))
        assertNull(SettingsStore.normalizeRelayUrl("ftp://relay.example.com"))
        assertNull(SettingsStore.normalizeRelayUrl("https://"))
        assertNull(SettingsStore.normalizeRelayUrl("not a url"))
        assertNull(SettingsStore.normalizeRelayUrl("relay.example.com"))
        assertFalse(SettingsStore.isValidRelayUrl("http://relay.example.com/"))
        assertTrue(SettingsStore.isValidRelayUrl("https://relay.example.com/"))
    }

    // Proxy selector ----------------------------------------------------

    @Test
    fun proxySelector_returnsSocksProxyWhenConfigured() {
        val settings = SettingsStore.ApiProxySettings("proxy.example.com", 1080, "", "")
        val selector = ControlPlaneProxySelector { settings }
        val proxies = selector.select(URI("https://api.accounts.firefox.com/v1/test"))
        assertEquals(1, proxies.size)
        assertEquals(Proxy.Type.SOCKS, proxies[0].type())
        val address = proxies[0].address() as java.net.InetSocketAddress
        assertEquals("proxy.example.com", address.hostString)
        assertEquals(1080, address.port)
    }

    @Test
    fun proxySelector_delegatesToPlatformDefaultWhenOff() {
        val selector = ControlPlaneProxySelector { null }
        val uri = URI("https://api.accounts.firefox.com/v1/test")
        val expected = java.net.ProxySelector.getDefault()?.select(uri) ?: listOf(Proxy.NO_PROXY)
        assertEquals(expected, selector.select(uri))
    }
}
