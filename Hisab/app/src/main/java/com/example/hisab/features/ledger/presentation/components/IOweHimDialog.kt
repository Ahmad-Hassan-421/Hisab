package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    val amount = LedgerAmounts.parseAmount(amountText)
    val invalidAmount = amountText.isNotBlank() && amount == null

    val otherMembers = remember(members, currentUserId) {
        members.filter { it.user.id != currentUserId }
    }
    val canSubmit = otherMembers.any { it.user.id == selectedUserId } && amount != null

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        title = { Text("Record debt") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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

                LedgerMemberPicker(otherMembers, selectedUserId) { selectedUserId = it }

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
            TextButton(
                onClick = {
                    if (onReload != null) onReload()
                    else if (amount != null) onSubmit(selectedUserId, amount, description.trim().ifEmpty { "Direct debt" })
                },
                enabled = !isLoading && (onReload != null || canSubmit)
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
