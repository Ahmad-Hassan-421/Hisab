package com.example.hisab.features.ledger.presentation

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
import com.example.hisab.features.ledger.presentation.components.MemberBalanceCard
import com.example.hisab.features.ledger.presentation.components.RepaymentDialog
import com.example.hisab.features.ledger.presentation.GroupDetailViewModel

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
                    Text("Send Invite")
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
            onDismiss = { showAddAmountDialog = false },
            onSplitToAll = { amount, desc ->
                viewModel.addAmountSplit(amount, desc)
            },
            onSpecificPerson = { debtorId, amount, desc ->
                viewModel.addAmountSpecific(debtorId, amount, desc)
            }
        )
    }

    if (showRepaymentDialog) {
        val debtorIds = uiState.members.firstOrNull { it.user.id == uiState.currentUserId }
            ?.receiveBreakdown.orEmpty().map { it.userId }.filter { it in activeIds }.toSet()
        if (debtorIds.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showRepaymentDialog = false },
                title = { Text("No Receive Amount") },
                text = { Text("You don't have any outstanding amounts to receive from others.") },
                confirmButton = { TextButton(onClick = { showRepaymentDialog = false }) { Text("OK") } }
            )
        } else {
            RepaymentDialog(
                members = activeMembers.filter { it.user.id in debtorIds },
                currentUserId = uiState.currentUserId,
                onDismiss = { showRepaymentDialog = false },
                onEveryonePaidSame = { amount ->
                    val amounts = debtorIds.associateWith { amount }
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment")
                },
                onEveryonePaidDifferent = { amounts ->
                    viewModel.recordRepayments(uiState.currentUserId, amounts, "Repayment")
                },
                onSomeonePaid = { debtorId, amount ->
                    viewModel.someonePaid(debtorId, uiState.currentUserId, amount, "Repayment")
                }
            )
        }
    }
    
    var showMenu by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave this Hisab group? Your previous records will still be visible to others.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveGroup(onComplete = {
                        onNavigateBack()
                    })
                }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
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
                TextButton(onClick = { viewModel.clearRecords() }, enabled = !uiState.isUpdatingLedger) {
                    Text("Delete Records", color = MaterialTheme.colorScheme.error)
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
                onDismiss = { detailDialogUser = null }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { showAddAmountDialog = true }, modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdatingLedger && uiState.group != null) {
                    Text("Add Amount")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showRepaymentDialog = true }, modifier = Modifier.weight(1f),
                    enabled = !uiState.isUpdatingLedger && uiState.group != null) {
                    Text("Someone Paid")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Members & Balances", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
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
