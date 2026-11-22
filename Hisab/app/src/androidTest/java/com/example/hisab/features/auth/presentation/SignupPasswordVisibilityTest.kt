package com.example.hisab.features.auth.presentation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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

    private fun showSignupForm(state: androidx.compose.runtime.MutableState<AuthUiState>) {
        state.value = AuthUiState(isLoginMode = false, privacyPolicyVersion = "test-policy")
        compose.waitForIdle()
        compose.onNodeWithTag(AuthTestTags.SIGNUP_FULL_NAME, useUnmergedTree = true)
            .assertExists("Signup form is not displayed; the screen is still in Login mode")
        compose.onNodeWithText("Welcome back").assertDoesNotExist()
    }

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

        try {
            compose.waitForIdle()
            showSignupForm(state)

            fun field(label: String) = when (label) {
                "Full Name" -> compose.onNodeWithTag(AuthTestTags.SIGNUP_FULL_NAME, useUnmergedTree = true)
                "Email" -> compose.onNodeWithTag(AuthTestTags.SIGNUP_EMAIL, useUnmergedTree = true)
                "Password" -> compose.onNodeWithTag(AuthTestTags.SIGNUP_PASSWORD, useUnmergedTree = true)
                "Confirm Password" -> compose.onNodeWithTag(AuthTestTags.SIGNUP_CONFIRM_PASSWORD, useUnmergedTree = true)
                else -> error("Unknown field $label")
            }

            field("Full Name").performScrollTo().performTextInput("Signup QA")
            field("Email").performScrollTo().performTextInput("signup-visibility@example.invalid")
            val password = field("Password")
            val confirmation = field("Confirm Password")
            val masked = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
            password.performScrollTo().assert(masked)
            confirmation.performScrollTo().assert(masked)

            password.performScrollTo().performTextInput("Signup-QA42@")
            password.performImeAction()
            confirmation.assertIsFocused()
            confirmation.performScrollTo().performTextInput("ignup-QA42@")
            confirmation.performSemanticsAction(SemanticsActions.SetSelection) { it(0, 0, false) }
            compose.onNodeWithContentDescription("Show confirm password").performScrollTo().performClick()
            password.performScrollTo().assert(masked)
            confirmation.performScrollTo().assert(!masked)
            confirmation.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange.Zero))
            confirmation.performScrollTo().performTextInput("S")
            confirmation.assertTextContains("Signup-QA42@")

            compose.onNodeWithContentDescription("Show password").performScrollTo().performClick()
            password.performScrollTo().assert(!masked).assertTextContains("Signup-QA42@")
            compose.onNodeWithContentDescription("Hide confirm password").performScrollTo().performClick()
            password.performScrollTo().assert(!masked)
            confirmation.performScrollTo().assert(masked)

            confirmation.performSemanticsAction(SemanticsActions.SetSelection) { it(12, 12, false) }
            confirmation.performScrollTo().performTextInput("x")
            confirmation.performImeAction()
            compose.onNodeWithText("Passwords do not match").assertExists()

            compose.onNodeWithText("I acknowledge the Privacy Policy").performScrollTo().assertIsOff()
            confirmation.performTextReplacement("Signup-QA42@")
            confirmation.performImeAction()
            compose.onNodeWithText("Review and acknowledge the Privacy Policy to create an account.").assertExists()
        } catch (e: Throwable) {
            throw AssertionError(
                (e.message ?: "SignupPasswordVisibilityTest failed") + "\n" +
                    compose.onRoot(useUnmergedTree = true).printToString(),
                e
            )
        }
    }
}
