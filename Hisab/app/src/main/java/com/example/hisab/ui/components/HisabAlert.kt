package com.example.hisab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

enum class AlertKind { SUCCESS, WARNING, ERROR, INFO, CONFIRM, DESTRUCTIVE }

@Composable
fun HisabAlert(
    kind: AlertKind,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    busy: Boolean = false,
    busyLabel: String = "Working…",
    confirmEnabled: Boolean = true,
    error: String? = null
) {
    val icon = when (kind) {
        AlertKind.SUCCESS -> Icons.Default.CheckCircle
        AlertKind.WARNING -> Icons.Default.Warning
        AlertKind.ERROR -> Icons.Default.Error
        AlertKind.DESTRUCTIVE -> Icons.Default.Delete
        AlertKind.INFO, AlertKind.CONFIRM -> Icons.Default.Info
    }
    val destructive = kind == AlertKind.DESTRUCTIVE
    val accent = when (kind) {
        AlertKind.ERROR, AlertKind.DESTRUCTIVE -> MaterialTheme.colorScheme.error
        AlertKind.WARNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(message)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled && !busy,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                ActionLabel(confirmLabel, busyLabel, busy)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss, enabled = !busy) { Text(label) } }
        }
    )
}
