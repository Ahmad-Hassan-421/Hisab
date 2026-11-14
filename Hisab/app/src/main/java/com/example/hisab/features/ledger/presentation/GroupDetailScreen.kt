package com.example.hisab.features.ledger.presentation

import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.ListSkeleton
import com.example.hisab.ui.components.AlertKind
import com.example.hisab.ui.components.HisabAlert
import com.example.hisab.ui.components.HisabRefresh
import com.example.hisab.ui.components.FeedbackEffect
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.LocalHisabFeedback
import com.example.hisab.ui.components.RefreshFailure
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.hisab.features.ledger.presentation.components.DebtDetailDialog
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.MoreVert
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.features.ledger.presentation.components.AddAmountDialog
import com.example.hisab.features.ledger.presentation.components.IOweHimDialog
import com.example.hisab.features.ledger.presentation.components.MemberBalanceCard
import com.example.hisab.features.ledger.presentation.components.RepaymentDialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeIds = uiState.group?.members.orEmpty().toSet()
    val activeMembers = uiState.members.filter { it.user.id in activeIds }
    
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteUniqueId by remember { mutableStateOf("") }
    
    var showAddAmountDialog by remember { mutableStateOf(false) }
    var showRepaymentDialog by remember { mutableStateOf(false) }
    var showIOweHimDialog by remember { mutableStateOf(false) }
    
    val feedback = LocalHisabFeedback.current
    
    LaunchedEffect(uiState.requestSuccessMessage) {
        if (uiState.requestSuccessMessage != null) {
            showInviteDialog = false
            inviteUniqueId = ""
        }
    }
    FeedbackEffect(uiState.requestSuccessMessage, FeedbackKind.SUCCESS, viewModel::clearMessages)
    FeedbackEffect(uiState.requestError, FeedbackKind.ERROR, viewModel::clearMessages)
    FeedbackEffect(
        "Settled records cleared.".takeIf { uiState.recordsCleared },
        FeedbackKind.SUCCESS,
        viewModel::acknowledgeRecordsCleared
    )

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Invite to Hisab") },
            text = {
                Column {
                    Text("Enter the 12-digit Unique ID of the user you want to invite.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inviteUniqueId,
                        onValueChange = { inviteUniqueId = it },
                        label = { Text("User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    val filteredRecs = uiState.recommendedUsers.filter {
                        it.uniqueId.contains(inviteUniqueId, ignoreCase = true) || 
                        it.name.contains(inviteUniqueId, ignoreCase = true)
                    }
                    
                    if (filteredRecs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Recommended Users", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(filteredRecs) { recUser ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { inviteUniqueId = recUser.uniqueId }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(recUser.name, fontSize = 14.sp)
                                    Text(recUser.uniqueId, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.sendInvite(inviteUniqueId) }, enabled = !uiState.isSendingRequest) {
                    ActionLabel("Send Invite", "Sending…", uiState.isSendingRequest)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showAddAmountDialog) {
        AddAmountDialog(
            members = activeMembers,
            currentUserId = uiState.currentUserId,
            isSaving = uiState.isUpdatingLedger,
            error = uiState.ledgerFormError,
            onReload = if (uiState.ledgerNeedsRefresh) viewModel::loadData else null,
            onDismiss = { showAddAmountDialog = false },
            onSplitToAll = { amount, desc ->
                viewModel.addAmountSplit(amount, desc) {
                    showAddAmountDialog = false
                    feedback?.post?.invoke("Shared expense recorded.", FeedbackKind.SUCCESS)
                }
            },
            onSpecificPerson = { debtorId, amount, desc ->
                viewModel.addAmountSpecific(debtorId, amount, desc) {
                    showAddAmountDialog = false
                    feedback?.post?.invoke("Expense recorded.", FeedbackKind.SUCCESS)
                }
            }
        )
    }

    if (showIOweHimDialog) {
        IOweHimDialog(
            members = activeMembers,
            currentUserId = uiState.currentUserId,
            isLoading = uiState.isUpdatingLedger,
            error = uiState.ledgerFormError,
            onReload = if (uiState.ledgerNeedsRefresh) viewModel::loadData else null,
            onDismiss = { showIOweHimDialog = false },
            onSubmit = { creditorId, amount, desc ->
                viewModel.addDirectDebt(creditorId, amount, desc) {
                    showIOweHimDialog = false
                    feedback?.post?.invoke("Debt recorded.", FeedbackKind.SUCCESS)
                }
            }
        )
    }

    if (showRepaymentDialog) {
        val onRepaymentSaved: () -> Unit = {
            showRepaymentDialog = false
            feedback?.post?.invoke("Repayment recorded.", FeedbackKind.SUCCESS)
        }
        val myBalance = uiState.members.firstOrNull { it.user.id == uiState.currentUserId }
        val debtorIds = myBalance?.receiveBreakdown.orEmpty().map { it.userId }.toSet()
        val creditorIds = myBalance?.debtBreakdown.orEmpty().map { it.userId }.toSet()
        if (debtorIds.isEmpty() && creditorIds.isEmpty()) {
            HisabAlert(
                kind = AlertKind.INFO,
                title = "No outstanding balance",
                message = "You have no outstanding debts or payments to receive.",
                onDismiss = { showRepaymentDialog = false },
                confirmLabel = "OK",
                onConfirm = { showRepaymentDialog = false }
            )
        } else {
            RepaymentDialog(
                debtors = uiState.members.filter { it.user.id in debtorIds },
                creditors = uiState.members.filter { it.user.id in creditorIds },
                isSaving = uiState.isUpdatingLedger,
                error = uiState.ledgerFormError,
                onReload = if (uiState.ledgerNeedsRefresh) viewModel::loadData else null,
                onDismiss = { showRepaymentDialog = false },
                onEveryonePaidSame = { amount ->
                    val amounts = debtorIds.associateWith { amount }
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment", onRepaymentSaved)
                },
                onEveryonePaidDifferent = { amounts ->
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment", onRepaymentSaved)
                },
                onSomeonePaid = { debtorId, amount ->
                    viewModel.someonePaid(debtorId, uiState.currentUserId, amount, "Repayment", onRepaymentSaved)
                },
                onIPaidSomeone = { creditorId, amount ->
                    viewModel.someonePaid(uiState.currentUserId, creditorId, amount, "Repayment", onRepaymentSaved)
                }
            )
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    if (showLeaveDialog) {
        HisabAlert(
            kind = AlertKind.WARNING,
            title = "Leave this Hisab?",
            message = "You’ll lose access to this group. Other members will still see your previous records.",
            onDismiss = { showLeaveDialog = false },
            confirmLabel = "Leave",
            dismissLabel = "Cancel",
            busy = uiState.isLeaving,
            busyLabel = "Leaving…",
            error = uiState.leaveError,
            onConfirm = {
                viewModel.leaveGroup {
                    showLeaveDialog = false
                    feedback?.post?.invoke("You left the Hisab.", FeedbackKind.INFO)
                    onNavigateBack()
                }
            }
        )
    }

    if (uiState.showClearRecordsDialog) {
        HisabAlert(
            kind = if (uiState.ledgerNeedsRefresh) AlertKind.ERROR else AlertKind.DESTRUCTIVE,
            title = "Clear settled records?",
            message = "All balances are settled. Deleting the transaction history removes it for every group member.",
            onDismiss = viewModel::dismissClearRecordsDialog,
            confirmLabel = if (uiState.ledgerNeedsRefresh) "Reload Hisab" else "Delete records",
            dismissLabel = "Keep records",
            busy = uiState.isUpdatingLedger || uiState.isLoading,
            busyLabel = if (uiState.isLoading) "Reloading…" else "Deleting…",
            error = uiState.clearRecordsError,
            onConfirm = { if (uiState.ledgerNeedsRefresh) viewModel.loadData() else viewModel.clearRecords() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hisab details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.group != null) Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Leave Group") },
                                onClick = {
                                    showMenu = false
                                    showLeaveDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        
        var detailDialogUser by remember { mutableStateOf<Pair<String, String>?>(null) }
        
        detailDialogUser?.let { (userId, userName) ->
            val entries = uiState.entries.filter { 
                (it.debtorId == uiState.currentUserId && it.creditorId == userId) || 
                (it.debtorId == userId && it.creditorId == uiState.currentUserId) 
            }
            DebtDetailDialog(
                pairBalance = uiState.members.firstOrNull { it.user.id == uiState.currentUserId }?.let { balance ->
                    balance.debtBreakdown.firstOrNull { it.userId == userId }?.amount
                        ?: balance.receiveBreakdown.firstOrNull { it.userId == userId }?.amount?.negate()
                } ?: BigDecimal.ZERO,
                otherUserName = userName,
                entries = entries,
                currentUserId = uiState.currentUserId,
                onDismiss = { detailDialogUser = null }
            )
        }
        
        HisabRefresh(
            refreshing = uiState.isRefreshing,
            onRefresh = viewModel::loadData,
            enabled = uiState.group != null && !uiState.isUpdatingLedger && !uiState.isLeaving,
            modifier = Modifier.padding(padding)
        ) {
            val group = uiState.group
            val failure = uiState.loadFailure
            val canWrite = !uiState.isUpdatingLedger && !uiState.isRefreshing && !uiState.isLeaving
            if (group == null && failure != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadFailure("Couldn't load this Hisab", failure, viewModel::loadData, retryEnabled = !uiState.isLoading)
                }
            } else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (group == null) {
                    item { ListSkeleton("Loading balances", avatar = true) }
                } else {
                    if (failure != null) item { RefreshFailure(failure, viewModel::loadData, !uiState.isLoading) }
                    item {
                        Text(group.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (activeMembers.size == 1) "1 active member" else "${activeMembers.size} active members",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = canWrite, onClick = {
                                viewModel.clearLedgerFormError()
                                showAddAmountDialog = true
                            }) { Text("Add expense") }
                            OutlinedButton(enabled = canWrite, onClick = {
                                viewModel.clearLedgerFormError()
                                showRepaymentDialog = true
                            }) { Text("Repayment") }
                            OutlinedButton(enabled = canWrite, onClick = {
                                viewModel.clearLedgerFormError()
                                showIOweHimDialog = true
                            }) { Text("Record debt") }
                            TextButton(enabled = !uiState.isLeaving, onClick = { showInviteDialog = true }) {
                                Text("Invite member")
                            }
                            if (uiState.hasSettledRecords) {
                                TextButton(enabled = canWrite && !uiState.ledgerNeedsRefresh, onClick = viewModel::requestClearRecords) {
                                    Text("Clear settled records")
                                }
                            }
                        }
                    }
                    item {
                        Text("Members and balances", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        if (!uiState.isLoading && uiState.entries.isEmpty()) Text(
                            "No transactions yet.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState.isLoading && uiState.members.isEmpty()) {
                        item { ListSkeleton("Loading balances", avatar = true) }
                    } else {
                        items(uiState.members, key = { it.user.id }) { member ->
                            MemberBalanceCard(
                                balance = member,
                                isFormerMember = member.user.id !in activeIds,
                                isCurrentUser = member.user.id == uiState.currentUserId,
                                onToggleExpand = { viewModel.toggleMemberExpanded(member.user.id) },
                                onShowDetails = { id, name -> detailDialogUser = Pair(id, name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
