package com.hisab.server.features.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseManifestTest {
    private val manifest = ReleaseManifest("1.10.0", 12, "com.example.hisab", 1000, "a".repeat(64))
    private val path = "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v1.10.0/"
    private val expected = GitHubAsset("hisab-v1.10.0.apk", path + "hisab-v1.10.0.apk", 1000, "sha256:${manifest.sha256}")
    private val other = GitHubAsset("internal-debug.apk", path + "internal-debug.apk", 1000)
    private val release = GitHubRelease("v1.10.0", "Notes", listOf(other, expected))

    @Test
    fun selectsTheNamedArtifactAndPublishesTheManifestCode() {
        val update = checkNotNull(verifiedRelease(release, manifest))
        assertEquals(12, update.versionCode)
        assertEquals("1.10.0", update.versionName)
        assertEquals(expected.browserDownloadUrl, update.downloadUrl)
        assertEquals(manifest.sha256, update.sha256)
    }

    @Test
    fun rejectsInconsistentOrUntrustedGitHubAssetMetadata() {
        assertNull(verifiedRelease(release, manifest.copy(sha256 = "0".repeat(64))))
        assertNull(verifiedRelease(release, manifest.copy(applicationId = "com.example.hisab.qa")))
        assertNull(verifiedRelease(release, manifest.copy(sizeBytes = 999)))
        assertNull(verifiedRelease(release, manifest.copy(versionName = "1.10.1")))
        assertNull(verifiedRelease(release.copy(assets = listOf(other)), manifest))
        assertNull(verifiedRelease(release.copy(assets = listOf(expected.copy(browserDownloadUrl = "https://example.invalid/test.apk"))), manifest))
    }
}
