package com.example.hisab.features.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.AppUpdateResponse
import com.example.hisab.features.settings.domain.UpdateRepository
import com.example.hisab.util.ApkUpdateManager
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.ThemeMode
import com.example.hisab.util.ThemePreferencesManager
import com.example.hisab.util.UpdateDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val isCheckingForUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val remoteRelease: AppUpdateResponse? = null,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val checkError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val apkUpdateManager: ApkUpdateManager,
    private val themePreferencesManager: ThemePreferencesManager,
    private val failures: FailureReporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val downloadStatus: StateFlow<UpdateDownloadStatus> = apkUpdateManager.status

    val themeMode: StateFlow<ThemeMode> = themePreferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (_uiState.value.isCheckingForUpdate) return
        _uiState.update { it.copy(isCheckingForUpdate = true, checkError = null) }
        viewModelScope.launch {
            try {
                val release = updateRepository.getLatestRelease()
                val isNewer = release.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        updateAvailable = isNewer,
                        remoteRelease = release,
                        checkError = null
                    )
                }
            } catch (e: Exception) {
                val failure = failures.report("Check for updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        updateAvailable = false,
                        remoteRelease = null,
                        checkError = failure.message
                    )
                }
            }
        }
    }

    fun startUpdateDownload() {
        if (!_uiState.value.updateAvailable || downloadStatus.value is UpdateDownloadStatus.Downloading) return
        val release = _uiState.value.remoteRelease ?: return
        apkUpdateManager.startDownload(release.downloadUrl, release.versionName)
    }

    fun installApk(context: Context, apkFile: File) {
        apkUpdateManager.promptInstall(context, apkFile)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferencesManager.setThemeMode(mode)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return apkUpdateManager.canRequestPackageInstalls(context)
    }
}
