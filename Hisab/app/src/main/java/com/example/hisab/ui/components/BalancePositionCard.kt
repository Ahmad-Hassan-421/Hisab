package com.example.hisab.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.presentation.components.formatLedgerAmount
import java.math.BigDecimal

@Composable
fun BalancePositionCard(title: String, description: String, debt: BigDecimal, receive: BigDecimal,
    modifier: Modifier = Modifier, footer: String? = null) {
    ElevatedCard(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PositionMetric("You owe", debt, MaterialTheme.colorScheme.error, true, Modifier.weight(1f))
                PositionMetric("To receive", receive, MaterialTheme.colorScheme.primary, false, Modifier.weight(1f))
            }
            footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PositionMetric(label: String, value: BigDecimal, color: Color, outgoing: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (outgoing) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null,
            tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatLedgerAmount(value), color = color,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
    }
}
