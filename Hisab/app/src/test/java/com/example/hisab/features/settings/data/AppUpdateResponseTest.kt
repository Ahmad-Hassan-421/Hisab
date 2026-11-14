package com.example.hisab.features.settings.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateResponseTest {
    private val release = AppUpdateResponse(12, "1.2.0", "Changes",
        "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v1.2.0/hisab-v1.2.0.apk",
        4096, "a".repeat(64))

    @Test
    fun usesTheActualAndroidVersionCodeForUpdateEligibility() {
        val verified = checkNotNull(release.validate(null))
        assertTrue(verified.isNewerThan(11))
        assertFalse(verified.isNewerThan(12))
        assertFalse(verified.isNewerThan(13))
    }

    @Test
    fun refusesIncompleteMetadataOrPackagesFromADifferentLocation() {
        assertNull(release.copy(sha256 = null).validate(null))
        assertNull(release.copy(sizeBytes = -1).validate(null))
        assertNull(release.copy(versionCode = 0).validate(null))
        assertNull(release.copy(versionName = "1.2.0-rc1").validate(null))
        assertNull(release.copy(downloadUrl = release.downloadUrl?.replace("Hisab/", "AnotherApp/")).validate(null))
        assertNull(release.copy(downloadUrl = release.downloadUrl + "?extra=1").validate(null))
        val local = release.copy(downloadUrl = "http://127.0.0.1:18093/test.apk")
        assertNull(local.validate(null))
        assertNull(local.validate("http://127.0.0.1:18094/"))
        assertTrue(local.validate("http://127.0.0.1:18093/") != null)
    }
}
