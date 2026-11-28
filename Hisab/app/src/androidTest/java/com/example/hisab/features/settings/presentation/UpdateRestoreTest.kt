package com.example.hisab.features.settings.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.AppUpdateResponse
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.UpdateDownloadStatus
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class UpdateRestoreTest {
    @Test fun restoredDownloadRechecksIntegrityAndCleansCorruptFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = context.getSharedPreferences("app_update_download", Context.MODE_PRIVATE)
        val apk = File(context.filesDir, "updates/hisab-update.apk").apply { parentFile?.mkdirs(); writeText("bad") }
        val metadata = AppUpdateResponse(BuildConfig.VERSION_CODE + 1, "1.2.2", "QA",
            "${BuildConfig.API_BASE_URL}candidate.apk", 3, "a".repeat(64))
        saved.edit().putString("release", Gson().toJson(metadata)).putBoolean("ready", true).commit()
        try {
            val manager = withContext(Dispatchers.Main) { ApkUpdateManager(context, FailureReporter({ true }, {})) }
            val result = withTimeout(10_000) { manager.status.first { it !is UpdateDownloadStatus.Preparing } }
            assertTrue(result is UpdateDownloadStatus.Error)
            assertFalse(apk.exists()); assertTrue(saved.all.isEmpty())
        } finally { saved.edit().clear().commit(); apk.delete() }
    }
}
