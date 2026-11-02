package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount

/*
 * Pure (no Android) decision logic for registering the LIVE TokenStore
 * session in the fork's account list. AccountRepository executes the
 * plan; unit tests exercise this file directly.
 */

/** Email of the placeholder entry that wraps a session of unknown identity. */
internal const val PLACEHOLDER_EMAIL = "Primary Account"

internal fun isPlaceholderEmail(email: String): Boolean =
    email == PLACEHOLDER_EMAIL || email.startsWith("$PLACEHOLDER_EMAIL (")

/** Same session lineage: the very same token, or the same refresh token. */
internal fun sameLineage(a: RuntimeAuth, b: RuntimeAuth): Boolean =
    a.accessToken == b.accessToken ||
        (a.refreshToken != null && a.refreshToken == b.refreshToken)

/** Proven to be DIFFERENT sessions (both refresh tokens known and unequal). */
internal fun provenDifferent(a: RuntimeAuth, b: RuntimeAuth): Boolean =
    a.accessToken != b.accessToken &&
        a.refreshToken != null &&
        b.refreshToken != null &&
        a.refreshToken != b.refreshToken

/** "Primary Account", then "Primary Account (2)", ... never colliding. */
internal fun nextPlaceholderEmail(accounts: List<VpnAccount>): String {
    if (accounts.none { it.email.equals(PLACEHOLDER_EMAIL, ignoreCase = true) }) {
        return PLACEHOLDER_EMAIL
    }
    var n = 2
    while (accounts.any { it.email.equals("$PLACEHOLDER_EMAIL ($n)", ignoreCase = true) }) n++
    return "$PLACEHOLDER_EMAIL ($n)"
}

/**
 * What to write: [entry] is upserted BY ID (never by email) and becomes
 * the active account; [removeIds] are placeholder duplicates of the very
 * same live session, folded into [entry] (their credentials ARE [entry]'s).
 */
internal data class RegistrationPlan(
    val entry: VpnAccount,
    val removeIds: List<String>,
    val isCreate: Boolean,
)

/**
 * Decides how the live [session] maps onto the account list. Returns null
 * when the list is already aligned (nothing to write).
 *
 * Rules:
 *  - LIVE SESSION WINS: the TokenStore session is what the VPN and every
 *    upstream reader use, so it always ends up owned by exactly one entry
 *    and that entry is the active one — stale migrated entries never block it.
 *  - An entry is reused only on positive evidence: a known identity
 *    (uid, then non-placeholder email) or session lineage. Without such
 *    evidence a NEW entry is created (placeholder identity if [email] is
 *    unknown). An existing entry is never overwritten with a session that
 *    has no identity/lineage link to it.
 *  - Nothing is ever deleted except placeholder entries holding this very
 *    session (same lineage) once the session is owned by a real entry.
 */
internal fun planLiveSession(
    accounts: List<VpnAccount>,
    activeId: String?,
    session: RuntimeAuth,
    email: String?,
    uid: String?,
    newId: () -> String = { java.util.UUID.randomUUID().toString() },
): RegistrationPlan? {
    val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }
    val cleanUid = uid?.trim()?.takeIf { it.isNotEmpty() }

    val byIdentity: VpnAccount? =
        (if (cleanUid != null) accounts.firstOrNull { it.uid == cleanUid } else null)
            ?: (if (cleanEmail != null) {
                accounts.firstOrNull {
                    !isPlaceholderEmail(it.email) && it.email.equals(cleanEmail, ignoreCase = true)
                }
            } else {
                null
            })

    val byLineage: VpnAccount? =
        accounts.firstOrNull { it.id == activeId && sameLineage(it.auth, session) }
            ?: accounts.firstOrNull { sameLineage(it.auth, session) }

    val target = byIdentity ?: byLineage

    if (target == null) {
        val entry = VpnAccount(
            id = newId(),
            email = cleanEmail ?: nextPlaceholderEmail(accounts),
            auth = session,
            uid = cleanUid,
        )
        return RegistrationPlan(entry, removeIds = emptyList(), isCreate = true)
    }

    val duplicatePlaceholders = accounts.filter {
        it.id != target.id && isPlaceholderEmail(it.email) && sameLineage(it.auth, session)
    }
    val updated = target.copy(
        email = cleanEmail ?: target.email,
        uid = cleanUid ?: target.uid,
        auth = session,
        lastError = null,
    )
    if (updated == target && activeId == target.id && duplicatePlaceholders.isEmpty()) {
        return null
    }
    return RegistrationPlan(updated, duplicatePlaceholders.map { it.id }, isCreate = false)
}
