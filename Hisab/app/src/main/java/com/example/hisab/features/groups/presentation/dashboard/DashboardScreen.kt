package com.example.hisab.features.groups.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.ui.components.FeedbackEffect
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.HisabRefresh
import com.example.hisab.ui.components.ListSkeleton
import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.RefreshFailure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isAdmin: Boolean,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onCreateHisab: () -> Unit = {},
    onGroupClick: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    FeedbackEffect(uiState.actionError, FeedbackKind.ERROR, viewModel::clearActionError)
    LaunchedEffect(viewModel) {
        // The view model loads on first entry. Refresh a retained dashboard when returning.
        if (viewModel.uiState.value.hasLoaded) viewModel.refreshGroups(userInitiated = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hisab", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin panel")
                        }
                    }
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        HisabRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshGroups() },
            enabled = uiState.hasLoaded,
            modifier = Modifier.padding(padding)
        ) {
            val failure = uiState.failure
            when {
                !uiState.hasLoaded && failure != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadFailure("Couldn't load your Hisabs", failure, { viewModel.refreshGroups() },
                            retryEnabled = !uiState.isLoading)
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    if (failure != null) RefreshFailure(failure, { viewModel.refreshGroups() }, !uiState.isLoading)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!uiState.hasLoaded) {
                            item { ListSkeleton("Loading your Hisabs", avatar = true) }
                        } else if (uiState.groups.isEmpty()) {
                            item { EmptyHisabs(onCreateHisab) }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Your Hisabs",
                                        modifier = Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    val count = uiState.groups.size
                                    Text(
                                        if (count == 1) "1 group" else "$count groups",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = onCreateHisab) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Create Hisab")
                                    }
                                }
                            }
                            items(uiState.groups, key = { it.id }) { group ->
                                HisabGroupItem(group) { onGroupClick(group.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHisabs(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GroupIcon()
        Text("Start your first Hisab", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Create a group to track shared expenses, debts, and repayments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onCreate) { Text("Create Hisab") }
    }
}

@Composable
private fun HisabGroupItem(group: HisabGroup, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GroupIcon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                val count = group.members.distinct().size
                Text(
                    if (count == 1) "1 member" else "$count members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun GroupIcon() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(
            Icons.Default.Group, contentDescription = null,
            modifier = Modifier.size(44.dp).padding(10.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
