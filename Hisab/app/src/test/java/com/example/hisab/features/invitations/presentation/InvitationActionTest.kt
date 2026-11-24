package com.example.hisab.features.invitations.presentation

import androidx.lifecycle.viewModelScope
import com.example.hisab.features.invitations.domain.*
import com.example.hisab.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationActionTest {
    @Test fun rapidDecisionTapsSubmitOnceAndSuccessAppearsOnlyAfterServerConfirmation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Requests()
        val session = SessionManager().apply { setSession("actor", "user") }
        val vm = model(repository, session)
        try {
            runCurrent()
            vm.acceptRequest(repository.request); vm.acceptRequest(repository.request); runCurrent()
            assertEquals(1, repository.decisions)
            assertNull(vm.uiState.value.successMessage)
            assertEquals(setOf("request"), vm.uiState.value.pendingActionRequestIds)
            repository.result.completeExceptionally(ConnectException()); runCurrent()
            assertNull(vm.uiState.value.successMessage)
            assertNotNull(vm.uiState.value.error)
            assertEquals(1, vm.uiState.value.requests.size)
            repository.result = CompletableDeferred()
            vm.acceptRequest(repository.request); runCurrent()
            repository.result.complete(repository.request.copy(status = "accepted")); runCurrent()
            assertEquals(2, repository.decisions)
            assertTrue(vm.uiState.value.requests.isEmpty())
            assertTrue(vm.uiState.value.successMessage!!.contains("Friends"))
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun switchingAccountsCancelsDecisionWithoutFalseErrorOrLeakedFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Requests()
        val session = SessionManager().apply { setSession("actor", "user") }
        val vm = model(repository, session)
        try {
            runCurrent(); vm.declineRequest(repository.request); runCurrent()
            session.clear(); runCurrent()
            repository.result.complete(repository.request.copy(status = "declined")); runCurrent()
            assertTrue(vm.uiState.value.requests.isEmpty())
            assertTrue(vm.uiState.value.pendingActionRequestIds.isEmpty())
            assertNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.successMessage)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun model(requests: Requests, session: SessionManager) = NotificationsViewModel(requests, session,
        object : InvitationAlerts { override fun showInviteNotification(request: HisabRequest) = Unit }, FailureReporter({ true }, {}))
    private class Requests : RequestRepository {
        val request = HisabRequest(id = "request", receiverId = "actor", groupName = "Friends")
        var decisions = 0
        var result = CompletableDeferred<HisabRequest>()
        override suspend fun getPendingRequests(receiverId: String) = listOf(request)
        override suspend fun sendRequest(receiverUniqueId: String, groupId: String): HisabRequest = error("Unused")
        override suspend fun acceptRequest(requestId: String): HisabRequest { decisions++; return result.await() }
        override suspend fun declineRequest(requestId: String) = acceptRequest(requestId)
    }
}
