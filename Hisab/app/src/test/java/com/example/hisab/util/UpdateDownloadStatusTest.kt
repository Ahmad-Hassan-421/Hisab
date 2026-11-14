package com.example.hisab.util

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadStatusTest {
    @Test
    fun progressIsIndeterminateUntilThereIsARealTotal() {
        assertNull(UpdateDownloadStatus.Downloading(1024, null).fraction)
        assertNull(UpdateDownloadStatus.Downloading(1024, -1).fraction)
        assertEquals(0.25f, UpdateDownloadStatus.Downloading(1024, 4096).fraction)
        assertEquals(1f, UpdateDownloadStatus.Downloading(4097, 4096).fraction)
    }

    @Test
    fun downloadFailuresGiveAnActionSpecificToTheReason() {
        assertTrue(ApkUpdateManager.downloadFailureMessage(404).contains("unavailable"))
        assertTrue(ApkUpdateManager.downloadFailureMessage(DownloadManager.ERROR_INSUFFICIENT_SPACE).contains("storage"))
        assertTrue(ApkUpdateManager.downloadFailureMessage(DownloadManager.ERROR_CANNOT_RESUME).contains("interrupted"))
        assertTrue(ApkUpdateManager.pausedMessage(DownloadManager.PAUSED_WAITING_FOR_NETWORK).contains("connection"))
    }

    @Test
    fun productionRequiresHttpsAndOnlyDebugAllowsLoopbackHttp() {
        assertTrue(ApkUpdateManager.allowedDownloadUrl("https://example.invalid/release.apk", false))
        assertTrue(ApkUpdateManager.allowedDownloadUrl("http://127.0.0.1:18093/test.apk", true))
        assertFalse(ApkUpdateManager.allowedDownloadUrl("http://127.0.0.1:18093/test.apk", false))
        assertFalse(ApkUpdateManager.allowedDownloadUrl("http://example.invalid/test.apk", true))
        assertFalse(ApkUpdateManager.allowedDownloadUrl("file:///test.apk", true))
    }
}
