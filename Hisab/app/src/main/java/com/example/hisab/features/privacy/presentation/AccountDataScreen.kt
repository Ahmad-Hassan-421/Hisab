package com.example.hisab.features.privacy.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.hisab.features.privacy.data.ArchiveCard
import com.example.hisab.features.profile.data.UserDto
import com.example.hisab.ui.components.*
import com.example.hisab.features.ledger.presentation.components.formatLedgerAmount
import java.math.BigDecimal
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDataScreen(onBack: () -> Unit, onPolicy: () -> Unit, viewModel: AccountDataViewModel = hiltViewModel(), startRecovered: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val feedback = LocalHisabFeedback.current
    var notificationOpened by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startRecovered) {
        if (startRecovered && !notificationOpened) { notificationOpened = true; viewModel.open(DataPanel.RECOVERED) }
    }
    var recover by remember { mutableStateOf<ArchiveCard?>(null) }
    var deleteCopy by remember { mutableStateOf<ArchiveCard?>(null) }
    var deleteAccount by remember { mutableStateOf<UserDto?>(null) }
    var password by remember { mutableStateOf("") }
    // Keep form errors beside the password; use the shared host for all other action feedback.
    if (deleteAccount == null) FeedbackEffect(state.message, state.messageKind, viewModel::consumeMessage)
    val goBack = { if (!state.busy) { if (state.panel == DataPanel.HOME) onBack() else viewModel.back() } }
    BackHandler(state.panel != DataPanel.HOME || state.busy, goBack)
    LaunchedEffect(state.panel, lifecycle) {
        if (state.panel == DataPanel.ARCHIVES || state.panel == DataPanel.RECOVERED) {
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh(background = true)
                while (true) { delay(15_000); viewModel.refresh(background = true) }
            }
        }
    }
    val title = when (state.panel) {
        DataPanel.HOME -> "Privacy and data"
        DataPanel.ARCHIVES -> "Recover Your Hisab Data"
        DataPanel.RECOVERED -> "Recovered Hisabs"
        DataPanel.HISTORY, DataPanel.MEMBERS -> state.selected?.name ?: "Recovered Hisab"
        DataPanel.ACCOUNTS -> "Select your account"
        DataPanel.SUBMITTED -> "Recovery Request Submitted"
    }
    val hasRows = when (state.panel) {
        DataPanel.ARCHIVES, DataPanel.RECOVERED -> state.cards.isNotEmpty()
        DataPanel.HISTORY -> state.entries.isNotEmpty()
        DataPanel.MEMBERS -> state.members.isNotEmpty()
        DataPanel.ACCOUNTS -> state.accounts.isNotEmpty()
        else -> false
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
        IconButton(onClick = goBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        HisabRefresh(state.loading && hasRows, { viewModel.refresh() }, Modifier.padding(padding),
            enabled = state.panel !in setOf(DataPanel.HOME, DataPanel.SUBMITTED) && !state.busy) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.panel == DataPanel.HOME) {
                    item { Text("Control your private copies and account. Other members keep access to their shared financial records.") }
                    item { OutlinedButton(onClick = onPolicy, modifier = Modifier.fillMaxWidth()) { Text("Privacy Policy") } }
                    item { Button(onClick = { viewModel.open(DataPanel.ARCHIVES) }, modifier = Modifier.fillMaxWidth()) { Text("Retrieve a Copy of My Data") } }
                    item { OutlinedButton(onClick = { viewModel.open(DataPanel.RECOVERED) }, modifier = Modifier.fillMaxWidth()) { Text("View recovered Hisabs") } }
                    item { TextButton(onClick = { viewModel.open(DataPanel.ACCOUNTS) }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Permanently delete my account") } }
                }
                if (state.panel == DataPanel.ARCHIVES) {
                    item { Text("Select a previously deleted Hisab to retrieve a read-only copy of its records. Deleted Hisabs are available for recovery for up to two months.") }
                    item { TextButton(onClick = { viewModel.open(DataPanel.RECOVERED) }) { Text("View recovered Hisabs") } }
                }
                if (state.panel == DataPanel.SUBMITTED) {
                    item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your request to retrieve this Hisab has been successfully submitted. Your records will be available within 3–5 minutes.")
                        Text(state.receipt?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("Status: ${state.receipt?.status ?: "pending"}")
                        Text("Recovery continues when you close the app.")
                        Button(onClick = { viewModel.open(DataPanel.RECOVERED) }) { Text("Done") }
                    } } }
                }
                if (state.panel in setOf(DataPanel.ARCHIVES, DataPanel.RECOVERED)) {
                    items(state.cards, key = ArchiveCard::id) { card ->
                        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.name, style = MaterialTheme.typography.titleMedium)
                            Text("${card.recordCount} records · ${card.memberCount} members")
                            Text("Deleted ${displayDate(card.deletedAt)}", style = MaterialTheme.typography.bodySmall)
                            card.expiresAt?.let { Text("Recovery expires ${displayDate(it)}", style = MaterialTheme.typography.bodySmall) }
                            Text(if (state.panel == DataPanel.RECOVERED) "Recovered — Read Only" else "Status: ${card.status}")
                            if (state.panel == DataPanel.ARCHIVES) {
                                Button(onClick = { recover = card }, enabled = !state.busy && card.status in setOf("available", "failed")) {
                                    Text(if (card.status == "failed") "Retry recovery" else if (card.status == "pending") "Recovery in progress" else if (card.status == "ready") "Already recovered" else "Recover")
                                }
                            } else {
                                Button(onClick = { viewModel.open(DataPanel.HISTORY, card) }) { Text("View records") }
                                TextButton(onClick = { deleteCopy = card }, enabled = !state.busy,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete permanently") }
                            }
                        } }
                    }
                    if (state.cards.isEmpty() && !state.loading && state.failure == null) item {
                        Text(if (state.panel == DataPanel.ARCHIVES) "No recoverable Hisabs found. Deleted Hisabs are available for recovery for up to two months after deletion."
                            else "No recovered Hisabs yet. Submitted requests normally become available within 3–5 minutes.")
                    }
                }
                if (state.panel in setOf(DataPanel.HISTORY, DataPanel.MEMBERS)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Recovered — Read Only", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            HisabAvatar(state.selected?.pictureUrl, Modifier.size(64.dp), "Historical Hisab picture", group = true)
                            Text("A historical copy. New debts, payments and member changes are disabled.")
                            TextButton(onClick = { viewModel.open(if (state.panel == DataPanel.HISTORY) DataPanel.MEMBERS else DataPanel.HISTORY) }) {
                                Text(if (state.panel == DataPanel.HISTORY) "View historical members" else "View transaction history")
                            }
                        }
                    }
                }
                if (state.panel == DataPanel.HISTORY) {
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "All", "debts" to "Debts", "payments" to "Payments").forEach { (key, text) ->
                            FilterChip(selected = state.filter == key, onClick = { viewModel.open(DataPanel.HISTORY, filter = key) }, label = { Text(text) })
                        }
                    } }
                    items(state.entries, key = { it.id }) { entry ->
                        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (entry.type == "REPAYMENT") "Payment" else "Debt", style = MaterialTheme.typography.titleMedium)
                            Text(formatLedgerAmount(BigDecimal.valueOf(entry.amount)))
                            Text(if (entry.type == "REPAYMENT") "${entry.creditorName ?: "Member"} paid ${entry.debtorName ?: "Member"}"
                                else "${entry.debtorName ?: "Member"} owes ${entry.creditorName ?: "Member"}")
                            if (!entry.description.isNullOrBlank()) Text(entry.description)
                            Text(displayDate(entry.timestamp), style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                    if (state.entries.isEmpty() && !state.loading && state.failure == null) item { Text("No records in this view.") }
                }
                if (state.panel == DataPanel.MEMBERS) {
                    items(state.members, key = { it.id }) { member ->
                        OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HisabAvatar(member.profilePicUrl, Modifier.size(40.dp), member.name)
                            Text(member.name)
                        } }
                    }
                }
                if (state.panel == DataPanel.ACCOUNTS) {
                    item { Text("Only your signed-in account can be deleted here. Select it to review the permanent deletion warning.") }
                    items(state.accounts, key = { it.id }) { account ->
                        OutlinedCard(onClick = { deleteAccount = account; password = "" }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HisabAvatar(account.profilePicUrl, Modifier.size(56.dp), account.name)
                                Text(account.name, style = MaterialTheme.typography.titleMedium)
                                Text(account.email)
                                Text("Signed in · Active")
                            }
                        }
                    }
                }
                if (state.loading && !hasRows) item {
                    val kind = when (state.panel) {
                        DataPanel.HISTORY -> SkeletonKind.TRANSACTIONS
                        DataPanel.MEMBERS, DataPanel.ACCOUNTS -> SkeletonKind.MEMBERS
                        else -> SkeletonKind.ARCHIVES
                    }
                    ListSkeleton("Loading ${title.lowercase()}", kind = kind, rows = if (state.panel == DataPanel.ACCOUNTS) 1 else 3)
                }
                if (state.panel == DataPanel.ACCOUNTS && !state.loading && state.accounts.isEmpty() && state.failure == null) item {
                    Text("No manageable account found. Return to sign-in and try again.")
                }
                state.failure?.let { failure -> item { Text(failure.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.refresh() }) { Text("Retry") } } }
                if (deleteAccount != null) state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                item { PageFooter(state.cursor, state.loadingMore, state.pageFailure, viewModel::more) }
            }
        }
    }
    recover?.let { card -> HisabAlert(AlertKind.CONFIRM, "Recover ${card.name}?", "Create a private, read-only historical copy. Recovery normally takes 3–5 minutes.",
        onDismiss = { recover = null }, confirmLabel = "Recover Hisab", onConfirm = { viewModel.recover(card); recover = null }, dismissLabel = "Cancel") }
    deleteCopy?.let { card -> HisabAlert(AlertKind.DESTRUCTIVE, "Delete recovered Hisab?", "This permanently deletes ${card.name}, its recovery source and recovery request. It cannot be recovered again.",
        onDismiss = { deleteCopy = null }, confirmLabel = "Delete permanently", onConfirm = { viewModel.deleteCopy(card); deleteCopy = null }, dismissLabel = "Cancel") }
    deleteAccount?.let { account -> HisabAlert(AlertKind.DESTRUCTIVE, "Permanently Delete Account?",
        "Deleting ${account.email} permanently removes your account, private archives, recovered Hisabs and personal data. Shared financial records remain for other members with your identity anonymized. This action cannot be undone.",
        onDismiss = { deleteAccount = null; password = "" }, confirmLabel = "Permanently Delete", onConfirm = {
            viewModel.deleteAccount(account.id, password) { feedback?.post?.invoke("Account permanently deleted.", FeedbackKind.SUCCESS) }
            password = ""
        },
        dismissLabel = "Cancel", busy = state.busy, busyLabel = "Deleting account…", confirmEnabled = password.isNotBlank(), error = state.message,
        details = { OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Confirm your password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }) }
}

private fun displayDate(value: Long) = if (value <= 0) "Date unavailable" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
