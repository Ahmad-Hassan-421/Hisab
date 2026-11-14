package com.example.hisab.features.settings.presentation

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.AlertKind
import com.example.hisab.ui.components.HisabAlert
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.UpdateDownloadStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val download by viewModel.downloadStatus.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current
    val available = state.remoteRelease?.takeIf { state.updateAvailable }
    val busy = download is UpdateDownloadStatus.Downloading || download is UpdateDownloadStatus.Paused ||
        download is UpdateDownloadStatus.Preparing
    var confirmUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val intended = available?.takeIf { it.downloadUrl == confirmUrl && it.versionName == confirmVersion }
    if (intended != null) {
        HisabAlert(
            kind = AlertKind.CONFIRM,
            title = "Update to Hisab ${intended.versionName}?",
            message = "Hisab will download the update. You’ll review the installation in Android before it starts.",
            confirmLabel = "Update now",
            dismissLabel = "Later",
            onDismiss = { confirmUrl = null; confirmVersion = null },
            onConfirm = { confirmUrl = null; confirmVersion = null; viewModel.startUpdateDownload(intended) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("App updates", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Installed version", style = MaterialTheme.typography.labelLarge)
                    Text("${state.currentVersionName} (build ${state.currentVersionCode})", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::checkForUpdates, enabled = !state.isCheckingForUpdate && !busy) {
                        ActionLabel("Check for updates", "Checking for updates…", state.isCheckingForUpdate)
                    }
                    val error = state.checkError
                    if (error != null) {
                        UpdateMessage(error, error = true)
                    } else if (!state.isCheckingForUpdate && state.remoteRelease != null && available == null) {
                        UpdateMessage("Hisab is up to date.")
                    }
                }
            }
            if (available != null || download !is UpdateDownloadStatus.Idle) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        available?.let { release ->
                            Text("Version ${release.versionName} available", style = MaterialTheme.typography.titleMedium)
                            release.sizeBytes?.takeIf { it > 0 }?.let {
                                Text("Download size: ${Formatter.formatFileSize(context, it)}")
                            }
                            val notes = release.releaseNotes.orEmpty().trim()
                            Text(notes.take(600).ifBlank { "Release notes aren’t available." }, style = MaterialTheme.typography.bodyMedium)
                        }
                        when (val current = download) {
                            is UpdateDownloadStatus.Downloading -> {
                                UpdateMessage("Downloading update…")
                                val progress = current.fraction
                                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                else {
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                    Text("${(progress * 100).roundToInt()}% downloaded")
                                }
                            }
                            is UpdateDownloadStatus.Paused -> UpdateMessage(current.message)
                            UpdateDownloadStatus.Preparing -> {
                                UpdateMessage("Preparing installation…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            is UpdateDownloadStatus.Ready -> {
                                UpdateMessage("Hisab ${current.version} is ready to install.")
                                if (current.permissionNeeded) {
                                    Text("Allow Hisab to open the installer in Android Settings, then return and choose Install update again.")
                                }
                                Button(onClick = { viewModel.installApk(context) }) { Text("Install update") }
                            }
                            is UpdateDownloadStatus.InstallerOpened -> {
                                UpdateMessage("Android installer opened. Finish the update there.")
                                OutlinedButton(onClick = { viewModel.installApk(context) }) { Text("Open installer again") }
                            }
                            is UpdateDownloadStatus.Error -> UpdateMessage(current.message, error = true)
                            UpdateDownloadStatus.Cancelled -> UpdateMessage("Download cancelled.")
                            UpdateDownloadStatus.Idle -> Unit
                        }
                        if (busy) {
                            TextButton(onClick = viewModel::cancelUpdateDownload) { Text("Cancel download") }
                        } else if (available != null && (download is UpdateDownloadStatus.Idle ||
                            download is UpdateDownloadStatus.Error || download is UpdateDownloadStatus.Cancelled)) {
                            Button(
                                onClick = { confirmUrl = available.downloadUrl; confirmVersion = available.versionName },
                                enabled = !state.isCheckingForUpdate
                            ) {
                                Text(if (download is UpdateDownloadStatus.Idle) "Update now" else "Retry download")
                            }
                        }
                    }
                }
            }
            Text("Appearance", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme preference")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                            FilterChip(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, label = { Text(label) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateMessage(message: String, error: Boolean = false) {
    Text(
        message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
