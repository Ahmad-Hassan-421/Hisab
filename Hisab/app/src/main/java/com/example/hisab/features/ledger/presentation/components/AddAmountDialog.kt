package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
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
fun AddAmountDialog(
    members: List<MemberBalanceUI>,
    currentUserId: String,
    isSaving: Boolean,
    error: String?,
    onReload: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSplitToAll: (amount: Double, description: String) -> Unit,
    onSpecificPerson: (debtorId: String, amount: Double, description: String) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0 = Select Mode, 1 = Split, 2 = Specific
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf("") }
    val amount = LedgerAmounts.parseAmount(amountText)
    val invalidAmount = amountText.isNotBlank() && amount == null

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (mode == 0) "Add Amount" else if (mode == 1) "Split to All" else "Specific Person") },
        text = {
            Column {
                if (mode == 0) {
                    Button(onClick = { mode = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Split to all")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { mode = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("For a specific person")
                    }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = invalidAmount,
                        supportingText = { if (invalidAmount) Text(LedgerAmounts.AMOUNT_ERROR) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (mode == 2) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Debtor:")
                        val otherMembers = members.filter { it.user.id != currentUserId }
                        var expanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = otherMembers.find { it.user.id == selectedUserId }?.user?.name ?: "Select a member",
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
                                otherMembers.forEach { member ->
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
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (onReload != null) {
                TextButton(onClick = onReload) { Text("Reload Hisab") }
            } else if (mode != 0) {
                TextButton(
                    enabled = !isSaving && amount != null && (mode == 1 || members.any { it.user.id == selectedUserId }),
                    onClick = {
                        val validAmount = amount ?: return@TextButton
                        if (mode == 1) onSplitToAll(validAmount, description)
                        else onSpecificPerson(selectedUserId, validAmount, description)
                    }
                ) {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
