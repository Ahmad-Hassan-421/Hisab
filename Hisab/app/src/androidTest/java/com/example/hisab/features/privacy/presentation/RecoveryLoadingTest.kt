package com.example.hisab.features.privacy.presentation

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.privacy.data.*
import com.example.hisab.features.ledger.data.HisabEntryDto
import com.example.hisab.features.profile.data.UserDto
import com.example.hisab.ui.theme.HisabTheme
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ConnectException

@RunWith(AndroidJUnit4::class)
class RecoveryLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recoverySkeletonEndsOnFailureAndRetryShowsTheRealEmptyState() {
        val source = Source()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = AccountDataViewModel(source, object : AccountLocalCleanup { override suspend fun erase(accountId: String) = Unit },
            SessionManager().apply { setSession("test-owner", "user") }, FailureReporter(context))
        try {
            compose.setContent { HisabTheme(darkTheme = true) { AccountDataScreen({}, {}, vm) } }
            compose.onNodeWithText("Retrieve a Copy of My Data").performClick()
            compose.onNodeWithContentDescription("Loading recover your hisab data").assertIsDisplayed()
            compose.onNodeWithText("No recoverable Hisabs found.", substring = true).assertDoesNotExist()
            compose.runOnIdle { source.result.completeExceptionally(ConnectException()) }
            compose.onNodeWithContentDescription("Loading recover your hisab data").assertDoesNotExist()
            compose.runOnIdle { source.result = CompletableDeferred(CursorPage(emptyList())) }
            compose.onNodeWithText("Retry").performClick()
            compose.onNodeWithText("No recoverable Hisabs found.", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Loading recover your hisab data").assertDoesNotExist()
        } finally { compose.runOnIdle { vm.viewModelScope.cancel() } }
    }

    private class Source : AccountDataSource {
        var result = CompletableDeferred<CursorPage<ArchiveCard>>()
        override suspend fun archives(cursor: String?) = result.await()
        override suspend fun recovered(cursor: String?) = result.await()
        override suspend fun entries(id: String, kind: String, cursor: String?) = CursorPage<HisabEntryDto>(emptyList())
        override suspend fun members(id: String, cursor: String?) = CursorPage<HistoricalMember>(emptyList())
        override suspend fun recover(id: String): RecoveryReceipt = error("Unused")
        override suspend fun deleteCopy(id: String) = Unit
        override suspend fun accounts() = emptyList<UserDto>()
        override suspend fun deleteAccount(input: DeleteAccountInput) = Unit
    }
}
