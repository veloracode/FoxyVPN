package com.vauth.foxyvpn.vpn

import com.vauth.foxyvpn.data.AdBlockLists
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.data.TunnelSettings
import com.vauth.foxyvpn.data.model.ProxyCandidate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A3 gate for Block ads (B2; DNS rule added in the v1.1.1 rescue):
 * the ONLY data-plane change in the whole overhaul. OFF must be
 * byte-identical to building without the feature at all; ON must
 * differ by exactly the inline rule-set, one route rule, and one
 * dns rule — nothing else in the generated config may move.
 *
 * The dns rule is the rescue fix: in TUN mode the router sees only
 * destination IPs, so the route-level reject could never match a
 * domain. The dns.rules reject refuses ad domains where domains are
 * actually visible (proven at runtime against a real sing-box core,
 * 2026-11-03 ads audit + re-verified on this builder output).
 */
class SingBoxConfigAdBlockTest {

    private val candidate = ProxyCandidate(
        host = "muc139.m1.fastly-masque.net",
        port = 2499,
        countryCode = "DE",
        countryName = "Germany",
        cityCode = "MUC",
    )

    private val lists = AdBlockLists(
        domainSuffix = listOf("doubleclick.net", "ads.example.com"),
        domain = listOf("tracker.example.org"),
        domainRegex = listOf("^ads[0-9]+\\."),
        domainKeyword = listOf("banner-ad"),
    )

    private fun settings(blockAds: Boolean) = TunnelSettings(
        automaticRecovery = true,
        dnsProvider = SettingsStore.DnsProvider.CLOUDFLARE,
        effectiveCustomEdgeAddress = null,
        effectiveDohUrl = "https://1.1.1.1/dns-query",
        excludedApps = emptySet(),
        includedApps = emptySet(),
        splitTunnelMode = SettingsStore.SplitTunnelMode.EXCLUDE,
        fragmentFallbackDelay = 300,
        httpVersion = SettingsStore.HttpVersion.HTTP_2,
        ipv6Enabled = false,
        maxConcurrentStreams = 8,
        mtu = 1280,
        localProxyAuthEnabled = false,
        localProxyUsername = "",
        localProxyPassword = "",
        proxyOnlyMode = false,
        socksBindAddress = "127.0.0.1",
        socksPort = 1080,
        tcpFastOpen = false,
        tlsFragmentMode = SettingsStore.TlsFragmentMode.OFF,
        upstreamProxyEnabled = false,
        upstreamProxyHost = "",
        upstreamProxyPassword = "",
        upstreamProxyPort = 7890,
        upstreamProxyType = SettingsStore.UpstreamProxyType.SOCKS5,
        upstreamProxyUsername = "",
        utlsFingerprint = SettingsStore.UtlsFingerprint.FIREFOX,
        bypassLocalNetwork = true,
        blockAds = blockAds,
    )

    private fun build(blockAds: Boolean, withLists: Boolean): String =
        SingBoxConfigBuilder.build(
            primaryCandidate = candidate,
            settings = settings(blockAds),
            bearerToken = "token",
            appPackageName = "com.veloracode.foxyvpn",
            controllerSecret = "secret",
            adBlockLists = if (withLists) lists else null,
        )

    /** Canonical (key-sorted) rendering for order-insensitive compare. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> {
            val keys = mutableListOf<String>()
            val it = value.keys()
            while (it.hasNext()) keys.add(it.next())
            keys.sorted().joinToString(",", "{", "}") { k ->
                JSONObject.quote(k) + ":" + canonical(value.get(k))
            }
        }
        is JSONArray ->
            (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    @Test
    fun off_isByteIdentical_withAndWithoutLists() {
        assertEquals(build(blockAds = false, withLists = false), build(blockAds = false, withLists = true))
        assertFalse(build(blockAds = false, withLists = true).contains(SingBoxConfigBuilder.ADS_RULE_SET_TAG))
    }

    @Test
    fun off_dnsHasNoRules() {
        val off = JSONObject(build(blockAds = false, withLists = true))
        assertFalse(off.getJSONObject("dns").has("rules"))
    }

    @Test
    fun on_dnsRulesRefuseAdDomains() {
        val on = JSONObject(build(blockAds = true, withLists = true))
        val dnsRules = on.getJSONObject("dns").getJSONArray("rules")
        assertEquals(1, dnsRules.length())
        val rule = dnsRules.getJSONObject(0)
        assertEquals("reject", rule.getString("action"))
        val sets = rule.getJSONArray("rule_set")
        assertEquals(1, sets.length())
        assertEquals(SingBoxConfigBuilder.ADS_RULE_SET_TAG, sets.getString(0))
    }

    @Test
    fun on_addsExactlyTheRuleSetAndOneRule() {
        val off = JSONObject(build(blockAds = false, withLists = true))
        val on = JSONObject(build(blockAds = true, withLists = true))

        val onRoute = on.getJSONObject("route")
        val offRoute = off.getJSONObject("route")

        // Exactly one inline rule-set, tagged ads-block, carrying the lists.
        val ruleSets = onRoute.optJSONArray("rule_set") ?: JSONArray()
        assertEquals(1, ruleSets.length())
        val ruleSet = ruleSets.getJSONObject(0)
        assertEquals(SingBoxConfigBuilder.ADS_RULE_SET_TAG, ruleSet.getString("tag"))
        assertEquals("inline", ruleSet.getString("type"))
        val headless = ruleSet.getJSONArray("rules").getJSONObject(0)
        assertEquals(2, headless.getJSONArray("domain_suffix").length())
        assertEquals(1, headless.getJSONArray("domain").length())
        assertEquals(1, headless.getJSONArray("domain_regex").length())
        assertEquals(1, headless.getJSONArray("domain_keyword").length())

        // Exactly one extra route rule: the reject referencing the set.
        val onRules = onRoute.getJSONArray("rules")
        val offRules = offRoute.getJSONArray("rules")
        assertEquals(offRules.length() + 1, onRules.length())

        // Remove the three additions; the trees must be identical.
        on.getJSONObject("dns").remove("rules")
        onRoute.remove("rule_set")
        val kept = JSONArray()
        var removed = 0
        for (i in 0 until onRules.length()) {
            val rule = onRules.getJSONObject(i)
            if (rule.optString("action") == "reject" && rule.has("rule_set")) {
                removed++
            } else {
                kept.put(rule)
            }
        }
        assertEquals(1, removed)
        onRoute.put("rules", kept)
        assertEquals(canonical(off), canonical(on))
    }

    @Test
    fun on_withEmptyLists_addsNothing() {
        val empty = AdBlockLists(emptyList(), emptyList(), emptyList(), emptyList())
        val on = SingBoxConfigBuilder.build(
            primaryCandidate = candidate,
            settings = settings(blockAds = true),
            bearerToken = "token",
            appPackageName = "com.veloracode.foxyvpn",
            controllerSecret = "secret",
            adBlockLists = empty,
        )
        assertEquals(build(blockAds = false, withLists = false), on)
    }
}
