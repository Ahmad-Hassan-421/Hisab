package com.example.hisab.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hisab.ui.components.DynamicLogo
import com.example.hisab.ui.components.ActionLabel
import com.example.hisab.ui.components.FeedbackKind
import com.example.hisab.ui.components.LocalHisabFeedback
import com.example.hisab.util.AppFailure
import com.example.hisab.features.auth.presentation.AuthViewModel

@Composable
fun LoginScreen(
    onNavigateToDashboard: () -> Unit,
    onForgotPassword: () -> Unit,
    onHelp: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    sessionExpired: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val feedback = LocalHisabFeedback.current
    val focusManager = LocalFocusManager.current
    val confirmationFocus = remember { FocusRequester() }
    var passwordVisible by remember(uiState.isLoginMode) { mutableStateOf(false) }
    var confirmationVisible by remember(uiState.isLoginMode) { mutableStateOf(false) }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) {
            uiState.successMessage?.let { feedback?.post?.invoke(it, FeedbackKind.SUCCESS) }
            viewModel.clearSuccess()
            onNavigateToDashboard()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DynamicLogo(
                size = 140.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Hisab",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your Debt Tracker",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (uiState.isLoginMode) "Welcome Back" else "Create Account",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (sessionExpired) {
                Text(AppFailure.SESSION_EXPIRED.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!uiState.isLoginMode) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onNameChange(it) },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = uiState.email,
                onValueChange = { viewModel.onEmailChange(it) },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                trailingIcon = {
                    PasswordVisibilityToggle(passwordVisible, "password") { passwordVisible = !passwordVisible }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (uiState.isLoginMode) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = if (uiState.isLoginMode) {
                    KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.login()
                    })
                } else {
                    KeyboardActions(onNext = { confirmationFocus.requestFocus() })
                }
            )

            if (uiState.isLoginMode) {
                TextButton(onClick = onForgotPassword, enabled = !uiState.isLoading, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot password?")
                }
            }

            if (!uiState.isLoginMode) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm Password") },
                    trailingIcon = {
                        PasswordVisibilityToggle(confirmationVisible, "confirm password") { confirmationVisible = !confirmationVisible }
                    },
                    visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.register()
                    })
                )
                val canAcknowledge = uiState.privacyPolicyVersion != null && !uiState.isLoading
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = uiState.privacyAcknowledged,
                    enabled = canAcknowledge, role = Role.Checkbox, onValueChange = viewModel::acknowledgePrivacy),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.privacyAcknowledged, onCheckedChange = null, enabled = canAcknowledge)
                    Text("I acknowledge the Privacy Policy", modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onPrivacyPolicy, modifier = Modifier.align(Alignment.Start)) {
                    Text("Read Privacy Policy")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (uiState.isLoginMode) viewModel.login() else viewModel.register()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                ActionLabel(
                    label = if (uiState.isLoginMode) "Log In" else "Register",
                    busyLabel = if (uiState.isLoginMode) "Signing in…" else "Creating account…",
                    busy = uiState.isLoading
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isLoginMode) "Don't have an account?" else "Already have an account?",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { viewModel.toggleAuthMode() }) {
                    Text(
                        text = if (uiState.isLoginMode) "Sign Up" else "Log In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            TextButton(onClick = onHelp) { Text("Help and support") }
        }
    }
}

@Composable
private fun PasswordVisibilityToggle(visible: Boolean, field: String, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide $field" else "Show $field"
        )
    }
}
