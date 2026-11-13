package com.example.hisab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hisab.util.AppFailure

@Composable
fun LoadFailure(
    title: String,
    failure: AppFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(failure.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (failure != AppFailure.SESSION_EXPIRED) {
            Button(onClick = onRetry, enabled = retryEnabled) { Text("Try again") }
        }
    }
}

@Composable
fun RefreshFailure(failure: AppFailure, onRetry: () -> Unit, enabled: Boolean = true) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(failure.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (failure != AppFailure.SESSION_EXPIRED) {
                TextButton(onClick = onRetry, enabled = enabled) { Text("Retry") }
            }
        }
    }
}
