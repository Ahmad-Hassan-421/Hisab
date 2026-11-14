package com.example.hisab.features.groups.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRecoveryTest {
    @Test
    fun firstLoadDoesNotReportAnEmptyAccountUntilTheServerResponds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = TestGroups().apply { next = CompletableDeferred() }
        val session = SessionManager()
        val viewModel = DashboardViewModel(repository, session, FailureReporter({ true }, {}))
        try {
            session.setSession("member", "user")
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.hasLoaded)
            assertNull(viewModel.uiState.value.failure)

            repository.next.complete(emptyList())
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.hasLoaded)
            assertTrue(viewModel.uiState.value.groups.isEmpty())
        } finally {
            viewModel.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switchingAccountsCancelsThePreviousLoadAndClearsItsContent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = TestGroups()
        val session = SessionManager()
        val viewModel = DashboardViewModel(repository, session, FailureReporter({ true }, {}))
        try {
            session.setSession("first", "user")
            runCurrent()
            assertEquals("existing", viewModel.uiState.value.groups.single().id)

            val oldResponse = CompletableDeferred<List<HisabGroup>>()
            repository.next = oldResponse
            viewModel.refreshGroups()
            runCurrent()
            repository.next = CompletableDeferred()
            session.setSession("second", "user")
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.groups.isEmpty())

            oldResponse.complete(listOf(HisabGroup(id = "old response")))
            runCurrent()
            assertTrue(viewModel.uiState.value.groups.isEmpty())
            repository.next.complete(listOf(HisabGroup(id = "new account")))
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("new account", viewModel.uiState.value.groups.single().id)
        } finally {
            viewModel.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun retainsDataAfterOfflineRefreshAndCoalescesRetries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = TestGroups()
        val session = SessionManager()
        val viewModel = DashboardViewModel(repository, session, FailureReporter({ false }, {}))
        try {
            session.setSession("member", "user")
            runCurrent()
            assertEquals(listOf("existing"), viewModel.uiState.value.groups.map { it.id })

            repository.next = CompletableDeferred()
            viewModel.refreshGroups()
            viewModel.refreshGroups()
            runCurrent()
            assertEquals(2, repository.reads)
            assertTrue(viewModel.uiState.value.isLoading)
            repository.next.completeExceptionally(ConnectException())
            runCurrent()
            assertEquals(AppFailure.OFFLINE, viewModel.uiState.value.failure)
            assertEquals(listOf("existing"), viewModel.uiState.value.groups.map { it.id })
            assertFalse(viewModel.uiState.value.isLoading)

            repository.next = CompletableDeferred(emptyList())
            viewModel.refreshGroups()
            runCurrent()
            assertTrue(viewModel.uiState.value.hasLoaded)
            assertTrue(viewModel.uiState.value.groups.isEmpty())
            assertNull(viewModel.uiState.value.failure)
        } finally {
            viewModel.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun ViewModel.cancel() = viewModelScope.cancel()

    private class TestGroups : GroupRepository {
        var next = CompletableDeferred(listOf(HisabGroup(id = "existing")))
        var reads = 0

        override suspend fun getGroupsForUser(userId: String): List<HisabGroup> {
            reads++
            return next.await()
        }

        override suspend fun createGroup(name: String, members: List<String>): HisabGroup = error("Unused")
        override suspend fun deleteGroup(groupId: String) = error("Unused")
        override suspend fun leaveGroup(groupId: String) = error("Unused")
    }
}
