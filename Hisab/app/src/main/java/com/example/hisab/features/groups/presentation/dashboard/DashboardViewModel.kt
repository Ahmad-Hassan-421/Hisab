package com.example.hisab.features.groups.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

data class DashboardUiState(
    val groups: List<HisabGroup> = emptyList(),
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val failure: AppFailure? = null,
    val actionError: String? = null,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val pageFailure: AppFailure? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        startPollingGroups()
    }

    private fun startPollingGroups() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                refreshJob?.cancel()
                _uiState.value = DashboardUiState()
                if (userId == null) return@collectLatest
                while (isActive) {
                    if (!_uiState.value.hasLoaded || _uiState.subscriptionCount.value > 0) refreshGroups(userInitiated = false)
                    refreshJob?.join()
                    delay(15_000)
                }
            }
        }
    }

    fun refreshGroups(userInitiated: Boolean = true) {
        val userId = sessionManager.currentUserId.value ?: return
        if (refreshJob?.isActive == true) return
        if (userInitiated && _uiState.value.hasLoaded) _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val page = groupRepository.getGroupsPage(userId)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update {
                        val preserveTail = !userInitiated && it.groups.size > page.items.size
                        it.copy(groups = (page.items + if (preserveTail) it.groups else emptyList()).distinctBy { group -> group.id },
                            nextCursor = if (preserveTail) it.nextCursor else page.nextCursor,
                            isLoading = false, isRefreshing = false, hasLoaded = true, failure = null, pageFailure = null)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load Hisabs", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, failure = failure) }
                }
            }
        }
    }

    fun loadNextPage() {
        val userId = sessionManager.currentUserId.value ?: return
        val cursor = _uiState.value.nextCursor ?: return
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingMore = true, pageFailure = null) }
        refreshJob = viewModelScope.launch {
            try {
                val page = groupRepository.getGroupsPage(userId, cursor)
                if (sessionManager.currentUserId.value == userId) _uiState.update {
                    it.copy(groups = (it.groups + page.items).distinctBy { group -> group.id }, nextCursor = page.nextCursor,
                        isLoadingMore = false, pageFailure = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failure = failures.report("Load more Hisabs", error)
                if (sessionManager.currentUserId.value == userId) _uiState.update {
                    it.copy(isLoadingMore = false, pageFailure = failure)
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                groupRepository.deleteGroup(groupId)
                _uiState.update { it.copy(groups = it.groups.filterNot { group -> group.id == groupId }) }
                refreshJob?.cancel()
                refreshGroups(userInitiated = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Delete Hisab", e)
                _uiState.update { it.copy(actionError = failure.message) }
            }
        }
    }

    fun clearActionError() = _uiState.update { it.copy(actionError = null) }
}
