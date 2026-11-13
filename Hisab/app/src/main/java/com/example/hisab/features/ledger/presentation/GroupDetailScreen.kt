package com.example.hisab.features.ledger.presentation

import com.example.hisab.ui.components.LoadFailure
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.ListSkeleton
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
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.requestSuccessMessage) {
        uiState.requestSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            showInviteDialog = false
            inviteUniqueId = ""
            viewModel.clearMessages()
        }
    }
    
    LaunchedEffect(uiState.requestError) {
        uiState.requestError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

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
                viewModel.addAmountSplit(amount, desc) { showAddAmountDialog = false }
            },
            onSpecificPerson = { debtorId, amount, desc ->
                viewModel.addAmountSpecific(debtorId, amount, desc) { showAddAmountDialog = false }
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
                viewModel.addDirectDebt(creditorId, amount, desc) { showIOweHimDialog = false }
            }
        )
    }

    if (showRepaymentDialog) {
        val myBalance = uiState.members.firstOrNull { it.user.id == uiState.currentUserId }
        val debtorIds = myBalance?.receiveBreakdown.orEmpty().map { it.userId }.toSet()
        val creditorIds = myBalance?.debtBreakdown.orEmpty().map { it.userId }.toSet()
        if (debtorIds.isEmpty() && creditorIds.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showRepaymentDialog = false },
                title = { Text("No Outstanding Balance") },
                text = { Text("You have no outstanding debts or payments to receive.") },
                confirmButton = { TextButton(onClick = { showRepaymentDialog = false }) { Text("OK") } }
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
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment") { showRepaymentDialog = false }
                },
                onEveryonePaidDifferent = { amounts ->
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment") { showRepaymentDialog = false }
                },
                onSomeonePaid = { debtorId, amount ->
                    viewModel.someonePaid(debtorId, uiState.currentUserId, amount, "Repayment") { showRepaymentDialog = false }
                },
                onIPaidSomeone = { creditorId, amount ->
                    viewModel.someonePaid(uiState.currentUserId, creditorId, amount, "Repayment") { showRepaymentDialog = false }
                }
            )
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isLeaving) showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to leave this Hisab group? Your previous records will still be visible to others.")
                    uiState.leaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveGroup(onComplete = {
                        showLeaveDialog = false
                        onNavigateBack()
                    })
                }, enabled = !uiState.isLeaving) {
                    ActionLabel("Leave", "Leaving…", uiState.isLeaving)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }, enabled = !uiState.isLeaving) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showClearRecordsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearRecordsDialog() },
            title = { Text("Hisab is Done!") },
            text = { Text("Everyone is at 0 debt and 0 will receive. Do you want to clear the previous records to save storage?") },
            confirmButton = {
                TextButton(
                    onClick = { if (uiState.ledgerNeedsRefresh) viewModel.loadData() else viewModel.clearRecords() },
                    enabled = !uiState.isUpdatingLedger && !uiState.isLoading
                ) {
                    ActionLabel(if (uiState.ledgerNeedsRefresh) "Reload Hisab" else "Delete Records",
                        "Deleting…", uiState.isUpdatingLedger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearRecordsDialog() }) {
                    Text("Keep Records")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
