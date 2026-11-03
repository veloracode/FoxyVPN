package com.vauth.foxyvpn.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Clears the FxA web state: cookies, plus the site storage of the FxA
 * origins (FxA keeps most of its session state in localStorage, so
 * the storage delete matters more than the cookie clear).
 *
 * The WebView must never hold a live FxA web session at rest (review
 * decision 2026-11-02): a persisted web session makes a later browser
 * sign-in silently re-authenticate the SAME account instead of asking
 * for credentials — the exact failure the add-account flow exists to
 * avoid. The app's own sessions live in the TokenStore, never in
 * WebView state, so clearing cannot disturb a signed-in account; the
 * only cost is one Fastly re-solve on the next browser flow.
 *
 * [onDone] fires after the cookie store reports the removal and has
 * been flushed, so a caller that opens a browser next is guaranteed
 * the wipe has landed before the first load.
 */
fun clearFxAWebState(onDone: () -> Unit = {}) {
    runCatching { WebStorage.getInstance().deleteOrigin("https://accounts.firefox.com") }
    runCatching { WebStorage.getInstance().deleteOrigin("https://oauth.accounts.firefox.com") }
    val manager = runCatching { CookieManager.getInstance() }.getOrNull()
    if (manager == null) {
        onDone()
        return
    }
    manager.removeAllCookies {
        runCatching { manager.flush() }
        onDone()
    }
}

/**
 * Whether an FxA web session plausibly survived — cookies present for
 * the FxA origin (FxA sets cookies as its sign-in flow progresses,
 * and the wipe clears cookies and site storage together, so the two
 * stores move as one). Used by the add-account flow to decide between
 * resuming an interrupted sign-in and wiping for a fresh one (B2).
 * A false negative costs one fresh sign-in; a false positive just
 * lands on FxA's own sign-in page — both are safe, so the check stays
 * cheap and best-effort.
 */
fun fxAWebSessionAlive(): Boolean {
    val cookies = runCatching {
        CookieManager.getInstance().getCookie("https://accounts.firefox.com")
    }.getOrNull()
    return !cookies.isNullOrBlank()
}
