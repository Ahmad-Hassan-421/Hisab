package com.example.hisab.features.auth.presentation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Rule
import org.junit.Test

class SignupPasswordVisibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun signupPasswordsToggleIndependentlyAndKeepEditingState() {
        val state = androidx.compose.runtime.mutableStateOf(
            AuthUiState(isLoginMode = true, privacyPolicyVersion = "test-policy")
        )
        compose.setContent {
            HisabTheme {
                AuthScreen(
                    uiState = state.value,
                    onNameChange = { state.value = state.value.copy(name = it) },
                    onEmailChange = { state.value = state.value.copy(email = it) },
                    onPasswordChange = { state.value = state.value.copy(password = it) },
                    onConfirmPasswordChange = { state.value = state.value.copy(confirmPassword = it) },
                    onAcknowledgePrivacy = { state.value = state.value.copy(privacyAcknowledged = it) },
                    onToggleMode = {
                        state.value = state.value.copy(
                            isLoginMode = !state.value.isLoginMode,
                            error = null,
                            successMessage = null,
                            confirmPassword = "",
                            name = "",
                            privacyAcknowledged = false
                        )
                    },
                    onForgotPassword = {},
                    onHelp = {},
                    onPrivacyPolicy = {},
                    onLogin = {},
                    onRegister = {
                        if (state.value.password != state.value.confirmPassword) {
                            state.value = state.value.copy(error = "Passwords do not match")
                        } else if (!state.value.privacyAcknowledged) {
                            state.value = state.value.copy(error = "Review and acknowledge the Privacy Policy to create an account.")
                        }
                    }
                )
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sign Up")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sign Up").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("signup_full_name"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        fun field(label: String) = when (label) {
            "Full Name" -> compose.onNodeWithTag("signup_full_name", useUnmergedTree = true)
            "Email" -> compose.onNodeWithTag("signup_email", useUnmergedTree = true)
            "Password" -> compose.onNodeWithTag("signup_password", useUnmergedTree = true)
            "Confirm Password" -> compose.onNodeWithTag("signup_confirm_password", useUnmergedTree = true)
            else -> error("Unknown field $label")
        }

        field("Full Name").performTextInput("Signup QA")
        field("Email").performTextInput("signup-visibility@example.invalid")
        val password = field("Password")
        val confirmation = field("Confirm Password")
        val masked = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        password.assert(masked)
        confirmation.assert(masked)

        password.performTextInput("Signup-QA42@")
        password.performImeAction()
        confirmation.assertIsFocused()
        confirmation.performTextInput("ignup-QA42@")
        confirmation.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 0, false) }
        compose.onNodeWithContentDescription("Show confirm password").performClick()
        password.assert(masked)
        confirmation.assert(!masked)
        confirmation.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange.Zero))
        confirmation.performTextInput("S")
        confirmation.assertTextContains("Signup-QA42@")

        compose.onNodeWithContentDescription("Show password").performClick()
        password.assert(!masked).assertTextContains("Signup-QA42@")
        compose.onNodeWithContentDescription("Hide confirm password").performClick()
        password.assert(!masked)
        confirmation.assert(masked)

        confirmation.performSemanticsAction(SemanticsActions.SetSelection) { it(12, 12, false) }
        confirmation.performTextInput("x")
        confirmation.performImeAction()
        compose.onNodeWithText("Passwords do not match").assertExists()

        compose.onNodeWithText("I acknowledge the Privacy Policy").performScrollTo().assertIsOff()
        confirmation.performTextReplacement("Signup-QA42@")
        confirmation.performImeAction()
        compose.onNodeWithText("Review and acknowledge the Privacy Policy to create an account.").assertExists()
    }
}
