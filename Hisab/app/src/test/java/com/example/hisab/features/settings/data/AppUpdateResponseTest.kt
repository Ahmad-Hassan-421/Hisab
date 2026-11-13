package com.example.hisab.features.settings.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateResponseTest {
    @Test
    fun comparesReleaseVersionsByNumericComponent() {
        assertFalse(release("v1.1.0", 110).isNewerThan("1.1.0", 2))
        assertFalse(release("v1.9.9", 199).isNewerThan("1.10.0", 3))
        assertTrue(release("v1.10.0", 1100).isNewerThan("1.9.9", 2))
        assertFalse(release("v1.1", 11).isNewerThan("1.1.0", 2))
        assertTrue(release("v1.1.1+build.4", 1114).isNewerThan("1.1.0", 2))
    }

    @Test
    fun retainsExplicitNumericBuildTagsAndIgnoresUnrecognizedTags() {
        assertTrue(release("v3", 3).isNewerThan("1.1.0", 2))
        assertFalse(release("v2", 2).isNewerThan("1.1.0", 2))
        assertFalse(release("v1.2.0-rc1", 1201).isNewerThan("1.1.0", 2))
        assertFalse(release("release-2026-11", 202611).isNewerThan("1.1.0", 2))
    }

    private fun release(tag: String, code: Int) = AppUpdateResponse(code, tag, "", "")
}
