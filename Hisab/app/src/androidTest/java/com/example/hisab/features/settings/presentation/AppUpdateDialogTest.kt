package com.example.hisab.features.settings.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.features.settings.data.VerifiedUpdate
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.UpdateDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDialogTest {
    @get:Rule val compose = createComposeRule()
    private val release = VerifiedUpdate(5, "1.2.2", "New features\nPerformance improvements\nBug fixes",
        "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/v1.2.2/hisab-v1.2.2.apk", 4096, "a".repeat(64),
        releaseDate = "2026-11-28T00:00:00Z")

    @Test fun optionalReleaseShowsMetadataAndRequiresExplicitDownload() {
        var downloads = 0; var dismissed = 0
        compose.setContent { HisabTheme { AppUpdateDialog(UpdateUiState(checked = true, release = release),
            UpdateDownloadStatus.Idle, "1.2.1", 4, { dismissed++ }, {}, { downloads++ }, {}, {}) } }
        compose.onNodeWithText("A new update is available").assertIsDisplayed()
        compose.onNodeWithText("Installed version: 1.2.1").assertIsDisplayed()
        compose.onNodeWithText("Latest version: 1.2.2").assertIsDisplayed()
        compose.onNodeWithText("What’s new").assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.runOnIdle { assertEquals(0, downloads); assertEquals(1, dismissed) }
        compose.onNodeWithText("Update now").performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun requiredReleaseRetainsRetryAndHasNoOptionalDismissal() {
        var retries = 0
        compose.setContent { HisabTheme { AppUpdateDialog(UpdateUiState(checked = true, release = release.copy(required = true)),
            UpdateDownloadStatus.Error("Download interrupted"), "1.2.1", 4, {}, {}, { retries++ }, {}, {}) } }
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithText("Retry download").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
