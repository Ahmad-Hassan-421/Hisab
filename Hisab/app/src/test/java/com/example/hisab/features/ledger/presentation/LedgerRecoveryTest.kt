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
import com.example.hisab.util.AppFailure
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun refreshKeepsBalancesVisibleAndAppliesAnExternalChangeOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = SessionManager().apply { setSession("member", "user") }
        val ledger = TestLedger()
        val initial = HisabEntry("debt", "group", "member", "other", 1.0)
        ledger.entries.value = listOf(initial)
        val viewModel = GroupDetailViewModel(
            Groups, Invitations, ledger, Users, session, FailureReporter({ false }, {}),
            SavedStateHandle(mapOf("groupId" to "group"))
        )
        try {
            runCurrent()
            viewModel.toggleMemberExpanded("member")
            val pending = CompletableDeferred<Unit>()
            ledger.sync = pending
            viewModel.loadData()
            viewModel.loadData()
            runCurrent()
            assertEquals(2, ledger.syncs)
            assertTrue(viewModel.uiState.value.isRefreshing)
            assertEquals(listOf(initial), viewModel.uiState.value.entries)

            pending.completeExceptionally(java.net.ConnectException())
            runCurrent()
            assertEquals(AppFailure.OFFLINE, viewModel.uiState.value.loadFailure)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(listOf(initial), viewModel.uiState.value.entries)

            ledger.sync = CompletableDeferred(Unit)
            val updated = initial.copy(amount = 3.0)
            ledger.entries.value = listOf(updated)
            viewModel.loadData()
            runCurrent()
            assertEquals(listOf(updated), viewModel.uiState.value.entries)
            assertEquals(java.math.BigDecimal("3.000"), viewModel.uiState.value.members.first().totalDebt)
            assertTrue(viewModel.uiState.value.members.first().isExpanded)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(null, viewModel.uiState.value.loadFailure)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settledHistoryIsClearedOnlyAfterConfirmationAndFailureKeepsTheDialogOpen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = SessionManager().apply { setSession("member", "user") }
        val ledger = TestLedger().apply {
            entries.value = listOf(
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

            val settled = ledger.entries.value
            ledger.entries.value = settled.take(1)
            runCurrent()
            ledger.entries.value = settled
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
        var syncs = 0
        var sync = CompletableDeferred(Unit)
        val entries = MutableStateFlow(emptyList<HisabEntry>())
        var shouldFail = true
        override fun getAllEntries() = entries
        override fun getEntriesForGroup(groupId: String) = getAllEntries()
        override suspend fun createHisabEntry(entry: HisabEntry) {
            writes++
            if (shouldFail) throw SocketTimeoutException()
        }
        override suspend fun createBatchEntries(entries: List<HisabEntry>) = error("Unused")
        override suspend fun syncGroupEntries(groupId: String) {
            syncs++
            sync.await()
        }
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
