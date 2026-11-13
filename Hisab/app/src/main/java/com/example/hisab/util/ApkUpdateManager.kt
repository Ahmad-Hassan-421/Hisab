package com.example.hisab.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateDownloadStatus {
    object Idle : UpdateDownloadStatus()
    data class Downloading(val progress: Float, val percentage: Int) : UpdateDownloadStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadStatus()
    data class Error(val message: String) : UpdateDownloadStatus()
}

@Singleton
class ApkUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "ApkUpdateManager"
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<UpdateDownloadStatus>(UpdateDownloadStatus.Idle)
    val status: StateFlow<UpdateDownloadStatus> = _status.asStateFlow()

    private var activeDownloadId: Long? = null
    private var progressJob: Job? = null
    private var receiverRegistered = false
    private var lastTargetFile: File? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(recvContext: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == activeDownloadId) {
                    checkDownloadCompletion(id)
                }
            }
        }
    }

    private fun registerReceiverIfNeeded() {
        if (!receiverRegistered) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(downloadReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    fun startDownload(downloadUrl: String, versionName: String) {
        try {
            registerReceiverIfNeeded()

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val sanitizedVersion = versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "hisab-release-$sanitizedVersion.apk"
            val targetFile = File(downloadsDir, fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            lastTargetFile = targetFile

            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("Hisab Update $versionName")
                setDescription("Downloading latest Hisab APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            _status.value = UpdateDownloadStatus.Downloading(0f, 0)

            progressJob?.cancel()
            progressJob = scope.launch {
                monitorDownloadProgress(downloadId, targetFile)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start download", e)
            _status.value = UpdateDownloadStatus.Error(e.localizedMessage ?: "Failed to start download")
        }
    }

    private suspend fun monitorDownloadProgress(downloadId: Long, targetFile: File) {
        var isRunning = true
        while (isRunning && currentCoroutineContext().isActive) {
            try {
                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val statusCode = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1

                        when (statusCode) {
                            DownloadManager.STATUS_RUNNING -> {
                                val bytesSoFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val bytesSoFar = if (bytesSoFarIdx >= 0) cursor.getLong(bytesSoFarIdx) else 0L
                                val bytesTotal = if (bytesTotalIdx >= 0) cursor.getLong(bytesTotalIdx) else 0L

                                if (bytesTotal > 0L) {
                                    val progress = (bytesSoFar.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
                                    val percentage = (progress * 100).toInt()
                                    _status.value = UpdateDownloadStatus.Downloading(progress, percentage)
                                } else {
                                    _status.value = UpdateDownloadStatus.Downloading(0f, 0)
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isRunning = false
                                _status.value = UpdateDownloadStatus.ReadyToInstall(targetFile)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                isRunning = false
                                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                                _status.value = UpdateDownloadStatus.Error("Download failed with code $reason")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error reading download progress", e)
            }
            delay(400)
        }
    }

    private fun checkDownloadCompletion(downloadId: Long) {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val statusCode = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                    if (statusCode == DownloadManager.STATUS_SUCCESSFUL) {
                        val file = lastTargetFile?.takeIf { it.exists() }
                            ?: run {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                downloadsDir.listFiles { _, name -> name.startsWith("hisab-release-") && name.endsWith(".apk") }
                                    ?.maxByOrNull { it.lastModified() }
                            }
                        if (file != null && file.exists()) {
                            _status.value = UpdateDownloadStatus.ReadyToInstall(file)
                        } else {
                            _status.value = UpdateDownloadStatus.Error("Downloaded file could not be located")
                        }
                    } else if (statusCode == DownloadManager.STATUS_FAILED) {
                        _status.value = UpdateDownloadStatus.Error("Download failed")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error verifying completed download", e)
        }
    }

    fun promptInstall(activityContext: Context, apkFile: File) {
        if (!apkFile.exists()) {
            _status.value = UpdateDownloadStatus.Error("APK file not found on disk")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activityContext.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activityContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activityContext.startActivity(intent)
                return
            }
        }

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch package installer", e)
            _status.value = UpdateDownloadStatus.Error("Installer launch failed: ${e.localizedMessage}")
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun resetStatus() {
        _status.value = UpdateDownloadStatus.Idle
    }
}
