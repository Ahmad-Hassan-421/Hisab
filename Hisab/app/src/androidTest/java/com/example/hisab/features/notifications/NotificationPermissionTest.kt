package com.example.hisab.features.notifications

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPermissionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explanationRequiresAnExplicitChoiceAndNotNowDoesNotEnableAnything() {
        var enabled = 0; var declined = 0
        compose.setContent { HisabTheme { NotificationPermissionExplanation({ enabled++ }, { declined++ }) } }
        compose.onNodeWithText("Stay Updated with Hisab").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertEquals(0, enabled); assertEquals(1, declined) }
        compose.onNodeWithText("Enable notifications").performClick()
        compose.runOnIdle { assertEquals(1, enabled) }
    }
}
