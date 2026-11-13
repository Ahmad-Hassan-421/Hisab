package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.domain.LedgerAmounts
import com.example.hisab.features.ledger.presentation.MemberBalanceUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepaymentDialog(
    debtors: List<MemberBalanceUI>,
    creditors: List<MemberBalanceUI>,
    isSaving: Boolean,
    error: String?,
    onReload: (() -> Unit)?,
    onDismiss: () -> Unit,
    onEveryonePaidSame: (amount: Double) -> Unit,
    onEveryonePaidDifferent: (amounts: Map<String, Double>) -> Unit,
    onSomeonePaid: (debtorId: String, amount: Double) -> Unit,
    onIPaidSomeone: (creditorId: String, amount: Double) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0=Menu, 1=Same, 2=Different, 3=Received, 4=Paid
    var amountText by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf("") }
    var showAmountError by remember { mutableStateOf(false) }
    
    // For mode 2
    val differentAmounts = remember { mutableStateMapOf<String, String>() }
    val counterparties = if (mode == 4) creditors else debtors

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (mode == 0) "Record Repayment" else "Enter Repayment") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (mode == 0) {
                    if (debtors.isNotEmpty()) {
                        Button(onClick = { mode = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Everyone paid Same") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { mode = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Everyone Paid Different") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { mode = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Someone paid me") }
                    }
                    if (creditors.isNotEmpty()) {
                        if (debtors.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { mode = 4 }, modifier = Modifier.fillMaxWidth()) { Text("I paid someone") }
                    }
                } else if (mode == 1) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            showAmountError = false
                        },
                        label = { Text("Amount paid by each") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mode == 2) {
                    Box(modifier = Modifier.heightIn(max = 200.dp)) {
                        LazyColumn {
                            items(debtors) { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(member.user.name, modifier = Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = differentAmounts[member.user.id] ?: "",
                                        onValueChange = {
                                            differentAmounts[member.user.id] = it
                                            showAmountError = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = counterparties.find { it.user.id == selectedUserId }?.user?.name ?: "Select a member",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            counterparties.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.user.name) },
                                    onClick = {
                                        selectedUserId = member.user.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            showAmountError = false
                        },
                        label = { Text("Amount paid") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showAmountError) {
                    Text(LedgerAmounts.AMOUNT_ERROR, color = MaterialTheme.colorScheme.error)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (onReload != null) {
                TextButton(onClick = onReload) { Text("Reload Hisab") }
            } else if (mode != 0) {
                TextButton(enabled = !isSaving && (mode <= 2 || counterparties.any { it.user.id == selectedUserId }), onClick = {
                    if (mode == 2) {
                        val amounts = LedgerAmounts.parseRepaymentAmounts(differentAmounts)
                        if (amounts == null) {
                            showAmountError = true
                            return@TextButton
                        }
                        onEveryonePaidDifferent(amounts)
                    } else {
                        val amount = LedgerAmounts.parseAmount(amountText)
                        if (amount == null) {
                            showAmountError = true
                            return@TextButton
                        }
                        when (mode) {
                            1 -> onEveryonePaidSame(amount)
                            3 -> onSomeonePaid(selectedUserId, amount)
                            4 -> onIPaidSomeone(selectedUserId, amount)
                        }
                    }
                }) {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}
