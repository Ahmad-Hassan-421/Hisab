package com.example.hisab.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.hisab.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateDownloadStatus {
    data object Idle : UpdateDownloadStatus()
    data class Downloading(val bytes: Long, val total: Long?) : UpdateDownloadStatus() {
        val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (bytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
    }
    data class Paused(val message: String) : UpdateDownloadStatus()
    data object Preparing : UpdateDownloadStatus()
    data object Cancelled : UpdateDownloadStatus()
    data class Ready(val apk: File, val version: String, val permissionNeeded: Boolean = false) : UpdateDownloadStatus()
    data class InstallerOpened(val ready: Ready) : UpdateDownloadStatus()
    data class Error(val message: String) : UpdateDownloadStatus()
}

@Singleton
class ApkUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val failures: FailureReporter
) {
    private val service = checkNotNull(context.getSystemService(DownloadManager::class.java))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow<UpdateDownloadStatus>(UpdateDownloadStatus.Idle)
    val status = _status.asStateFlow()
    private var job: Job? = null
    private var downloadId: Long? = null

    @MainThread
    fun startDownload(downloadUrl: String, version: String, expectedSize: Long?) {
        if (job?.isActive == true || _status.value is UpdateDownloadStatus.Ready ||
            _status.value is UpdateDownloadStatus.InstallerOpened) return
        if (!allowedDownloadUrl(downloadUrl, BuildConfig.DEBUG)) {
            _status.value = UpdateDownloadStatus.Error("This update doesn’t have a supported download link.")
            return
        }
        _status.value = UpdateDownloadStatus.Downloading(0, expectedSize)
        job = scope.launch {
            try {
                removeDownload()
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw UpdateProblem("Update storage is unavailable. Try again later.")
                if (expectedSize != null && expectedSize > directory.usableSpace) {
                    throw UpdateProblem("There isn’t enough storage for the update. Free some space and retry.")
                }
                val filename = "hisab-${java.util.UUID.randomUUID()}.apk"
                val target = File(directory, "updates")
                if (!target.isDirectory && !target.mkdirs()) throw UpdateProblem("Update storage is unavailable. Try again later.")
                val file = File(target, filename)
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setTitle("Hisab update")
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$filename")
                    // Android should open the installer only after in-app verification and a user action.
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    setMimeType(APK_MIME)
                    setAllowedOverRoaming(false)
                }
                val id = service.enqueue(request).also { downloadId = it }
                while (isActive) {
                    val query = withContext(Dispatchers.IO) { readDownload(id) }
                    when (query.state) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING ->
                            _status.value = UpdateDownloadStatus.Downloading(query.bytes, query.total ?: expectedSize)
                        DownloadManager.STATUS_PAUSED ->
                            _status.value = UpdateDownloadStatus.Paused(pausedMessage(query.reason))
                        DownloadManager.STATUS_FAILED -> throw UpdateProblem(downloadFailureMessage(query.reason))
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _status.value = UpdateDownloadStatus.Preparing
                            withContext(Dispatchers.IO) { verifyPackage(file, expectedSize) }
                            _status.value = UpdateDownloadStatus.Ready(file, version)
                            return@launch
                        }
                        else -> throw UpdateProblem("The download is no longer available. Please try again.")
                    }
                    delay(400)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is UpdateProblem) failures.report("Download app update", e)
                removeDownload()
                _status.value = UpdateDownloadStatus.Error(
                    e.message.takeIf { e is UpdateProblem } ?: "Couldn’t download the update. Please try again."
                )
            }
        }
    }

    @MainThread
    fun cancel() {
        if (job?.isActive != true) return
        job?.cancel()
        job = null
        removeDownload()
        _status.value = UpdateDownloadStatus.Cancelled
    }

    @MainThread
    fun promptInstall(activity: Context) {
        val ready = when (val current = status.value) {
            is UpdateDownloadStatus.Ready -> current
            is UpdateDownloadStatus.InstallerOpened -> current.ready
            else -> return
        }
        try {
            if (!ready.apk.isFile) throw UpdateProblem("The update file is missing. Download the update again.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
                _status.value = ready.copy(permissionNeeded = true)
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")
                ))
                return
            }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", ready.apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            _status.value = UpdateDownloadStatus.InstallerOpened(ready)
        } catch (e: Exception) {
            if (e !is UpdateProblem) failures.report("Open package installer", e)
            _status.value = UpdateDownloadStatus.Error(e.message.takeIf { e is UpdateProblem }
                ?: "Couldn’t open Android’s installer. Please try again.")
        }
    }

    private fun removeDownload() {
        val id = downloadId ?: return
        downloadId = null
        runCatching { service.remove(id) }.onFailure { failures.report("Remove app update", it) }
    }

    private data class Download(val state: Int, val reason: Int, val bytes: Long, val total: Long?)

    private fun readDownload(id: Long): Download =
        service.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun column(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
            Download(
                column(DownloadManager.COLUMN_STATUS).toInt(), column(DownloadManager.COLUMN_REASON).toInt(),
                column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).takeIf { it > 0 }
            )
        } ?: throw UpdateProblem("The download was interrupted or removed. Please try again.")

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, expectedSize: Long?) {
        if (!file.isFile || file.length() == 0L || (expectedSize != null && file.length() != expectedSize)) {
            throw UpdateProblem("The downloaded file is incomplete. Please download it again.")
        }
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = manager.getPackageInfo(context.packageName, flags)
        val candidate = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateProblem("Android couldn’t read the downloaded update package.")
        fun PackageInfo.signers() = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
        val currentSigners = installed.signers()?.toSet().orEmpty()
        val updateSigners = candidate.signers()?.toSet().orEmpty()
        if (candidate.packageName != context.packageName || currentSigners.isEmpty() ||
            currentSigners != updateSigners || PackageInfoCompat.getLongVersionCode(candidate) <=
            PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateProblem("This file can’t update the installed Hisab. Check for another release.")
        }
    }

    private class UpdateProblem(message: String) : Exception(message)

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"

        internal fun allowedDownloadUrl(value: String, debug: Boolean): Boolean {
            val url = value.toHttpUrlOrNull() ?: return false
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
            return url.isHttps || (debug && url.host in setOf("127.0.0.1", "localhost"))
        }

        internal fun pausedMessage(reason: Int) = when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Download paused. Waiting for a connection."
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Download paused. Connect to Wi-Fi."
            else -> "Download paused. Android will retry when it can."
        }

        internal fun downloadFailureMessage(reason: Int) = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There isn’t enough storage for the update. Free some space and retry."
            404, 410 -> "The update file is unavailable. Check for updates again."
            DownloadManager.ERROR_CANNOT_RESUME -> "The download was interrupted and couldn’t resume. Try again."
            else -> "Couldn’t download the update. Please try again."
        }
    }
}
