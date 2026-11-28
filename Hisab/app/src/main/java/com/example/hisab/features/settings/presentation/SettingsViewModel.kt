package com.example.hisab.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.UpdateDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class SettingsUiState(
    val savingTheme: Boolean = false,
    val feedback: String? = null,
    val feedbackError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val updates: UpdateCoordinator,
    private val apkUpdateManager: ApkUpdateManager,
    private val themePreferencesManager: ThemePreferencesManager,
    private val failures: FailureReporter,
    private val users: UserRepository,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val downloadStatus: StateFlow<UpdateDownloadStatus> = apkUpdateManager.status
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val account = session.currentUserId.flatMapLatest { id -> if (id == null) flowOf(null) else users.getCurrentUser(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = themePreferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), themePreferencesManager.cachedThemeMode())

    fun checkForUpdates() = updates.check(UpdateTrigger.MANUAL)

    fun setThemeMode(mode: ThemeMode) {
        if (_uiState.value.savingTheme || mode == themeMode.value) return
        _uiState.update { it.copy(savingTheme = true, feedback = null) }
        viewModelScope.launch {
            try {
                themePreferencesManager.setThemeMode(mode)
                _uiState.update { it.copy(feedback = "Appearance updated.", feedbackError = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _uiState.update { it.copy(feedback = failures.report("Save appearance", error).message, feedbackError = true) } }
            finally { _uiState.update { it.copy(savingTheme = false) } }
        }
    }
    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }

    fun cancelUpdateDownload() = apkUpdateManager.cancel()
}
