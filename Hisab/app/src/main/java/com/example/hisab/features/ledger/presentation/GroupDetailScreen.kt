package com.example.hisab.features.ledger.presentation

import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.ListSkeleton
import com.example.hisab.ui.components.AlertKind
import com.example.hisab.ui.components.HisabAlert
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
import androidx.compose.material.icons.filled.Add
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
import com.example.hisab.features.ledger.presentation.GroupDetailViewModel
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
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
                title = { Text(uiState.group?.name ?: "Group Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Member")
                    }
                    Box {
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
                currentUserOwesOther = uiState.members.firstOrNull { it.user.id == uiState.currentUserId }
                    ?.debtBreakdown.orEmpty().any { it.userId == userId },
                otherUserName = userName,
                entries = entries,
                currentUserId = uiState.currentUserId,
                onDismiss = { detailDialogUser = null }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val failure = uiState.loadFailure
            if (failure != null && uiState.group != null) {
                RefreshFailure(failure, viewModel::loadData, !uiState.isLoading)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.clearLedgerFormError()
                        showAddAmountDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdatingLedger && uiState.group != null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Add Amount",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Button(
                    onClick = {
                        viewModel.clearLedgerFormError()
                        showRepaymentDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdatingLedger && uiState.group != null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Repayment",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Button(
                    onClick = {
                        viewModel.clearLedgerFormError()
                        showIOweHimDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdatingLedger && uiState.group != null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        "I Owe Him",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            "Members & Balances",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${activeMembers.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.group == null && failure != null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadFailure("Couldn't load this Hisab", failure, viewModel::loadData,
                                retryEnabled = !uiState.isLoading)
                        }
                    } else if (uiState.isLoading && uiState.members.isEmpty()) {
                        ListSkeleton("Loading balances", avatar = true)
                    } else if (uiState.members.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                "No members in this group",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.members) { member ->
                                MemberBalanceCard(
                                    balance = member,
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
}
