package com.example.hisab.features.invitations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.util.InviteNotificationHelper
import com.example.hisab.util.SessionManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val requests: List<HisabRequest> = emptyList(),
    val isLoading: Boolean = true,
    val pendingActionRequestIds: Set<String> = emptySet(),
    val hasLoaded: Boolean = false,
    val loadFailure: AppFailure? = null,
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val requestRepository: RequestRepository,
    private val sessionManager: SessionManager,
    private val inviteNotificationHelper: InviteNotificationHelper,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private val notifiedRequestIds = mutableSetOf<String>()
    private var refreshJob: Job? = null

    init {
        startPollingRequests()
    }

    private fun startPollingRequests() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                refreshJob?.cancel()
                _uiState.value = NotificationsUiState()
                notifiedRequestIds.clear()
                if (userId == null) return@collectLatest
                while (isActive) {
                    refreshRequests()
                    refreshJob?.join()
                    delay(15_000)
                }
            }
        }
    }

    fun refreshRequests() {
        val userId = sessionManager.currentUserId.value ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val requests = requestRepository.getPendingRequests(userId)
                if (sessionManager.currentUserId.value != userId) return@launch
                requests.forEach { request ->
                    if (request.id.isNotBlank() && notifiedRequestIds.add(request.id)) {
                        inviteNotificationHelper.showInviteNotification(request)
                    }
                }
                _uiState.update { it.copy(requests = requests, isLoading = false, hasLoaded = true, loadFailure = null) }
            } catch (e: Exception) {
                val failure = failures.report("Load invitations", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isLoading = false, loadFailure = failure) }
                }
            }
        }
    }

    fun clearActionError() = _uiState.update { it.copy(error = null) }

    fun acceptRequest(request: HisabRequest) {
        if (request.id in _uiState.value.pendingActionRequestIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingActionRequestIds = it.pendingActionRequestIds + request.id) }
            try {
                requestRepository.acceptRequest(request.id)
                _uiState.update { state ->
                    state.copy(
                        requests = state.requests.filter { it.id != request.id },
                        pendingActionRequestIds = state.pendingActionRequestIds - request.id
                    )
                }
            } catch (e: Exception) {
                val failure = failures.report("Accept invitation", e)
                _uiState.update {
                    it.copy(
                        pendingActionRequestIds = it.pendingActionRequestIds - request.id,
                        error = failure.message
                    )
                }
            }
        }
    }

    fun declineRequest(request: HisabRequest) {
        if (request.id in _uiState.value.pendingActionRequestIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingActionRequestIds = it.pendingActionRequestIds + request.id) }
            try {
                requestRepository.declineRequest(request.id)
                _uiState.update { state ->
                    state.copy(
                        requests = state.requests.filter { it.id != request.id },
                        pendingActionRequestIds = state.pendingActionRequestIds - request.id
                    )
                }
            } catch (e: Exception) {
                val failure = failures.report("Decline invitation", e)
                _uiState.update {
                    it.copy(
                        pendingActionRequestIds = it.pendingActionRequestIds - request.id,
                        error = failure.message
                    )
                }
            }
        }
    }
}
