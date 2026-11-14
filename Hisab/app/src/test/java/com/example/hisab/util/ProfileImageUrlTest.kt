package com.example.hisab.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileImageUrlTest {
    @Test
    fun usesLocalFallbackForMissingLegacyDefaultAndNonNetworkImages() {
        listOf(
            null, "", "   ", Constants.DEFAULT_GOOGLE_AVATAR_URL, "/static/default_profile.png",
            "https://example.invalid/static/default_profile.png", "content://example/avatar", "file:///tmp/avatar",
            "/static/%2e%2e/profile.png", "https://user:password@example.invalid/profile.png"
        ).forEach { assertNull(it, Constants.profileImageUrl(it)) }
    }

    @Test
    fun acceptsPortableUploadsAndExistingNetworkImages() {
        assertEquals(Constants.API_BASE_URL.trimEnd('/') + "/static/uploads/profile.png",
            Constants.profileImageUrl("/static/uploads/profile.png"))
        assertEquals("https://example.invalid/profile.png", Constants.profileImageUrl("https://example.invalid/profile.png"))
    }
}
