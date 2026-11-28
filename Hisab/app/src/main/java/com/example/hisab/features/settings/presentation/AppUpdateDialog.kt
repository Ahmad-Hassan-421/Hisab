package com.example.hisab.features.settings.presentation

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.releaseDateMillis
import com.example.hisab.ui.components.HisabFormDialog
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.UpdateDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class AppUpdateViewModel @Inject constructor(val updates: UpdateCoordinator, val downloads: ApkUpdateManager) : ViewModel()

@Composable
fun AppUpdateHost(allowed: Boolean, viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.updates.state.collectAsStateWithLifecycle()
    val download by viewModel.downloads.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (allowed && state.visible) AppUpdateDialog(state, download, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        onDismiss = viewModel.updates::dismiss,
        onRetry = { viewModel.updates.check(UpdateTrigger.MANUAL) },
        onDownload = { state.release?.let(viewModel.downloads::startDownload) },
        onCancelDownload = viewModel.downloads::cancel,
        onInstall = { viewModel.downloads.promptInstall(context) })
}

@Composable
fun AppUpdateDialog(state: UpdateUiState, download: UpdateDownloadStatus, installedName: String, installedCode: Int,
    onDismiss: () -> Unit, onRetry: () -> Unit, onDownload: () -> Unit, onCancelDownload: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    val release = state.release
    val required = release?.isRequiredFor(installedCode) == true && state.error == null
    HisabFormDialog(onDismissRequest = onDismiss,
        busy = required,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (release != null) "A new update is available" else "Application updates") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Installed version: $installedName")
            if (state.checking) { Text("Checking for updates…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.checking && state.error == null && state.checked && release == null) Text("Hisab is up to date.")
            release?.let {
                Text("Latest version: ${it.versionName}", style = MaterialTheme.typography.titleMedium)
                it.releaseDate?.let(::releaseDateMillis)?.let { timestamp -> Text("Released ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))}") }
                Text("Download size: ${Formatter.formatFileSize(context, it.sizeBytes)}")
                Text("What’s new", style = MaterialTheme.typography.titleSmall)
                Text(it.releaseNotes.trim().ifBlank { "Release notes aren’t available for this version." })
                if (required) Text("This update is required for your installed version.")
                Text("Your Hisab data stays on this device. Android will ask you to approve installation.", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when (download) {
                    is UpdateDownloadStatus.Downloading -> {
                        Text("Downloading update…")
                        val progress = download.fraction
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Text("${(progress * 100).roundToInt()}% downloaded") }
                    }
                    is UpdateDownloadStatus.Paused -> Text(download.message)
                    UpdateDownloadStatus.Preparing -> { Text("Verifying the update…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateDownloadStatus.Ready -> Text(if (download.permissionNeeded)
                        "Allow Hisab to open the installer in Android Settings, then return and choose Install update again."
                        else "The verified update is ready to install.")
                    is UpdateDownloadStatus.InstallerOpened -> Text("Android’s installer opened. Finish the update there, or open it again.")
                    is UpdateDownloadStatus.Error -> Text(download.message, color = MaterialTheme.colorScheme.error)
                    UpdateDownloadStatus.Cancelled -> Text("Download cancelled. You can retry when ready.")
                    UpdateDownloadStatus.Idle -> Unit
                }
            }
        } },
        confirmButton = {
            when {
                download is UpdateDownloadStatus.Ready || download is UpdateDownloadStatus.InstallerOpened -> Button(onClick = onInstall) { Text("Install update") }
                download is UpdateDownloadStatus.Downloading || download is UpdateDownloadStatus.Paused -> TextButton(onClick = onCancelDownload) { Text("Cancel download") }
                download is UpdateDownloadStatus.Preparing || state.checking -> Unit
                state.error != null -> Button(onClick = onRetry) { Text("Retry check") }
                release != null -> Button(onClick = onDownload) { Text(if (download is UpdateDownloadStatus.Error || download is UpdateDownloadStatus.Cancelled) "Retry download" else "Update now") }
                else -> TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = if (required) null else { { TextButton(onClick = onDismiss) { Text(if (release == null) "Close" else "Later") } } })
}
