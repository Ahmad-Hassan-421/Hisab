package com.example.hisab.features.privacy.presentation

import androidx.lifecycle.viewModelScope
import com.example.hisab.data.remote.api.CursorPage
import com.example.hisab.features.privacy.data.*
import com.example.hisab.features.ledger.data.HisabEntryDto
import com.example.hisab.features.profile.data.UserDto
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDataStateTest {
    @Test fun recoveryAvailabilityUsesServerTimeWhenThePhoneClockIsWrong() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = Source().apply { expired = true; serverTime = 0 }
        val vm = model(source, SessionManager().apply { setSession("alice", "user") })
        try {
            runCurrent(); vm.open(DataPanel.ARCHIVES); runCurrent()
            assertEquals(1, vm.state.value.cards.size)
            source.serverTime = 2
            vm.refresh(background = true); runCurrent()
            assertTrue(vm.state.value.cards.isEmpty())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun failedInitialLoadRemainsAnErrorUntilAnExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = Source().apply { fail = true }
        val vm = model(source, SessionManager().apply { setSession("alice", "user") })
        try {
            runCurrent(); vm.open(DataPanel.ARCHIVES); runCurrent()
            assertNotNull(vm.state.value.failure)
            vm.refresh(background = true); runCurrent()
            assertEquals(1, source.calls)
            assertNotNull(vm.state.value.failure)
            assertFalse(vm.state.value.loading)
            vm.refresh(); runCurrent()
            assertEquals(1, vm.state.value.cards.size)
            assertNull(vm.state.value.failure)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun expiredArchiveIsRemovedDuringQuietRefreshAndNeverOfferedAsRecoverable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = Source()
        val vm = model(source, SessionManager().apply { setSession("alice", "user") })
        try {
            runCurrent(); vm.open(DataPanel.ARCHIVES); runCurrent()
            assertEquals(1, vm.state.value.cards.size)
            source.expired = true
            vm.refresh(background = true); runCurrent()
            assertTrue(vm.state.value.cards.isEmpty())
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.failure)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun pagesCoalesceRetryWithoutLosingRowsAndAccountChangeClearsPrivateData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = Source()
        val session = SessionManager().apply { setSession("alice", "user") }
        val vm = model(source, session)
        try {
            runCurrent(); vm.open(DataPanel.ARCHIVES); runCurrent()
            assertEquals(listOf("one"), vm.state.value.cards.map { it.id })
            source.fail = true
            vm.more(); vm.more(); runCurrent()
            assertEquals(2, source.calls)
            assertEquals(1, vm.state.value.cards.size)
            assertNotNull(vm.state.value.pageFailure)
            vm.more(); runCurrent()
            assertEquals(listOf("one", "two"), vm.state.value.cards.map { it.id })
            assertNull(vm.state.value.cursor)
            session.setSession("bob", "user"); runCurrent()
            assertEquals(DataPanel.HOME, vm.state.value.panel)
            assertTrue(vm.state.value.cards.isEmpty())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun failedRecoveryNeverShowsSubmittedAndDeletionRequiresSelectedSessionOwner() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = Source()
        val session = SessionManager().apply { setSession("alice", "user") }
        val erased = mutableListOf<String>()
        val vm = AccountDataViewModel(source, object : AccountLocalCleanup {
            override suspend fun erase(accountId: String) { erased += accountId }
        }, session, FailureReporter({ true }, {}))
        try {
            runCurrent(); vm.open(DataPanel.ARCHIVES); runCurrent()
            source.fail = true
            vm.recover(source.one); runCurrent()
            assertEquals(DataPanel.ARCHIVES, vm.state.value.panel)
            assertNull(vm.state.value.receipt)
            assertNotNull(vm.state.value.message)
            vm.recover(source.one); runCurrent()
            assertEquals(DataPanel.SUBMITTED, vm.state.value.panel)
            assertEquals("one", vm.state.value.receipt?.id)
            vm.deleteAccount("bob", "password"); runCurrent()
            assertEquals(0, source.deletions)
            assertTrue(erased.isEmpty())
            vm.deleteAccount("alice", "password"); runCurrent()
            assertEquals(1, source.deletions)
            assertEquals(listOf("alice"), erased)
            assertNull(session.currentUserId.value)
            assertTrue(session.requiresSignIn.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun model(source: Source, session: SessionManager) = AccountDataViewModel(source,
        object : AccountLocalCleanup { override suspend fun erase(accountId: String) = Unit }, session, FailureReporter({ true }, {}))
    private class Source : AccountDataSource {
        val one = ArchiveCard("one", "Friends", 0, System.currentTimeMillis() + 60_000, null, 1205, 2, "available")
        val two = one.copy(id = "two")
        var calls = 0
        var fail = false
        var deletions = 0
        var expired = false
        var serverTime: Long? = null
        private fun failOnce() { if (fail) { fail = false; throw ConnectException() } }
        override suspend fun archives(cursor: String?): CursorPage<ArchiveCard> {
            calls++; failOnce()
            return if (cursor == null) CursorPage(listOf(if (expired) one.copy(expiresAt = 1) else one), "next", serverTime = serverTime) else CursorPage(listOf(one, two), serverTime = serverTime)
        }
        override suspend fun recovered(cursor: String?) = archives(cursor)
        override suspend fun entries(id: String, kind: String, cursor: String?) = CursorPage<HisabEntryDto>(emptyList())
        override suspend fun members(id: String, cursor: String?) = CursorPage<HistoricalMember>(emptyList())
        override suspend fun recover(id: String): RecoveryReceipt { failOnce(); return RecoveryReceipt(id, one.name, "pending", 180000) }
        override suspend fun deleteCopy(id: String) = Unit
        override suspend fun accounts() = listOf(UserDto("alice", "Alice", "alice@example.invalid"))
        override suspend fun deleteAccount(input: DeleteAccountInput) { deletions++ }
    }
}
