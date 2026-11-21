package com.example.hisab.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun splashRendersAndTransitionsToLogin() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Sign Up")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Help and support")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
