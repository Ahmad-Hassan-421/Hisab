package com.example.hisab.features.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.VerifiedUpdate
import com.example.hisab.features.settings.domain.UpdateRepository
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.UpdateDownloadStatus
import com.google.gson.JsonParseException
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
    val isCheckingForUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val remoteRelease: VerifiedUpdate? = null,
    val updateCheckComplete: Boolean = false,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val checkError: String? = null,
    val savingTheme: Boolean = false,
    val feedback: String? = null,
    val feedbackError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
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

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (_uiState.value.isCheckingForUpdate) return
        if (BuildConfig.DEBUG && !BuildConfig.UPDATE_QA) {
            _uiState.update { it.copy(checkError = "Use the release build to check for Hisab updates.") }
            return
        }
        _uiState.update { it.copy(isCheckingForUpdate = true, checkError = null) }
        viewModelScope.launch {
            try {
                val release = updateRepository.getLatestRelease()?.let {
                    it.validate(BuildConfig.API_BASE_URL.takeIf { BuildConfig.UPDATE_QA })
                        ?: throw JsonParseException("Invalid update metadata")
                }
                val isNewer = release?.isNewerThan(BuildConfig.VERSION_CODE) == true
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        updateAvailable = isNewer,
                        remoteRelease = release,
                        updateCheckComplete = true,
                        checkError = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Check for updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        updateAvailable = false,
                        remoteRelease = null,
                        updateCheckComplete = false,
                        checkError = failure.message
                    )
                }
            }
        }
    }

    fun startUpdateDownload(release: VerifiedUpdate) {
        val state = _uiState.value
        if (!state.updateAvailable || state.isCheckingForUpdate || state.remoteRelease != release) return
        apkUpdateManager.startDownload(release)
    }

    fun installApk(context: Context) {
        apkUpdateManager.promptInstall(context)
    }

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
