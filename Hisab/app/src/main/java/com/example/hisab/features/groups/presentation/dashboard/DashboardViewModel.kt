package com.example.hisab.features.groups.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.features.groups.domain.GroupRepository
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

data class DashboardUiState(
    val groups: List<HisabGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        startPollingGroups()
    }

    private fun startPollingGroups() {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                if (userId == null) {
                    _uiState.value = DashboardUiState()
                    return@collectLatest
                }
                while (isActive) {
                    try {
                        val groups = groupRepository.getGroupsForUser(userId)
                        _uiState.update { it.copy(groups = groups, isLoading = false, error = null) }
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

    fun refreshGroups() {
        viewModelScope.launch {
            val userId = sessionManager.currentUserId.value
            if (userId != null) {
                try {
                    _uiState.update { it.copy(isLoading = true) }
                    val groups = groupRepository.getGroupsForUser(userId)
                    _uiState.update { it.copy(groups = groups, isLoading = false, error = null) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
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
                _uiState.update { it.copy(error = "Failed to delete group") }
            }
        }
    }
}
