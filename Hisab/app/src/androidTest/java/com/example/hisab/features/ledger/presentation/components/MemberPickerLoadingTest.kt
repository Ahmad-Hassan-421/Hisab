package com.example.hisab.features.ledger.presentation.components

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
class MemberPickerLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anEmptyEligiblePageStillLoadsTheNextPageBeforeShowingEmpty() {
        var cursor by mutableStateOf<String?>("next-page")
        var loads = 0
        compose.setContent { HisabTheme {
            LedgerMemberPicker(emptyList(), "", nextCursor = cursor, onLoadMore = { loads++; cursor = null }) { }
        } }
        compose.onNodeWithText("Select a member").performClick()
        compose.waitUntil(5_000) { loads == 1 }
        compose.onNodeWithText("No eligible members.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, loads) }
    }
}
