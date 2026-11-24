package com.example.hisab.features.invitations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.invitations.domain.HisabRequest
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.util.InvitationAlerts
import com.example.hisab.util.SessionManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val hasLoaded: Boolean = false,
    val loadFailure: AppFailure? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val pageFailure: AppFailure? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val requestRepository: RequestRepository,
    private val sessionManager: SessionManager,
    private val inviteNotificationHelper: InvitationAlerts,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private val notifiedRequestIds = mutableSetOf<String>()
    private var refreshJob: Job? = null
    private var resetPages = false
    private val actionJobs = mutableSetOf<Job>()
    private var accountGeneration = 0

    init {
        startPollingRequests()
    }

    private fun startPollingRequests() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                accountGeneration++
                actionJobs.toList().forEach { it.cancel() }
                actionJobs.clear()
                refreshJob?.cancel()
                _uiState.value = NotificationsUiState()
                notifiedRequestIds.clear()
                if (userId == null) return@collectLatest
                while (isActive) {
                    if (!_uiState.value.hasLoaded || _uiState.subscriptionCount.value > 0) refreshRequests()
                    refreshJob?.join()
                    delay(15_000)
                }
            }
        }
    }

    fun refreshRequests() {
        val userId = sessionManager.currentUserId.value ?: return
        if (refreshJob?.isActive == true) return
        val reset = resetPages
        resetPages = false
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val page = requestRepository.getRequestsPage(userId)
                val requests = page.items
                if (sessionManager.currentUserId.value != userId) return@launch
                requests.forEach { request ->
                    if (request.id.isNotBlank() && notifiedRequestIds.add(request.id)) {
                        inviteNotificationHelper.showInviteNotification(request)
                    }
                }
                _uiState.update {
                    val preserveTail = !reset && it.requests.size > requests.size
                    it.copy(requests = (requests + if (preserveTail) it.requests else emptyList()).distinctBy { item -> item.id },
                        nextCursor = if (preserveTail) it.nextCursor else page.nextCursor,
                        isLoading = false, hasLoaded = true, loadFailure = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load invitations", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isLoading = false, loadFailure = failure) }
                }
            }
        }
    }

    fun reloadRequests() {
        refreshJob?.cancel()
        _uiState.update { it.copy(pageFailure = null, isLoadingMore = false) }
        refreshJob = null
        resetPages = true
        refreshRequests()
    }

    fun loadNextPage() {
        val userId = sessionManager.currentUserId.value ?: return
        val cursor = _uiState.value.nextCursor ?: return
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingMore = true, pageFailure = null) }
        refreshJob = viewModelScope.launch {
            try {
                val page = requestRepository.getRequestsPage(userId, cursor)
                if (sessionManager.currentUserId.value == userId) _uiState.update {
                    it.copy(requests = (it.requests + page.items).distinctBy { request -> request.id },
                        nextCursor = page.nextCursor, isLoadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load more invitations", e)
                if (sessionManager.currentUserId.value == userId) _uiState.update {
                    it.copy(isLoadingMore = false, pageFailure = failure)
                }
            }
        }
    }

    fun clearActionError() = _uiState.update { it.copy(error = null) }

    fun clearSuccess() = _uiState.update { it.copy(successMessage = null) }

    fun acceptRequest(request: HisabRequest) = decide(request, accepted = true)
    fun declineRequest(request: HisabRequest) = decide(request, accepted = false)

    private fun decide(request: HisabRequest, accepted: Boolean) {
        val actor = sessionManager.currentUserId.value ?: return
        val stamp = accountGeneration
        if (request.id in _uiState.value.pendingActionRequestIds) return
        _uiState.update { it.copy(pendingActionRequestIds = it.pendingActionRequestIds + request.id, error = null) }
        val job = viewModelScope.launch {
            try {
                if (accepted) requestRepository.acceptRequest(request.id) else requestRepository.declineRequest(request.id)
                if (sessionManager.currentUserId.value == actor && accountGeneration == stamp) _uiState.update {
                    it.copy(requests = it.requests.filterNot { row -> row.id == request.id },
                        successMessage = if (accepted) "Invitation accepted. You joined ${request.groupName}." else "Invitation declined.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (sessionManager.currentUserId.value == actor && accountGeneration == stamp) _uiState.update {
                    it.copy(error = failures.report(if (accepted) "Accept invitation" else "Decline invitation", error).message)
                }
            } finally {
                if (sessionManager.currentUserId.value == actor && accountGeneration == stamp) _uiState.update {
                    it.copy(pendingActionRequestIds = it.pendingActionRequestIds - request.id)
                }
            }
        }
        actionJobs.add(job)
        job.invokeOnCompletion { actionJobs.remove(job) }
    }
}
