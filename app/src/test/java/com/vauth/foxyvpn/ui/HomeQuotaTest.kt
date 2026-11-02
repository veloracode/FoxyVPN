package com.vauth.foxyvpn.ui

import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D2: the service writes UNSTAMPED quota values; Home must still show them. */
class HomeQuotaTest {

    private val GB = 1024L * 1024L * 1024L

    private fun snapshot(
        remaining: Long? = 12 * GB,
        max: Long? = 50 * GB,
        unlimited: Boolean = false,
        stamp: String? = null,
        fetchedAt: Long = 0L,
    ) = SettingsStore.AccountSnapshot(null, null, remaining, max, unlimited, stamp, fetchedAt)

    private fun account(id: String, quotaRemaining: Long? = null, fetchedAt: Long = 0L) =
        VpnAccount(
            id = id,
            email = "$id@example.org",
            auth = RuntimeAuth("a", "r", 0L),
            quotaRemaining = quotaRemaining,
            quotaFetchedAtEpochSeconds = fetchedAt,
        )

    @Test
    fun unstampedServiceValues_areShown_whenTheActiveAccountIsRegistered() {
        val active = account("L")
        val q = resolveHomeQuota(snapshot(stamp = null, fetchedAt = 0L), active, setOf("L"), 0L)
        assertEquals(12 * GB, q.remaining)
        assertEquals(50 * GB, q.max)
        assertFalse(q.isEstimate)
    }

    @Test
    fun unstampedServiceValues_areShown_evenWithNoAccountEntryAtAll() { // the D3 state
        val q = resolveHomeQuota(snapshot(), null, emptySet(), 0L)
        assertEquals(12 * GB, q.remaining)
    }

    @Test
    fun matchingStamp_isShownWithItsFetchTime() {
        val q = resolveHomeQuota(snapshot(stamp = "L", fetchedAt = 1_000L), account("L"), setOf("L"), 0L)
        assertEquals(12 * GB, q.remaining)
        assertEquals(1_000L, q.fetchedAtEpochSeconds)
    }

    @Test
    fun stampOfAnotherExistingAccount_isDemoted_toTheActiveEntrysOwnValue() {
        val q = resolveHomeQuota(
            snapshot(stamp = "OLD"),
            account("L", quotaRemaining = 3 * GB, fetchedAt = 500L),
            setOf("L", "OLD"),
            0L,
        )
        assertEquals(3 * GB, q.remaining)
        assertFalse(q.isEstimate)
    }

    @Test
    fun orphanStamp_pointingAtNoExistingAccount_isTreatedAsUnattributed() {
        // e.g. a placeholder that was folded away after the service stamped it.
        val q = resolveHomeQuota(snapshot(stamp = "GONE"), account("L"), setOf("L"), 0L)
        assertEquals(12 * GB, q.remaining)
    }

    @Test
    fun emptySnapshot_fallsBackToTheEntry_estimateWhenNoServerValue() {
        val q = resolveHomeQuota(snapshot(null, null), account("L"), setOf("L"), 5 * GB)
        assertTrue(q.isEstimate)
        assertEquals(DEFAULT_50GB - 5 * GB, q.remaining)
    }

    @Test
    fun unlimitedSnapshot_isShown() {
        val q = resolveHomeQuota(snapshot(null, null, unlimited = true), null, emptySet(), 0L)
        assertTrue(q.unlimited)
    }

    @Test
    fun nothingKnownAnywhere_isTheOnlyBlankCase() {
        assertEquals(NO_HOME_QUOTA, resolveHomeQuota(snapshot(null, null), null, emptySet(), 0L))
    }

    // --- caption honesty ---

    @Test
    fun caption_unknownAge_saysLastKnown_neverFakesATime() {
        assertEquals(QuotaCaption.LastKnown, quotaCaption(true, false, 0L, 1_000_000L))
    }

    @Test
    fun caption_freshShowsClockTime_staleShowsAge() {
        val now = 1_000_000L
        assertEquals(QuotaCaption.UpdatedAt(now - 60), quotaCaption(true, false, now - 60, now))
        assertEquals(QuotaCaption.Stale(3 * 3600L), quotaCaption(true, false, now - 3 * 3600L, now))
    }

    @Test
    fun caption_estimateAndNone() {
        assertEquals(QuotaCaption.Estimate, quotaCaption(true, true, 123L, 456L))
        assertEquals(QuotaCaption.None, quotaCaption(false, false, 123L, 456L))
    }

    @Test
    fun formatAge() {
        assertEquals("1 min", formatQuotaAge(10))
        assertEquals("45 min", formatQuotaAge(45 * 60L))
        assertEquals("3 h", formatQuotaAge(3 * 3600L))
        assertEquals("3 d", formatQuotaAge(3 * 86_400L))
    }

    private val DEFAULT_50GB = 50L * 1024L * 1024L * 1024L
}
