package com.vauth.foxyvpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase B2 regression gate for the add-account state-loss defect:
 * leaving the app mid-sign-in (to fetch the email code) and returning
 * after a process death must RESUME on the live FxA session instead
 * of wiping it and stranding the user at the start of the flow.
 */
class AddAccountFlowStoreTest {

    @Test
    fun decideEntry_truthTable() {
        // No pending record -> always a fresh flow.
        assertEquals(
            AddAccountFlowStore.Entry.FRESH,
            AddAccountFlowStore.decideEntry(null, fxASessionAlive = true),
        )
        assertEquals(
            AddAccountFlowStore.Entry.FRESH,
            AddAccountFlowStore.decideEntry(null, fxASessionAlive = false),
        )
        // Browser stage reached + session survived -> RESUME.
        assertEquals(
            AddAccountFlowStore.Entry.RESUME,
            AddAccountFlowStore.decideEntry(
                AddAccountFlowStore.Stage.BROWSER,
                fxASessionAlive = true,
            ),
        )
        // Browser stage reached but the session is gone -> FRESH
        // (resuming would reopen a dead sign-in page).
        assertEquals(
            AddAccountFlowStore.Entry.FRESH,
            AddAccountFlowStore.decideEntry(
                AddAccountFlowStore.Stage.BROWSER,
                fxASessionAlive = false,
            ),
        )
    }

    @Test
    fun pendingStage_requiresActiveFreshRecord() {
        val now = 1_700_000_000_000L
        // Nothing recorded.
        assertNull(AddAccountFlowStore.pendingStage(FakeSharedPreferences(emptyMap()), now))
        // Recorded but the browser was never opened -> no stage yet.
        assertNull(
            AddAccountFlowStore.pendingStage(
                FakeSharedPreferences(
                    mapOf(
                        "flow_active" to true,
                        "flow_started_at" to now - 60_000L,
                    ),
                ),
                now,
            ),
        )
        // Browser stage, fresh -> BROWSER.
        assertEquals(
            AddAccountFlowStore.Stage.BROWSER,
            AddAccountFlowStore.pendingStage(
                FakeSharedPreferences(
                    mapOf(
                        "flow_active" to true,
                        "flow_stage" to "BROWSER",
                        "flow_started_at" to now - 60_000L,
                    ),
                ),
                now,
            ),
        )
        // Browser stage but older than MAX_AGE -> stale, ignored.
        assertNull(
            AddAccountFlowStore.pendingStage(
                FakeSharedPreferences(
                    mapOf(
                        "flow_active" to true,
                        "flow_stage" to "BROWSER",
                        "flow_started_at" to now - AddAccountFlowStore.MAX_AGE_MS - 1L,
                    ),
                ),
                now,
            ),
        )
        // Inactive record -> ignored even with a stage stored.
        assertNull(
            AddAccountFlowStore.pendingStage(
                FakeSharedPreferences(
                    mapOf(
                        "flow_active" to false,
                        "flow_stage" to "BROWSER",
                        "flow_started_at" to now - 60_000L,
                    ),
                ),
                now,
            ),
        )
    }
}
