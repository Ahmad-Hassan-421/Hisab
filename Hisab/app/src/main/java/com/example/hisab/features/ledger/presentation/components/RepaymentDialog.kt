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
import com.example.hisab.features.ledger.presentation.MemberBalanceUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepaymentDialog(
    members: List<MemberBalanceUI>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onEveryonePaidSame: (amount: Double) -> Unit,
    onEveryonePaidDifferent: (amounts: Map<String, Double>) -> Unit,
    onSomeonePaid: (debtorId: String, amount: Double) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0=Menu, 1=Same, 2=Diff, 3=Specific
    var amountText by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf("") }
    
    // For mode 2
    val differentAmounts = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == 0) "Someone Paid" else "Enter Repayment") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (mode == 0) {
                    Button(onClick = { mode = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Everyone paid Same") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { mode = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Everyone Paid Different") }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { mode = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Someone paid") }
                } else if (mode == 1) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount paid by each") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mode == 2) {
                    val otherMembers = members.filter { it.user.id != currentUserId }
                    Box(modifier = Modifier.heightIn(max = 200.dp)) {
                        LazyColumn {
                            items(otherMembers) { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(member.user.name, modifier = Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = differentAmounts[member.user.id] ?: "",
                                        onValueChange = { differentAmounts[member.user.id] = it },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                } else if (mode == 3) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount paid") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (mode != 0) {
                TextButton(onClick = {
                    if (mode == 1) {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) onEveryonePaidSame(amt)
                    } else if (mode == 2) {
                        val parsed = differentAmounts.mapValues { it.value.toDoubleOrNull() ?: 0.0 }.filter { it.value > 0.0 }
                        onEveryonePaidDifferent(parsed)
                    } else if (mode == 3) {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0 && selectedUserId.isNotEmpty()) {
                            onSomeonePaid(selectedUserId, amt)
                        }
                    }
                    onDismiss()
                }) {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
