package com.vauth.foxyvpn.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag-comparison behavior around the v1 naming reset (tag "v1",
 * versionName "1.0"): single-segment tags must parse so a v1 install
 * is never offered its own release as an update.
 */
class UpdatePolicyTest {

    @Test
    fun v1TagIsNotNewerThanV1Install() {
        assertTrue(releaseTagIsNotNewer("v1", "1.0"))
    }

    @Test
    fun v1TagIsNotNewerThanPreResetInstallByName() {
        // Name-wise 1.0 < 1.1.6; the in-app gate defers and those
        // installs take the release page (Android's real ordering is
        // the monotonic versionCode, 53 > 52, which the identity
        // check enforces at install time).
        assertTrue(releaseTagIsNotNewer("v1", "1.1.6"))
    }

    @Test
    fun futureTagsStillCompare() {
        assertFalse(releaseTagIsNotNewer("v2", "1.0"))
        assertFalse(releaseTagIsNotNewer("v1.1.7", "1.1.6"))
        assertTrue(releaseTagIsNotNewer("v1.1.6", "1.1.6"))
        assertTrue(releaseTagIsNotNewer("v1.0.9", "1.1.6"))
    }
}
