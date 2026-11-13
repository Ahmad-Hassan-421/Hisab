package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.domain.LedgerAmounts
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.features.ledger.presentation.MemberBalanceUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOweHimDialog(
    members: List<MemberBalanceUI>,
    currentUserId: String,
    isLoading: Boolean = false,
    error: String?,
    onReload: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSubmit: (creditorId: String, amount: Double, description: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val amount = LedgerAmounts.parseAmount(amountText)
    val invalidAmount = amountText.isNotBlank() && amount == null

    val otherMembers = remember(members, currentUserId) {
        members.filter { it.user.id != currentUserId }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        title = { Text("I Owe Someone") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Select the member you owe money to and enter the details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Select Creditor (Who you owe):",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = otherMembers.find { it.user.id == selectedUserId }?.user?.name ?: "Select a member",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
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

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (PKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = invalidAmount,
                    supportingText = { if (invalidAmount) Text(LedgerAmounts.AMOUNT_ERROR) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Note") },
                    placeholder = { Text("e.g. Lunch, cab fare, loan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val isValid = otherMembers.any { it.user.id == selectedUserId } && amount != null && !isLoading
            TextButton(
                onClick = {
                    if (onReload != null) onReload()
                    else if (amount != null) onSubmit(selectedUserId, amount, description.trim().ifEmpty { "I owe him" })
                },
                enabled = !isLoading && (onReload != null || isValid)
            ) {
                ActionLabel(if (onReload != null) "Reload Hisab" else "Confirm", "Saving…", isLoading)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}
