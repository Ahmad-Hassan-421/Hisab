package com.example.hisab.features.admin.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.features.admin.data.AccountOverview
import com.example.hisab.features.admin.data.DirectoryAccount
import com.example.hisab.ui.components.AlertKind
import com.example.hisab.ui.components.FeedbackEffect
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.HisabAlert
import com.example.hisab.ui.components.HisabAvatar
import com.example.hisab.ui.components.ListSkeleton
import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.RefreshFailure
import com.example.hisab.util.AppFailure
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(onNavigateBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    var selected by remember { mutableStateOf<DirectoryAccount?>(null) }

    FeedbackEffect(state.feedback, FeedbackKind.SUCCESS, viewModel::clearFeedback)
    selected?.let { account ->
        val suspendAccount = account.status == "active"
        HisabAlert(
            kind = if (suspendAccount) AlertKind.DESTRUCTIVE else AlertKind.CONFIRM,
            title = if (suspendAccount) "Suspend account?" else "Reactivate account?",
            message = if (suspendAccount) "This account will be signed out. Its Hisabs and transactions will be kept."
                else "This account can sign in again.",
            onDismiss = { selected = null },
            dismissLabel = "Cancel",
            confirmLabel = if (suspendAccount) "Suspend" else "Reactivate",
            onConfirm = {
                selected = null
                viewModel.changeStatus(account)
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Account administration") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        val problem = state.failure
        if (problem == AppFailure.FORBIDDEN || problem == AppFailure.SESSION_EXPIRED) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadFailure("Account directory unavailable", problem, viewModel::refresh, retryEnabled = !state.busy)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.overview?.let { overview -> item { OverviewCard(overview) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.searchInput, onValueChange = viewModel::searchChanged, enabled = !state.busy,
                        label = { Text("Name, email, or Hisab ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.search() })
                    )
                    Button(onClick = { focus.clearFocus(); viewModel.search() }, enabled = !state.busy) { Text("Search") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DirectoryFilter.entries.forEach { filter ->
                            FilterChip(selected = filter == state.filter, onClick = { viewModel.filter(filter) },
                                enabled = !state.busy, label = { Text(filter.label) })
                        }
                    }
                    Text("Newest registrations first", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (problem != null) item { RefreshFailure(problem, viewModel::refresh, !state.busy) }
            if (state.accounts.isEmpty() && !state.busy && problem == null) item { Text("No matching accounts.") }
            if (state.accounts.isEmpty() && state.busy) item { ListSkeleton("Loading accounts") }
            items(state.accounts, key = DirectoryAccount::id) { account ->
                AccountCard(account, state.busy, onChangeStatus = { selected = account })
            }
            if (state.nextCursor != null) item {
                OutlinedButton(onClick = viewModel::nextPage, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Loading accounts…" else "Load more accounts")
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: AccountOverview) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OverviewRow("Registered accounts", overview.registered)
            OverviewRow("Registered in the last 30 days", overview.registeredInLast30Days)
            OverviewRow("Suspended accounts", overview.suspended)
        }
    }
}

@Composable
private fun OverviewRow(label: String, count: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AccountCard(account: DirectoryAccount, loading: Boolean, onChangeStatus: () -> Unit) {
    val registered = remember(account.createdAt) {
        account.createdAt?.let { DateFormat.getDateInstance().format(Date(it)) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HisabAvatar(account.avatarUrl, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name?.takeIf(String::isNotBlank) ?: "Unnamed account", style = MaterialTheme.typography.titleMedium)
                    account.email?.takeIf(String::isNotBlank)?.let { email -> Text(email, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            account.publicId?.takeIf(String::isNotBlank)?.let { Text("Hisab ID $it") }
            registered?.let { Text("Registered $it") }
            if (!account.email.isNullOrBlank()) Text(if (account.emailVerified) "Email verified" else "Email unverified")
            val role = if (account.role == "admin") "Administrator" else "User"
            val status = if (account.status == "active") "Active" else "Suspended"
            Text("$role · $status", style = MaterialTheme.typography.labelLarge)
            if (account.role == "user") {
                OutlinedButton(onClick = onChangeStatus, enabled = !loading) {
                    Text(if (account.status == "active") "Suspend account" else "Reactivate account")
                }
            }
        }
    }
}
