package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.HisabEntry
import java.math.BigDecimal

@Composable
fun DebtDetailDialog(
    pairBalance: BigDecimal,
    otherUserName: String,
    entries: List<HisabEntry>,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    val newestFirst = remember(entries) {
        entries.sortedWith(compareByDescending<HisabEntry> { it.timestamp }.thenBy { it.id })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transactions with $otherUserName") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val summary = when (pairBalance.signum()) {
                        1 -> "You owe ${formatLedgerAmount(pairBalance)}"
                        -1 -> "You’re owed ${formatLedgerAmount(pairBalance.abs())}"
                        else -> "No outstanding balance"
                    }
                    Text(summary, style = MaterialTheme.typography.titleMedium)
                }
                if (newestFirst.isEmpty()) item { Text("There are no transactions between you yet.") }
                items(newestFirst, key = { it.id }) { entry ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val type = when (entry.type) {
                                "DIRECT_DEBT" -> "Direct debt"
                                "REPAYMENT" -> "Repayment"
                                else -> "Expense"
                            }
                            val description = when {
                                entry.type == "REPAYMENT" && entry.creditorId == currentUserId -> "You paid $otherUserName"
                                entry.type == "REPAYMENT" -> "$otherUserName paid you"
                                entry.type == "DIRECT_DEBT" && entry.debtorId == currentUserId -> "You owed $otherUserName"
                                entry.type == "DIRECT_DEBT" -> "$otherUserName owed you"
                                entry.debtorId == currentUserId -> "$otherUserName covered your share"
                                else -> "You covered $otherUserName’s share"
                            }
                            Text(type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(formatLedgerAmount(BigDecimal.valueOf(entry.amount)), style = MaterialTheme.typography.titleMedium)
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                            if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatLedgerDate(entry.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
