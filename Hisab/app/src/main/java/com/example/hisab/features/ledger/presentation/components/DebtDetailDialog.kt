package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.data.db.HisabEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebtDetailDialog(
    currentUserOwesOther: Boolean,
    otherUserName: String,
    entries: List<HisabEntry>,
    currentUserId: String = "",
    onDismiss: () -> Unit
) {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details with $otherUserName") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val title = if (currentUserOwesOther) "You owe $otherUserName" else "$otherUserName owes you"
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))

                if (entries.isEmpty()) {
                    Text("No transactions found.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(entries) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val typeLabel = when (entry.type) {
                                        "DIRECT_DEBT" -> "Direct Debt (I Owe)"
                                        "REPAYMENT" -> "Repayment"
                                        else -> "Expense"
                                    }
                                    val direction = when {
                                        currentUserId.isEmpty() -> null
                                        entry.type == "REPAYMENT" -> {
                                            if (entry.creditorId == currentUserId) "You paid $otherUserName" else "$otherUserName paid you"
                                        }
                                        entry.debtorId == currentUserId -> "You owe $otherUserName"
                                        else -> "$otherUserName owes you"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        if (direction != null) {
                                            Text(
                                                direction,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (entry.debtorId == currentUserId) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                typeLabel,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val amountStr = String.format("PKR %.3f", entry.amount)
                                    Text("Amount: $amountStr", fontWeight = FontWeight.Bold)
                                    Text("Description: ${entry.description.ifEmpty { "None" }}")
                                    Text(
                                        "Was added on: ${formatter.format(Date(entry.timestamp))}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
