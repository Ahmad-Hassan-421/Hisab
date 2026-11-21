package com.example.hisab.features.settings.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.BuildConfig
import com.example.hisab.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HisabHelpTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOutHelpExpandsAnAnswerAndOpensAbout() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Help and support")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Help and support").performScrollTo().performClick()
        val question = compose.onNodeWithText("What is a Hisab?")
        question.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        question.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("A Hisab is a shared ledger.", substring = true).assertIsDisplayed()
        question.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithText("About Hisab").performScrollTo().performClick()
        compose.onNodeWithText("Created by Hantalyze").assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}").assertIsDisplayed()
        compose.onNodeWithText("Open Hisab Issues on GitHub").performScrollTo().assertIsDisplayed()
    }
}
