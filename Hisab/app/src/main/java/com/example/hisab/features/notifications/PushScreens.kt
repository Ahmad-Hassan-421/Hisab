package com.example.hisab.features.notifications

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.hisab.ui.components.*

@Composable
fun NotificationPermissionExplanation(onEnable: () -> Unit, onLater: () -> Unit) {
    HisabAlert(AlertKind.INFO, "Stay Updated with Hisab",
        "Enable notifications to receive important updates about your debts, payments, shared Hisabs, and new application versions. Financial details stay hidden unless you enable previews in Settings.",
        onDismiss = onLater, confirmLabel = "Enable notifications", onConfirm = onEnable, dismissLabel = "Not now")
}

@Composable
private fun PermissionChoice(show: Boolean, onDecide: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onDecide(granted); onDismiss() }
    if (show) NotificationPermissionExplanation(onEnable = {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else { onDecide(NotificationManagerCompat.from(context).areNotificationsEnabled()); onDismiss() }
    }, onLater = { onDecide(false); onDismiss() })
}

@Composable
fun PushPermissionPrompt(blocked: Boolean = false, onVisibility: (Boolean) -> Unit = {}, viewModel: PushViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showing = state.owner != null && !state.asked && !blocked
    SideEffect { onVisibility(showing) }
    DisposableEffect(Unit) { onDispose { onVisibility(false) } }
    PermissionChoice(showing, viewModel::decide, {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSettingsScreen(onBack: () -> Unit, viewModel: PushViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var explanation by rememberSaveable { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var systemEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    PermissionChoice(explanation, viewModel::decide, { explanation = false })
    Scaffold(topBar = { TopAppBar(title = { Text("Notifications") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Choose what reaches you", style = MaterialTheme.typography.headlineSmall)
            Text("Your category and preview choices apply to all devices on your account. Enabling alerts is specific to this device.")
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                PreferenceSwitch("Alerts on this device", state.optedIn && systemEnabled, true) { if (it) explanation = true else viewModel.decide(false) }
                if (state.optedIn && !systemEnabled) Text("Android is blocking alerts. Enable them in Android notification settings.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(intent) }
                }) { Text("Open Android notification settings") }
            } }
            if (!state.configured && state.loaded) Text("Push delivery is not configured on this server yet. Your preferences are saved.")
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::refresh) { Text("Retry") } }
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                val enabled = state.loaded && !state.saving
                PreferenceSwitch("Debt updates", state.preferences.debts, enabled) { viewModel.save(state.preferences.copy(debts = it)) }
                PreferenceSwitch("Recorded payments", state.preferences.payments, enabled) { viewModel.save(state.preferences.copy(payments = it)) }
                PreferenceSwitch("Hisab invitations", state.preferences.invitations, enabled) { viewModel.save(state.preferences.copy(invitations = it)) }
                PreferenceSwitch("Recovery ready", state.preferences.recovery, enabled) { viewModel.save(state.preferences.copy(recovery = it)) }
                PreferenceSwitch("New app versions", state.preferences.updates, enabled) { viewModel.save(state.preferences.copy(updates = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                PreferenceSwitch("Show financial previews", state.preferences.previews, enabled) { viewModel.save(state.preferences.copy(previews = it)) }
                Text("Off by default. Lock-screen previews remain private. Open Hisab to view records securely.", style = MaterialTheme.typography.bodySmall)
                if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            } }
            Text("Delivery depends on your connection and Android background settings. After force-stopping Hisab in Android, open it again to resume messages.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
