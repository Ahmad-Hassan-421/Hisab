package com.example.hisab.features.ledger.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.presentation.DebtDetail
import com.example.hisab.features.ledger.presentation.MemberBalanceUI
import com.example.hisab.ui.components.HisabAvatar

@Composable
fun MemberBalanceCard(
    balance: MemberBalanceUI,
    isFormerMember: Boolean,
    isCurrentUser: Boolean,
    onToggleExpand: () -> Unit,
    onShowDetails: (String, String) -> Unit
) {
    val debtColor = MaterialTheme.colorScheme.error
    val receiveColor = MaterialTheme.colorScheme.primary
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HisabAvatar(balance.user.profilePicUrl, Modifier.size(40.dp))
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text(balance.user.name, style = MaterialTheme.typography.titleMedium)
                    val subtitle = when {
                        isCurrentUser -> "You"
                        isFormerMember -> "Former member"
                        balance.user.uniqueId.isNotBlank() -> "ID: ${balance.user.uniqueId}"
                        else -> "Member"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggleExpand) {
                    val expanded = balance.isExpanded
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide balance breakdown" else "Show balance breakdown"
                    )
                }
            }
            if (balance.totalDebt.signum() > 0) {
                Text("Owes · ${formatLedgerAmount(balance.totalDebt)}", style = MaterialTheme.typography.titleSmall, color = debtColor)
            }
            if (balance.totalReceive.signum() > 0) {
                Text("To receive · ${formatLedgerAmount(balance.totalReceive)}", style = MaterialTheme.typography.titleSmall, color = receiveColor)
            }
            if (balance.totalDebt.signum() == 0 && balance.totalReceive.signum() == 0) {
                Text("Settled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(balance.isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    BalanceBreakdown("Owes to", balance.debtBreakdown, debtColor)
                    BalanceBreakdown("Owed by", balance.receiveBreakdown, receiveColor)
                    if (balance.debtBreakdown.isEmpty() && balance.receiveBreakdown.isEmpty()) {
                        Text("No outstanding balance.", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!isCurrentUser) {
                        OutlinedButton(onClick = { onShowDetails(balance.user.id, balance.user.name) }) {
                            Text("View transactions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceBreakdown(title: String, entries: List<DebtDetail>, color: Color) {
    if (entries.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, color = color)
    entries.forEach { entry ->
        Text("${entry.userName} · ${formatLedgerAmount(entry.amount)}", style = MaterialTheme.typography.bodyMedium)
    }
}
