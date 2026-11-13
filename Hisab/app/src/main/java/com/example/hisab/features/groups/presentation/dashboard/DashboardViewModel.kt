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
    val failure: AppFailure? = null,
    val actionError: String? = null
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
                    refreshGroups()
                    refreshJob?.join()
                    delay(15_000)
                }
            }
        }
    }

    fun refreshGroups() {
        val userId = sessionManager.currentUserId.value ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groups = groupRepository.getGroupsForUser(userId)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(groups = groups, isLoading = false, hasLoaded = true, failure = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load Hisabs", e)
                if (sessionManager.currentUserId.value == userId) {
                    _uiState.update { it.copy(isLoading = false, failure = failure) }
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                groupRepository.deleteGroup(groupId)
                refreshGroups()
            } catch (e: Exception) {
                val failure = failures.report("Delete Hisab", e)
                _uiState.update { it.copy(actionError = failure.message) }
            }
        }
    }

    fun clearActionError() = _uiState.update { it.copy(actionError = null) }
}
