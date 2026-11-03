package com.vauth.foxyvpn.data

import android.content.Context
import org.json.JSONObject

/**
 * The bundled ad-domain lists behind the Block ads toggle (B2).
 *
 * Provenance: generated at BUILD time from the v2fly
 * domain-list-community data files (MIT License) at a pinned commit —
 * geosite:category-ads-all with its includes resolved exactly like the
 * dlc builder (see tools/build_ads_list.py; the workflow verifies the
 * generated file's sha256). Plain dlc entries are domain-suffix
 * matches; full:/regexp:/keyword: entries map to the same-named
 * sing-box headless-rule fields. The JSON ships as the app asset
 * `ads_rules.json` and is parsed lazily, only when the toggle is on.
 *
 * Loading is fail-open BY DESIGN: if the asset is missing or broken,
 * [load] returns null and the config is built without the ad-block
 * rule — a broken ad list must never break the VPN itself.
 */
data class AdBlockLists(
    val domainSuffix: List<String>,
    val domain: List<String>,
    val domainRegex: List<String>,
    val domainKeyword: List<String>,
) {
    val isEmpty: Boolean
        get() = domainSuffix.isEmpty() && domain.isEmpty() && domainRegex.isEmpty() && domainKeyword.isEmpty()

    companion object {
        const val ASSET_NAME = "ads_rules.json"

        @Volatile private var cached: AdBlockLists? = null

        fun load(context: Context): AdBlockLists? {
            cached?.let { return it }
            val parsed = runCatching {
                val text = context.applicationContext.assets.open(ASSET_NAME)
                    .bufferedReader().use { it.readText() }
                parse(text)
            }.getOrNull()
            if (parsed != null) cached = parsed
            return parsed
        }

        /** Pure parse (unit-testable); returns null on malformed input. */
        fun parse(json: String): AdBlockLists? = runCatching {
            val root = JSONObject(json)
            fun list(key: String): List<String> {
                val array = root.optJSONArray(key) ?: return emptyList()
                return List(array.length()) { array.getString(it) }
            }
            AdBlockLists(
                domainSuffix = list("domain_suffix"),
                domain = list("domain"),
                domainRegex = list("domain_regex"),
                domainKeyword = list("domain_keyword"),
            )
        }.getOrNull()
    }
}
