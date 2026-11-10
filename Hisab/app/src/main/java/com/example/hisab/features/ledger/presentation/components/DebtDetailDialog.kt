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
