package com.example.hisab.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.ui.components.*
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destructiveConfirmationCannotSubmitWhileBusyAndCancelDoesNotConfirm() {
        val busy = mutableStateOf(true)
        var confirms = 0
        var cancels = 0
        compose.setContent { HisabTheme(darkTheme = true) {
            HisabAlert(AlertKind.DESTRUCTIVE, "Delete test copy?", "This cannot be undone.",
                onDismiss = { cancels++ }, confirmLabel = "Permanently delete", onConfirm = { confirms++ },
                dismissLabel = "Cancel", busy = busy.value, busyLabel = "Deleting…")
        } }
        compose.onNodeWithText("Deleting…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, confirms) }
    }

    @Test fun errorFeedbackIsAnnouncedAndCanBeDismissedInLightTheme() {
        compose.setContent { HisabTheme(darkTheme = false) {
            val feedback = rememberHisabFeedback()
            LaunchedEffect(Unit) { feedback.post("Check your connection.", FeedbackKind.ERROR) }
            HisabFeedbackHost(feedback.state)
        } }
        compose.onNodeWithContentDescription("Error. Check your connection.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dismiss error message").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Error. Check your connection.").assertDoesNotExist()
    }
}
