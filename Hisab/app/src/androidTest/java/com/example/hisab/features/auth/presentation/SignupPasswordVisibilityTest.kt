package com.example.hisab.features.auth.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupPasswordVisibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun signupPasswordsToggleIndependentlyAndKeepEditingState() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sign Up")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sign Up").performClick()
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

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
}
