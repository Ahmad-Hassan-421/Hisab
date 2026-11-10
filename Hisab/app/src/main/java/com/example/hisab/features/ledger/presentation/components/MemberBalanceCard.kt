package com.example.hisab.features.ledger.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.features.ledger.presentation.MemberBalanceUI

@Composable
fun MemberBalanceCard(
    balance: MemberBalanceUI,
    onToggleExpand: () -> Unit,
    onShowDetails: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = balance.user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = "ID: ${balance.user.uniqueId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.clickable { onToggleExpand() }
                    ) {
                        Text("Debt", fontSize = 12.sp, color = Color.Red)
                        Text("PKR ${String.format("%.3f", balance.totalDebt)}", fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.clickable { onToggleExpand() }
                    ) {
                        Text("Will Receive", fontSize = 12.sp, color = Color(0xFF4CAF50)) // Green
                        Text("PKR ${String.format("%.3f", balance.totalReceive)}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
            
            AnimatedVisibility(visible = balance.isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (balance.debtBreakdown.isNotEmpty()) {
                        Text("Owes to:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        balance.debtBreakdown.forEach { debt ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(debt.userName, fontSize = 14.sp)
                                Text("PKR ${String.format("%.3f", debt.amount)}", color = Color.Red, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (balance.receiveBreakdown.isNotEmpty()) {
                        Text("Owed by:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        balance.receiveBreakdown.forEach { receive ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(receive.userName, fontSize = 14.sp)
                                Text("PKR ${String.format("%.3f", receive.amount)}", color = Color(0xFF4CAF50), fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    TextButton(onClick = { onShowDetails(balance.user.id, balance.user.name) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Detail about Debt")
                    }
                }
            }
        }
    }
}
