package com.example.hisab.features.settings.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.AppUpdateResponse
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.UpdateDownloadStatus
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Requires a separately signed, newer QA APK served by the local device-test fixture. */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadDeviceTest {
    @Test fun downloadsVerifiesAndRestoresACompatibleNewerApk() = runBlocking {
        assumeTrue("Run with the local newer-APK fixture enabled", InstrumentationRegistry.getArguments().getString("updateFixture") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = withContext(Dispatchers.IO) {
            val connection = URL("${BuildConfig.API_BASE_URL}api/releases/latest").openConnection() as HttpURLConnection
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            try { connection.inputStream.reader().use { Gson().fromJson(it, AppUpdateResponse::class.java) } }
            finally { connection.disconnect() }
        }
        val release = checkNotNull(metadata.validate(BuildConfig.API_BASE_URL))
        assertTrue(release.isNewerThan(BuildConfig.VERSION_CODE))
        val failures = FailureReporter({ true }, {})
        val manager = withContext(Dispatchers.Main) { ApkUpdateManager(context, failures) }
        withTimeout(15_000) { manager.status.first { it !is UpdateDownloadStatus.Preparing } }
        withContext(Dispatchers.Main) { manager.startDownload(release) }
        val started = withTimeout(60_000) { manager.status.first {
            it is UpdateDownloadStatus.Downloading && it.bytes >= 65_536 || it is UpdateDownloadStatus.Error || it is UpdateDownloadStatus.Ready
        } }
        assertTrue("Expected an active throttled fixture download; received $started", started is UpdateDownloadStatus.Downloading)
        withContext(Dispatchers.Main) { manager.cancel() }
        assertTrue(manager.status.value is UpdateDownloadStatus.Cancelled)
        assertTrue(context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE).all.isEmpty())
        withContext(Dispatchers.Main) { manager.startDownload(release) }
        val result = withTimeout(120_000) { manager.status.first { it is UpdateDownloadStatus.Ready || it is UpdateDownloadStatus.Error } }
        assertTrue("Expected verified APK; received $result", result is UpdateDownloadStatus.Ready)
        val ready = result as UpdateDownloadStatus.Ready
        assertEquals(release.sizeBytes, ready.apk.length())
        assertTrue(ApkUpdateManager.hasExpectedChecksum(ready.apk, release.sha256))
        // A fresh manager must validate saved metadata, size, checksum, package, version and signer again.
        val restored = withContext(Dispatchers.Main) { ApkUpdateManager(context, failures) }
        val afterRestore = withTimeout(15_000) { restored.status.first { it !is UpdateDownloadStatus.Preparing } }
        assertTrue("Restoration failed: $afterRestore", afterRestore is UpdateDownloadStatus.Ready)
        // Leave the verified file for a separate explicit installer handoff; never approve installation here.
    }
}
