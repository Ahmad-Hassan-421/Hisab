package com.example.hisab.features.invitations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.invitations.domain.RequestRepository
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
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val requestRepository: RequestRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        startPollingRequests()
    }

    private fun startPollingRequests() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                if (userId == null) {
                    _uiState.value = NotificationsUiState()
                    return@collectLatest
                }
                while (isActive) {
                    try {
                        val requests = requestRepository.getPendingRequests(userId)
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
        viewModelScope.launch {
            try {
                requestRepository.acceptRequest(request.id)
                _uiState.update { state ->
                    state.copy(requests = state.requests.filter { it.id != request.id })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage) }
            }
        }
    }

    fun declineRequest(request: HisabRequest) {
        viewModelScope.launch {
            try {
                requestRepository.declineRequest(request.id)
                _uiState.update { state ->
                    state.copy(requests = state.requests.filter { it.id != request.id })
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage) }
            }
        }
    }
}
