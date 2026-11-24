package com.example.hisab.features.ledger.presentation.components

import com.example.hisab.ui.components.HisabFormDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.ui.components.ListSkeleton
import com.example.hisab.ui.components.PageFooter
import com.example.hisab.util.AppFailure
import java.math.BigDecimal

@Composable
fun DebtDetailDialog(
    pairBalance: BigDecimal?,
    otherUserName: String?,
    entries: List<HisabEntry>,
    currentUserId: String,
    onDismiss: () -> Unit,
    nextCursor: String? = null,
    loading: Boolean = false,
    failure: AppFailure? = null,
    names: Map<String, String> = emptyMap(),
    kind: String = "all",
    onKind: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    LaunchedEffect(kind) { listState.scrollToItem(0) }
    HisabFormDialog(
        onDismissRequest = onDismiss,
        title = { Text(otherUserName?.let { "Transactions with $it" } ?: "Transaction history") },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "All", "debts" to "Debts", "payments" to "Payments").forEach { (value, label) ->
                            FilterChip(selected = kind == value, onClick = { onKind(value) }, label = { Text(label) })
                        }
                    }
                    if (pairBalance != null) {
                        val summary = when (pairBalance.signum()) {
                            1 -> "You owe ${formatLedgerAmount(pairBalance)}"
                            -1 -> "You're owed ${formatLedgerAmount(pairBalance.abs())}"
                            else -> "No outstanding balance"
                        }
                        Text(summary, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (entries.isEmpty()) item {
                    when {
                        loading -> ListSkeleton("Loading transactions", kind = com.example.hisab.ui.components.SkeletonKind.TRANSACTIONS, rows = 2)
                        failure != null -> Column {
                            Text(failure.message)
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                        else -> Text("No transactions match this filter.")
                    }
                }
                items(entries, key = { it.id }, contentType = { "transaction" }) { entry ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val type = when (entry.type) {
                                "DIRECT_DEBT" -> "Direct debt"
                                "REPAYMENT" -> "Payment"
                                else -> "Expense"
                            }
                            fun name(id: String) = if (id == currentUserId) "You" else names[id] ?: otherUserName ?: "Former member"
                            val description = if (entry.type == "REPAYMENT")
                                "${name(entry.creditorId)} paid ${name(entry.debtorId)}"
                            else "${name(entry.debtorId)} owes ${name(entry.creditorId)}"
                            Text(type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(formatLedgerAmount(BigDecimal.valueOf(entry.amount)), style = MaterialTheme.typography.titleMedium)
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                            if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                            Text(formatLedgerDate(entry.timestamp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (entries.isNotEmpty()) item(key = "history-page-footer") {
                    PageFooter(nextCursor, loading, failure, onLoadMore)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
