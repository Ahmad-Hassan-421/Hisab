package com.example.hisab.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionCheckingAndFailureDoNotDisplayPolicyReview() {
        val checking = mutableStateOf(true)
        var retries = 0
        compose.setContent { HisabTheme {
            StartupStatusScreen(checking.value, "Check your connection.", { retries++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Getting Hisab ready").assertIsDisplayed()
        compose.onNodeWithText("Privacy Policy").assertDoesNotExist()
        compose.onNodeWithText("Acknowledge Privacy Policy").assertDoesNotExist()
        compose.runOnIdle { checking.value = false }
        compose.onNodeWithText("Couldn't finish startup").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("Acknowledge Privacy Policy").assertDoesNotExist()
    }
}
