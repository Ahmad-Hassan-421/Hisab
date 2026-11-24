package com.example.hisab.features.privacy.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hisab.features.privacy.data.PrivacyDocument
import com.example.hisab.features.settings.presentation.InformationScreen
import com.example.hisab.ui.components.ActionLabel

@Composable
fun PrivacyPolicyScreen(viewModel: PrivacyViewModel, onBack: () -> Unit) {
    val policy by viewModel.document.collectAsState()
    LaunchedEffect(Unit) { viewModel.viewPolicy() }
    InformationScreen("Privacy Policy", onBack) {
        policy?.let { PrivacyPolicyContent(it) } ?: CircularProgressIndicator(Modifier.padding(16.dp))
    }
}

@Composable
fun PrivacyReviewScreen(viewModel: PrivacyViewModel, onAccount: () -> Unit, onData: () -> Unit, onSignOut: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val policy by viewModel.document.collectAsState()
    var options by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.gate == PrivacyGate.Review && !options) { options = true }
    InformationScreen(if (options) "Account and data options" else "Privacy Policy", {
        if (options) options = false else options = true
    }) {
        if (options) {
            Text("You can review your account, submit a privacy request, or sign out. To return to shared ledgers, review and acknowledge the current policy.")
            Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("View account profile") }
            OutlinedButton(onClick = onData, modifier = Modifier.fillMaxWidth()) { Text("Privacy and data requests") }
            TextButton(onClick = { options = false }) { Text("Return to policy") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        } else {
            when (state.gate) {
                PrivacyGate.Checking, PrivacyGate.Continue -> CircularProgressIndicator(Modifier.padding(16.dp))
                PrivacyGate.Unavailable -> {
                    Text(state.error ?: "Couldn’t check your agreement. Try again.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::checkStatus) { Text("Retry") }
                    TextButton(onClick = { options = true }) { Text("Account and data options") }
                }
                PrivacyGate.Review -> {
                    val loaded = policy
                    if (loaded == null) CircularProgressIndicator() else {
                        Text("Review this notice to continue to your shared ledgers. You can also use the account and data options below.")
                        PrivacyPolicyContent(loaded)
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { viewModel.acknowledge(loaded) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                            ActionLabel("Acknowledge Privacy Policy", "Saving acknowledgment…", state.saving)
                        }
                        TextButton(onClick = { options = true }, enabled = !state.saving) { Text("Not now") }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyDataScreen(onBack: () -> Unit, onPolicy: () -> Unit) {
    AccountDataScreen(onBack, onPolicy)
}

@Composable
private fun PrivacyPolicyContent(document: PrivacyDocument) {
    Text("Effective ${document.effectiveDate} · Version ${document.version}", style = MaterialTheme.typography.bodySmall)
    document.sections.forEach { section ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(section.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
