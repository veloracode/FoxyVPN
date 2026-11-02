package com.vauth.foxyvpn.data.model

import java.util.UUID

const val DEFAULT_ACCOUNT_QUOTA_BYTES: Long = 50L * 1024L * 1024L * 1024L

/**
 * One Mozilla/FxA account held by the fork's multi-account layer.
 *
 * Upstream v2 is single-account: its TokenStore holds the one ACTIVE
 * session and everything (sign-in, Guardian, the VPN service) reads
 * that session. This list is the fork's layer around it — every stored
 * account's credentials live here, and the active account's credentials
 * are additionally installed in the upstream TokenStore (see
 * AccountRepository). Switching accounts swaps which entry is
 * installed; the connection core is never touched.
 */
data class VpnAccount(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val auth: RuntimeAuth,
    val isEnabled: Boolean = true,
    val addedAtEpochSeconds: Long = System.currentTimeMillis() / 1000,
    val quotaMax: Long = DEFAULT_ACCOUNT_QUOTA_BYTES,
    val quotaRemaining: Long? = null,
    val uid: String? = null,
    val lastError: String? = null,
)
