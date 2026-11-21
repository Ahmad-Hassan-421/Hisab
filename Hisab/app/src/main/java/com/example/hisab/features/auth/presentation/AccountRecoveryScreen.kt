package com.example.hisab.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.ui.components.ActionLabel

@Composable
fun AccountRecoveryScreen(onBack: () -> Unit, onHelp: () -> Unit, viewModel: AccountRecoveryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    RecoveryForm("Reset password", onBack, state.error) {
        when (state.step) {
            RecoveryStep.Request -> {
                Text("Enter your registered email. Hisab can send a reset code to a verified recovery address.")
                RecoveryField(state.email, viewModel::emailChanged, "Registered email", KeyboardType.Email, !state.busy)
                RecoveryButton("Send reset code", "Requesting code…", state.busy, viewModel::requestReset)
                TextButton(onClick = viewModel::enterCode, enabled = !state.busy) { Text("I already have a code") }
            }
            RecoveryStep.Sent -> {
                Text("If this address is verified for recovery, check your inbox and spam folder for a code. If you haven’t verified your email, contact Hisab support for help.")
                RecoveryButton("Enter reset code", "", false, viewModel::enterCode)
                TextButton(onClick = viewModel::requestAgain) { Text("Use another email") }
                TextButton(onClick = onHelp) { Text("Help with account recovery") }
            }
            RecoveryStep.EnterCode -> {
                Text("Paste the code from your email and choose a new password.")
                RecoveryField(state.email, viewModel::emailChanged, "Registered email", KeyboardType.Email, !state.busy)
                RecoveryField(state.code, viewModel::codeChanged, "Reset code", KeyboardType.Password, !state.busy)
                RecoveryField(state.password, viewModel::passwordChanged, "New password", KeyboardType.Password, !state.busy)
                RecoveryField(state.confirmation, viewModel::confirmationChanged, "Confirm new password", KeyboardType.Password, !state.busy)
                Text("Use 8 or more characters with a letter, a number and a special character.", style = MaterialTheme.typography.bodySmall)
                RecoveryButton("Reset password", "Updating password…", state.busy, viewModel::resetPassword)
                TextButton(onClick = viewModel::requestAgain, enabled = !state.busy) { Text("Request a new code") }
            }
            RecoveryStep.Complete -> {
                Text("Password updated", style = MaterialTheme.typography.headlineSmall)
                Text("You're signed out of your previous sessions. Sign in with your new password.")
                RecoveryButton("Back to login", "", false, onBack)
            }
        }
    }
}

@Composable
fun VerifyRecoveryEmailScreen(onBack: () -> Unit, viewModel: AccountRecoveryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadVerification() }
    RecoveryForm("Verify recovery email", onBack, state.error) {
        when {
            !state.verificationStatusLoaded -> RecoveryButton("Retry", "Checking verification…", state.checkingVerification, viewModel::loadVerification)
            state.step == RecoveryStep.Complete -> {
                Text("Recovery email verified", style = MaterialTheme.typography.headlineSmall)
                Text("You can use this address to reset your password.")
                RecoveryButton("Done", "", false, onBack)
            }
            state.step == RecoveryStep.EnterCode -> {
                Text("Check your registered email and paste the verification code.")
                RecoveryField(state.code, viewModel::codeChanged, "Verification code", KeyboardType.Password, !state.busy)
                RecoveryButton("Verify email", "Verifying email…", state.busy, viewModel::verifyEmail)
                TextButton(onClick = viewModel::requestAgain, enabled = !state.busy) { Text("Request a new code") }
            }
            else -> {
                Text("Verify your registered email so you can recover access if you forget your password.")
                RecoveryButton("Send verification code", "Sending code…", state.busy, viewModel::requestVerification)
                TextButton(onClick = viewModel::enterCode, enabled = !state.busy) { Text("I already have a code") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryForm(title: String, onBack: () -> Unit, error: String?, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            content()
        }
    }
}

@Composable
private fun RecoveryField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType, enabled: Boolean) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (keyboard == KeyboardType.Password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable
private fun RecoveryButton(label: String, pending: String, busy: Boolean, action: () -> Unit) {
    val focus = LocalFocusManager.current
    Button(onClick = { focus.clearFocus(); action() }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
        ActionLabel(label, pending, busy)
    }
}
