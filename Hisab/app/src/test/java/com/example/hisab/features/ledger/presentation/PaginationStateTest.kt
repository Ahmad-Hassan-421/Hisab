package com.example.hisab.features.ledger.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.data.db.User
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.groups.data.HisabGroupDto
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.features.ledger.data.LedgerOverview
import com.example.hisab.features.ledger.data.PairBalanceDto
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.features.profile.data.UserDto
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
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.net.ConnectException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PaginationStateTest {
    @Test
    fun balancesStayAuthoritativeWhenOnlyOneHistoryPageIsLoadedAndRetryDeduplicates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val ledger = Ledger()
        val viewModel = model(ledger)
        try {
            runCurrent()
            assertEquals(BigDecimal("1025.000"), viewModel.uiState.value.members.first().totalDebt)
            assertEquals(10_000L, viewModel.uiState.value.entryCount)
            assertEquals("Carol", viewModel.paymentParticipants().first { it.user.id == "carol" }.user.name)
            viewModel.loadHistory()
            runCurrent()
            assertEquals(1, viewModel.uiState.value.historyEntries.size)
            assertEquals(BigDecimal("1025.000"), viewModel.uiState.value.members.first().totalDebt)
            ledger.failNext = true
            viewModel.loadMoreHistory()
            viewModel.loadMoreHistory()
            runCurrent()
            assertEquals(2, ledger.pageCalls)
            assertEquals(1, viewModel.uiState.value.historyEntries.size)
            assertNotNull(viewModel.uiState.value.historyFailure)
            viewModel.loadMoreHistory()
            runCurrent()
            assertEquals(2, viewModel.uiState.value.historyEntries.size)
            assertNull(viewModel.uiState.value.historyCursor)
            assertNull(viewModel.uiState.value.historyFailure)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun memberPaginationCannotClearAnUncertainWriteAndAccountSwitchDropsPrivatePages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val ledger = Ledger()
        val session = SessionManager().apply { setSession("actor", "user") }
        val viewModel = model(ledger, session)
        try {
            runCurrent()
            viewModel.addAmountSpecific("bob", 1.0, "Dinner") {}
            runCurrent()
            assertTrue(viewModel.uiState.value.ledgerNeedsRefresh)
            viewModel.loadMoreMembers()
            runCurrent()
            assertEquals(3, viewModel.uiState.value.members.size)
            assertTrue(viewModel.uiState.value.ledgerNeedsRefresh)
            viewModel.loadHistory()
            runCurrent()
            session.clear()
            runCurrent()
            assertTrue(viewModel.uiState.value.members.isEmpty())
            assertTrue(viewModel.uiState.value.historyEntries.isEmpty())
            assertFalse(viewModel.uiState.value.historyOpen)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun model(ledger: Ledger, session: SessionManager = SessionManager().apply { setSession("actor", "user") }) =
        GroupDetailViewModel(Groups, Requests, ledger, Users, session, FailureReporter({ true }, {}),
            SavedStateHandle(mapOf("groupId" to "group")))

    private class Ledger : HisabRepository {
        var failNext = false
        var pageCalls = 0
        private val first = HisabEntry("first", "group", "actor", "bob", 1.0)
        override suspend fun getOverview(groupId: String) = LedgerOverview(
            HisabGroupDto("group", "Friends", listOf("actor", "bob", "carol"), createdAt = 0),
            UserDto("actor", "Alice", "alice@example.invalid"),
            listOf(PairBalanceDto("actor", "bob", "1000.000"), PairBalanceDto("actor", "carol", "25.000")),
            mapOf("actor" to "Alice", "bob" to "Bob", "carol" to "Carol"), 10_000, false, 5)
        override suspend fun getMembersPage(groupId: String, cursor: String?) = if (cursor == null)
            CursorPage(listOf(User("bob", "Bob", "")), "members-next")
            else CursorPage(listOf(User("bob", "Bob", ""), User("carol", "Carol", "")))
        override suspend fun getEntriesPage(groupId: String, cursor: String?, kind: String, otherUserId: String?): CursorPage<HisabEntry> {
            pageCalls++
            if (failNext) { failNext = false; throw ConnectException() }
            return if (cursor == null) CursorPage(listOf(first), "next", 5)
                else CursorPage(listOf(first, first.copy(id = "second")), null, 5)
        }
        override fun getAllEntries() = flowOf(emptyList<HisabEntry>())
        override fun getEntriesForGroup(groupId: String) = getAllEntries()
        override suspend fun createHisabEntry(entry: HisabEntry): Unit = throw SocketTimeoutException()
        override suspend fun createBatchEntries(entries: List<HisabEntry>) = error("Unused")
        override suspend fun syncGroupEntries(groupId: String) = error("Paged mode must not download full history")
        override suspend fun clearGroupEntries(groupId: String) = error("Unused")
    }

    private object Groups : GroupRepository {
        override suspend fun getGroupsForUser(userId: String): List<HisabGroup> = error("Paged detail must not download all Hisabs")
        override suspend fun createGroup(name: String, members: List<String>) = error("Unused")
        override suspend fun deleteGroup(groupId: String) = error("Unused")
        override suspend fun leaveGroup(groupId: String) = error("Unused")
    }
    private object Requests : RequestRepository {
        override suspend fun sendRequest(receiverUniqueId: String, groupId: String) = error("Unused")
        override suspend fun getPendingRequests(receiverId: String) = error("Unused")
        override suspend fun acceptRequest(requestId: String) = error("Unused")
        override suspend fun declineRequest(requestId: String) = error("Unused")
    }
    private object Users : UserRepository {
        override fun getCurrentUser(userId: String) = flowOf<User?>(null)
        override suspend fun fetchRemoteUser(userId: String, allowCached: Boolean) = error("Paged profiles must be batched")
        override suspend fun createUserDocument(user: User) = error("Unused")
        override suspend fun saveUser(user: User) = error("Unused")
        override suspend fun updateUser(user: User) = error("Unused")
        override suspend fun deleteUser(userId: String) = error("Unused")
        override suspend fun uploadProfilePicture(uri: Uri) = error("Unused")
    }
}
