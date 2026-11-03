package com.vauth.foxyvpn.ui.bento

/**
 * Pure encode/decode for the `nav_destinations` pref — Phase B1 of
 * the Zed overhaul. Kept free of Compose/Android types so the total
 * decode is unit-testable on the JVM. The keys mirror
 * [FoxyDestination.key] values; Home and Settings are pinned
 * (always present), matching Zed's special-casing of Settings.
 */
object NavDestinationKeys {
    const val HOME = "home"
    const val SERVERS = "servers"
    const val ACCOUNTS = "accounts"
    const val SETTINGS = "settings"

    val DEFAULT: List<String> = listOf(HOME, SERVERS, ACCOUNTS, SETTINGS)
    val KNOWN: Set<String> = DEFAULT.toSet()
    val PINNED: Set<String> = setOf(HOME, SETTINGS)
}

/** Total decode: blank/garbage → default; unknown dropped; pinned forced. */
fun decodeNavKeys(raw: String): List<String> {
    if (raw.isBlank()) return NavDestinationKeys.DEFAULT
    val parsed = ArrayList<String>()
    for (token in raw.split(",")) {
        val key = token.trim()
        if (key in NavDestinationKeys.KNOWN && key !in parsed) parsed += key
    }
    // A pref holding only garbage decodes to the default bar, not to
    // a bar with just the pinned tabs.
    if (parsed.isEmpty()) return NavDestinationKeys.DEFAULT
    if (NavDestinationKeys.HOME !in parsed) parsed.add(0, NavDestinationKeys.HOME)
    if (NavDestinationKeys.SETTINGS !in parsed) parsed += NavDestinationKeys.SETTINGS
    return parsed
}

fun encodeNavKeys(keys: List<String>): String = keys.joinToString(",")
