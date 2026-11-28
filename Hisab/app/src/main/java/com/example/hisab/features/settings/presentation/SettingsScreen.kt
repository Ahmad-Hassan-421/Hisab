package com.example.hisab.features.settings.presentation

import android.text.format.Formatter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hisab.BuildConfig
import com.example.hisab.ui.components.FeedbackEffect
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.HisabAvatar
import com.example.hisab.ui.components.LocalHisabFeedback
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
fun SettingsScreen(onNavigateBack: () -> Unit, onAbout: () -> Unit, onFaq: () -> Unit, onPrivacy: () -> Unit,
                   onProfile: () -> Unit = {}, onNotifications: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.updates.state.collectAsStateWithLifecycle()
    val download by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    FeedbackEffect(state.feedback, if (state.feedbackError) FeedbackKind.ERROR else FeedbackKind.SUCCESS, viewModel::clearFeedback)
    val available = update.release
    val busy = download is UpdateDownloadStatus.Downloading || download is UpdateDownloadStatus.Paused || download is UpdateDownloadStatus.Preparing

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
            Text("Make Hisab yours", style = MaterialTheme.typography.headlineSmall)
            Text("Manage your account, choose your appearance, and find help when you need it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            SettingsSectionHeading("Account and profile", Icons.Outlined.Person)
            OutlinedCard(Modifier.fillMaxWidth()) {
                SettingsRow(account?.name ?: "Profile information", account?.email ?: "Your name, picture and recovery email",
                    Icons.Outlined.Person, onProfile, account?.profilePicUrl, avatar = true)
                HorizontalDivider()
                SettingsRow("Account management", "Private copies and permanent account deletion", Icons.Outlined.ManageAccounts, onPrivacy)
            }
            SettingsSectionHeading("Preferences", Icons.Outlined.Tune)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme preference")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                            FilterChip(enabled = !state.savingTheme, selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, label = { Text(label) })
                        }
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                SettingsRow("Notification settings", "Alerts, categories and private previews", Icons.Outlined.Notifications, onNotifications)
            }
            SettingsSectionHeading("Application and support", Icons.Outlined.SystemUpdate)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Application updates", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::checkForUpdates, enabled = !update.checking && !busy) {
                        ActionLabel("Check for updates", "Checking for updates…", update.checking)
                    }
                    val error = update.error
                    if (error != null) {
                        UpdateMessage(error, error = true)
                    } else if (!update.checking && update.checked && available == null) {
                        UpdateMessage("Hisab is up to date.")
                    }
                }
            }
            if (available != null || download !is UpdateDownloadStatus.Idle) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(available?.let { "Version ${it.versionName} available" } ?: "Update download", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = viewModel.updates::showAvailable) { Text("View update") }
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                SettingsRow("About Hisab", "The app, its creator and support", Icons.Outlined.Info, onAbout)
                HorizontalDivider()
                SettingsRow("Privacy and data", "Policy, deleted Hisabs and recovered copies", Icons.Outlined.PrivacyTip, onPrivacy)
                HorizontalDivider()
                SettingsRow("Frequently asked questions", "Answers and help for everyday use", Icons.Outlined.HelpOutline, onFaq)
            }
            Text("Version ${BuildConfig.VERSION_NAME}", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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


@Composable
private fun SettingsSectionHeading(title: String, icon: ImageVector) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit,
    avatarUrl: String? = null, avatar: Boolean = false) {
    ListItem(modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            if (avatar) HisabAvatar(avatarUrl, Modifier.size(44.dp), "Your profile picture")
            else Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}
