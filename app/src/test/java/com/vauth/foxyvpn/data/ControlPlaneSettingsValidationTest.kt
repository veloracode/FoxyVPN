package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Proxy
import java.net.URI

/** Pure validation/parsing behind the control-plane fallback settings. */
class ControlPlaneSettingsValidationTest {

    // Proxy host/port ---------------------------------------------------

    @Test
    fun cleanProxyHost_stripsSchemeSpacesAndTrailingSlash() {
        assertEquals("proxy.example.com", SettingsStore.cleanProxyHost("  socks5://proxy.example.com/ "))
        assertEquals("127.0.0.1", SettingsStore.cleanProxyHost("127.0.0.1"))
        assertEquals("proxy.example.com", SettingsStore.cleanProxyHost("proxy.example.com"))
        assertEquals("", SettingsStore.cleanProxyHost("   "))
    }

    // NOTE: SettingsStore.isValidProxyHost is upstream v2's validator and
    // calls Android framework APIs (InetAddresses / Patterns) that the
    // stub android.jar in JVM unit tests cannot execute. It is upstream's
    // own code, covered on-device; the fork's pure validators are below.

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

    // Control-plane DNS (placeholder while the account proxy is on) ------

    @Test
    fun controlPlaneDns_returnsHostnameCarryingPlaceholderWhenProxyOn() {
        val settings = SettingsStore.ApiProxySettings("127.0.0.1", 1080, "", "")
        val dns = ControlPlaneDns(proxyProvider = { settings })
        val addresses = dns.lookup("api.accounts.firefox.com")
        assertEquals(1, addresses.size)
        assertEquals("api.accounts.firefox.com", addresses[0].hostName)
        assertEquals("0.0.0.0", addresses[0].hostAddress)
    }

    @Test
    fun controlPlaneDns_usesSystemResolverWhenProxyOff() {
        val dns = ControlPlaneDns(proxyProvider = { null })
        val addresses = dns.lookup("localhost")
        assertTrue(addresses.isNotEmpty())
        assertTrue(addresses.any { it.isLoopbackAddress })
    }

    // Proxy host resolution (never system DNS) ---------------------------

    @Test
    fun proxyHostResolver_acceptsLiteralsAndLocalhost() {
        assertEquals(
            "127.0.0.1",
            ProxyHostResolver.literalOrLocalhost("127.0.0.1")?.hostAddress,
        )
        assertEquals(
            "192.168.1.10",
            ProxyHostResolver.literalOrLocalhost("192.168.1.10")?.hostAddress,
        )
        assertEquals(
            "127.0.0.1",
            ProxyHostResolver.literalOrLocalhost("localhost")?.hostAddress,
        )
        assertNull(ProxyHostResolver.literalOrLocalhost("256.1.1.1"))
        assertNull(ProxyHostResolver.literalOrLocalhost("proxy.example.com"))
        assertNull(ProxyHostResolver.literalOrLocalhost("1.2.3"))
    }

    @Test
    fun proxyHostResolver_resolvesNamesOnlyThroughTheInjectedDoh() {
        val original = ProxyHostResolver.dohLookup
        try {
            ProxyHostResolver.dohLookup = { host ->
                if (host == "proxy.example.com") {
                    ProxyHostResolver.literalOrLocalhost("203.0.113.7")
                } else {
                    null
                }
            }
            assertEquals(
                "203.0.113.7",
                ProxyHostResolver.resolve("proxy.example.com").hostAddress,
            )
            try {
                ProxyHostResolver.resolve("missing.example.com")
                fail("expected UnknownHostException")
            } catch (expected: java.net.UnknownHostException) {
            }
        } finally {
            ProxyHostResolver.dohLookup = original
            ProxyHostResolver.clearCache()
        }
    }
}
