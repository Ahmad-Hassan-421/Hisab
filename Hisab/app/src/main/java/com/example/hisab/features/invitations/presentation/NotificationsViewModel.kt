package com.example.hisab.features.invitations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.util.InviteNotificationHelper
import com.example.hisab.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val requestRepository: RequestRepository,
    private val sessionManager: SessionManager,
    private val inviteNotificationHelper: InviteNotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private val notifiedRequestIds = mutableSetOf<String>()

    init {
        startPollingRequests()
    }

    private fun startPollingRequests() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                if (userId == null) {
                    _uiState.value = NotificationsUiState()
                    notifiedRequestIds.clear()
                    return@collectLatest
                }
                while (isActive) {
                    try {
                        val requests = requestRepository.getPendingRequests(userId)
                        // Trigger rich notification for newly detected requests
                        requests.forEach { req ->
                            if (req.id.isNotBlank() && req.id !in notifiedRequestIds) {
                                notifiedRequestIds.add(req.id)
                                inviteNotificationHelper.showInviteNotification(req)
                            }
                        }
                        _uiState.update { it.copy(requests = requests, isLoading = false, error = null) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                    }
                    delay(15_000)
                }
            }
        }
    }

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
                _uiState.update {
                    it.copy(
                        pendingActionRequestIds = it.pendingActionRequestIds - request.id,
                        error = e.localizedMessage
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
                _uiState.update {
                    it.copy(
                        pendingActionRequestIds = it.pendingActionRequestIds - request.id,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }
}
