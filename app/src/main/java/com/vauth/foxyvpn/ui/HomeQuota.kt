package com.vauth.foxyvpn.ui

import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.data.model.VpnAccount

/*
 * Pure (no Android/Compose) quota resolution for the Home quota row.
 *
 * Upstream's Home reads the settings snapshot unconditionally, because the
 * connect service only ever runs with the active session, so the snapshot
 * is the active session's quota by construction. The fork's stamp
 * (quotaAccountId) is a REFINEMENT only: it can demote the snapshot when it
 * PROVES the values belong to a different, still-existing account — it can
 * never blank the row.
 */

internal data class HomeQuota(
    val remaining: Long?,
    val max: Long?,
    val unlimited: Boolean,
    /** When the shown value was fetched (0 = unknown age). */
    val fetchedAtEpochSeconds: Long,
    /** True when the value is this device's usage estimate, not the server's. */
    val isEstimate: Boolean,
)

internal val NO_HOME_QUOTA = HomeQuota(null, null, false, 0L, false)

internal fun resolveHomeQuota(
    snapshot: SettingsStore.AccountSnapshot,
    activeAccount: VpnAccount?,
    knownAccountIds: Set<String>,
    usedBytesOfActive: Long,
): HomeQuota {
    val snapshotHasData =
        snapshot.unlimited || snapshot.quotaRemaining != null || snapshot.quotaMax != null
    val stamp = snapshot.quotaAccountId
    // Proven foreign = stamped with ANOTHER account that still exists. A
    // stamp pointing at nothing (a folded placeholder, a removed account)
    // or no stamp at all is "unattributed" and trusted like upstream does.
    val provenForeign =
        activeAccount != null &&
            stamp != null &&
            stamp != activeAccount.id &&
            stamp in knownAccountIds

    if (snapshotHasData && !provenForeign) {
        return HomeQuota(
            remaining = snapshot.quotaRemaining,
            max = snapshot.quotaMax,
            unlimited = snapshot.unlimited,
            fetchedAtEpochSeconds = snapshot.quotaFetchedAtEpochSeconds,
            isEstimate = false,
        )
    }
    if (activeAccount != null) {
        val serverKnown = activeAccount.quotaRemaining != null
        return HomeQuota(
            remaining = activeAccount.quotaRemaining
                ?: (activeAccount.quotaMax - usedBytesOfActive).coerceAtLeast(0L),
            max = activeAccount.quotaMax,
            unlimited = false,
            fetchedAtEpochSeconds = activeAccount.quotaFetchedAtEpochSeconds,
            isEstimate = !serverKnown,
        )
    }
    return NO_HOME_QUOTA
}

/** Values newer than this show a clock time; older ones show their age. */
internal const val QUOTA_FRESH_SECONDS: Long = 15L * 60L

internal sealed interface QuotaCaption {
    object None : QuotaCaption
    object Estimate : QuotaCaption

    /** A value is shown but nothing says when it was fetched. */
    object LastKnown : QuotaCaption
    data class UpdatedAt(val epochSeconds: Long) : QuotaCaption
    data class Stale(val ageSeconds: Long) : QuotaCaption
}

internal fun quotaCaption(
    hasValue: Boolean,
    isEstimate: Boolean,
    fetchedAtEpochSeconds: Long,
    nowEpochSeconds: Long,
): QuotaCaption {
    if (!hasValue) return QuotaCaption.None
    if (isEstimate) return QuotaCaption.Estimate
    if (fetchedAtEpochSeconds <= 0L) return QuotaCaption.LastKnown
    val age = (nowEpochSeconds - fetchedAtEpochSeconds).coerceAtLeast(0L)
    return if (age < QUOTA_FRESH_SECONDS) {
        QuotaCaption.UpdatedAt(fetchedAtEpochSeconds)
    } else {
        QuotaCaption.Stale(age)
    }
}

/** "5 min", "3 h", "4 d" (the caller appends " ago"). */
internal fun formatQuotaAge(ageSeconds: Long): String {
    val minutes = ageSeconds / 60L
    return when {
        minutes < 60L -> "${minutes.coerceAtLeast(1L)} min"
        minutes < 48L * 60L -> "${minutes / 60L} h"
        else -> "${minutes / (24L * 60L)} d"
    }
}
