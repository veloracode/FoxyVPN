package com.vauth.foxyvpn.data

import com.vauth.foxyvpn.data.model.RuntimeAuth
import com.vauth.foxyvpn.data.model.VpnAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3: seeded states — v1-era entries migrated from older generations (stale
 * tokens) plus a fresh LIVE session — must still register the live session.
 */
class SessionRegistrationTest {

    private fun auth(access: String, refresh: String?) = RuntimeAuth(access, refresh, 4_000_000_000L)

    private val staleA = VpnAccount(id = "A", email = "alice@example.org", auth = auth("a-old", "ra-old"))
    private val staleB = VpnAccount(id = "B", email = "bob@example.org", auth = auth("b-old", "rb-old"))
    private val live = auth("live-access", "live-refresh")

    private fun plan(
        accounts: List<VpnAccount>,
        activeId: String? = null,
        session: RuntimeAuth = live,
        email: String? = null,
        uid: String? = null,
    ) = planLiveSession(accounts, activeId, session, email, uid, newId = { "NEW" })

    // --- The exact D3 state: migrated stale entries, no active, fresh live session ---

    @Test
    fun staleMigratedEntries_doNotBlockRegisteringTheLiveSession_withoutAnyNetwork() {
        val p = plan(listOf(staleA, staleB), activeId = null)
        assertNotNull(p)
        p!!
        assertTrue(p.isCreate)
        assertEquals("NEW", p.entry.id)
        assertEquals(live, p.entry.auth)
        assertEquals(PLACEHOLDER_EMAIL, p.entry.email) // placeholder identity is enough
        assertTrue(p.removeIds.isEmpty())
        // The stale entries are not part of the write at all: never overwritten.
    }

    @Test
    fun foreignSession_neverOverwritesTheActiveEntry() {
        val p = plan(listOf(staleA, staleB), activeId = "A")
        p!!
        assertTrue(p.isCreate)
        assertTrue(p.entry.id != "A" && p.entry.id != "B")
    }

    @Test
    fun placeholder_isFoldedIntoTheStaleEntryThatOwnsTheIdentity() {
        val placeholder = VpnAccount(id = "P", email = PLACEHOLDER_EMAIL, auth = live)
        val p = plan(listOf(staleA, staleB, placeholder), activeId = "P", email = "ALICE@example.org", uid = "uid-a")!!
        assertFalse(p.isCreate)
        assertEquals("A", p.entry.id)
        assertEquals(live, p.entry.auth) // live session wins over the stale tokens
        assertEquals("uid-a", p.entry.uid)
        assertEquals(listOf("P"), p.removeIds)
    }

    @Test
    fun identityKnownButNoEntryOwnsIt_createsARealEntry() {
        val p = plan(listOf(staleA), activeId = null, email = "carol@example.org", uid = "uid-c")!!
        assertTrue(p.isCreate)
        assertEquals("carol@example.org", p.entry.email)
        assertEquals("uid-c", p.entry.uid)
    }

    @Test
    fun uidMatchBeatsEmailMatch() {
        val a = staleA.copy(uid = "uid-x")
        val b = staleB.copy(uid = null)
        val p = plan(listOf(a, b), email = "bob@example.org", uid = "uid-x")!!
        assertEquals("A", p.entry.id)
    }

    // --- Refresh / idempotence / lineage protections ---

    @Test
    fun tokenRefreshOfTheActiveEntry_updatesInPlace() {
        val active = VpnAccount(id = "L", email = "me@example.org", auth = auth("old-access", "live-refresh"))
        val p = plan(listOf(active), activeId = "L")!!
        assertFalse(p.isCreate)
        assertEquals("L", p.entry.id)
        assertEquals(live, p.entry.auth)
    }

    @Test
    fun alreadyAligned_isANoOp_idempotent() {
        val active = VpnAccount(id = "L", email = "me@example.org", auth = live, uid = "u")
        assertNull(plan(listOf(active), activeId = "L"))
        assertNull(plan(listOf(active), activeId = "L", email = "me@example.org", uid = "u"))
    }

    @Test
    fun crashAfterEntryWritten_butBeforeActiveMarker_isRepairedByMarkingActive() {
        val written = VpnAccount(id = "L", email = PLACEHOLDER_EMAIL, auth = live)
        val p = plan(listOf(staleA, written), activeId = null)!!
        assertFalse(p.isCreate) // no duplicate
        assertEquals("L", p.entry.id)
    }

    @Test
    fun secondPlaceholder_getsAUniqueEmail_andNeverEvictsAnExistingOne() {
        val existing = VpnAccount(id = "P1", email = PLACEHOLDER_EMAIL, auth = auth("x", "rx"))
        val p = plan(listOf(existing), activeId = null)!!
        assertTrue(p.isCreate)
        assertEquals("$PLACEHOLDER_EMAIL (2)", p.entry.email)
        assertTrue(isPlaceholderEmail(p.entry.email))
    }

    @Test
    fun lineageHelpers() {
        assertTrue(sameLineage(auth("a1", "r"), auth("a2", "r")))
        assertFalse(sameLineage(auth("a1", null), auth("a2", null)))
        assertTrue(provenDifferent(auth("a1", "r1"), auth("a2", "r2")))
        assertFalse(provenDifferent(auth("a1", null), auth("a2", "r2")))
    }
}
