package com.example.hisab.util

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class UpdateDownloadStatusTest {
    @get:Rule val files = TemporaryFolder()

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
    fun updateChecksumRejectsAChangedFileOfTheSameSize() {
        val apk = files.newFile("candidate.apk")
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        apk.writeText("abc")
        assertTrue(ApkUpdateManager.hasExpectedChecksum(apk, expected))
        apk.writeText("abd")
        assertFalse(ApkUpdateManager.hasExpectedChecksum(apk, expected))
    }
}
