package com.example.hisab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.features.privacy.presentation.PrivacyGate
import com.example.hisab.ui.components.ActionLabel

internal fun startupResolved(restoring: Boolean, accountId: String?, privacy: PrivacyGate): Boolean =
    !restoring && (accountId == null || privacy != PrivacyGate.Checking)

@Composable
fun StartupStatusScreen(checking: Boolean, message: String?, onRetry: () -> Unit,
    onAccount: () -> Unit, onData: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (checking) "Getting Hisab ready" else "Couldn't finish startup", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (checking) ActionLabel("", "Checking your account…", true)
        else {
            Text(message ?: "Check your connection and try again.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onAccount) { Text("View account profile") }
            TextButton(onClick = onData) { Text("Privacy and data") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
