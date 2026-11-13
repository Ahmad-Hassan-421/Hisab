package com.example.hisab.features.ledger.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.data.db.User
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerRecoveryTest {
    @Test
    fun settledHistoryIsClearedOnlyAfterConfirmationAndFailureKeepsTheDialogOpen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = SessionManager().apply { setSession("member", "user") }
        val ledger = TestLedger().apply {
            entries = listOf(
                HisabEntry("debt", "group", "member", "other", 1.0),
                HisabEntry("payment", "group", "other", "member", 1.0)
            )
        }
        val viewModel = GroupDetailViewModel(
            Groups, Invitations, ledger, Users, session, FailureReporter({ true }, {}),
            SavedStateHandle(mapOf("groupId" to "group"))
        )
        try {
            runCurrent()
            assertTrue(viewModel.uiState.value.showClearRecordsDialog)
            viewModel.dismissClearRecordsDialog()
            viewModel.clearRecords()
            runCurrent()
            assertEquals(0, ledger.clears)

            viewModel.loadData()
            runCurrent()
            viewModel.clearRecords()
            viewModel.clearRecords()
            runCurrent()
            assertEquals(1, ledger.clears)
            assertTrue(viewModel.uiState.value.showClearRecordsDialog)
            assertTrue(viewModel.uiState.value.clearRecordsError != null)
            assertFalse(viewModel.uiState.value.isUpdatingLedger)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun uncertainSavePreservesTheFormAndRequiresReloadBeforeAnotherWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = SessionManager().apply { setSession("member", "user") }
        val ledger = TestLedger()
        val viewModel = GroupDetailViewModel(
            Groups, Invitations, ledger, Users, session, FailureReporter({ true }, {}),
            SavedStateHandle(mapOf("groupId" to "group"))
        )
        try {
            runCurrent()
            var confirmed = false
            val onSaved = { confirmed = true }
            viewModel.addAmountSpecific("other", 1.234, "Dinner", onSaved)
            viewModel.addAmountSpecific("other", 1.234, "Dinner", onSaved)
            runCurrent()

            assertEquals(1, ledger.writes)
            assertFalse(confirmed)
            assertTrue(viewModel.uiState.value.ledgerNeedsRefresh)
            assertFalse(viewModel.uiState.value.isUpdatingLedger)
            viewModel.addAmountSpecific("other", 1.234, "Dinner", onSaved)
            runCurrent()
            assertEquals(1, ledger.writes)

            viewModel.loadData()
            runCurrent()
            assertFalse(viewModel.uiState.value.ledgerNeedsRefresh)
            ledger.shouldFail = false
            viewModel.addAmountSpecific("other", 1.234, "Dinner", onSaved)
            runCurrent()
            assertEquals(2, ledger.writes)
            assertTrue(confirmed)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class TestLedger : HisabRepository {
        var writes = 0
        var clears = 0
        var entries = emptyList<HisabEntry>()
        var shouldFail = true
        override fun getAllEntries() = flowOf(entries)
        override fun getEntriesForGroup(groupId: String) = getAllEntries()
        override suspend fun createHisabEntry(entry: HisabEntry) {
            writes++
            if (shouldFail) throw SocketTimeoutException()
        }
        override suspend fun createBatchEntries(entries: List<HisabEntry>) = error("Unused")
        override suspend fun syncGroupEntries(groupId: String) = Unit
        override suspend fun clearGroupEntries(groupId: String) {
            clears++
            throw SocketTimeoutException()
        }
    }

    private object Groups : GroupRepository {
        override suspend fun getGroupsForUser(userId: String) =
            listOf(HisabGroup(id = "group", members = listOf("member", "other")))
        override suspend fun createGroup(name: String, members: List<String>) = error("Unused")
        override suspend fun deleteGroup(groupId: String) = error("Unused")
        override suspend fun leaveGroup(groupId: String) = error("Unused")
    }

    private object Invitations : RequestRepository {
        override suspend fun sendRequest(receiverUniqueId: String, groupId: String) = error("Unused")
        override suspend fun getPendingRequests(receiverId: String) = error("Unused")
        override suspend fun acceptRequest(requestId: String) = error("Unused")
        override suspend fun declineRequest(requestId: String) = error("Unused")
    }

    private object Users : UserRepository {
        override fun getCurrentUser(userId: String) = flowOf(User(userId, userId, ""))
        override suspend fun fetchRemoteUser(userId: String, allowCached: Boolean) = User(userId, userId, "")
        override suspend fun createUserDocument(user: User) = error("Unused")
        override suspend fun saveUser(user: User) = error("Unused")
        override suspend fun updateUser(user: User) = error("Unused")
        override suspend fun uploadProfilePicture(uri: Uri) = error("Unused")
        override suspend fun deleteUser(userId: String) = error("Unused")
    }
}
