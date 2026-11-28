package com.example.hisab.features.settings.presentation

import com.example.hisab.BuildConfig
import com.example.hisab.features.settings.data.VerifiedUpdate
import com.example.hisab.features.settings.domain.UpdateRepository
import com.example.hisab.util.FailureReporter
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateTrigger { AUTOMATIC, MANUAL, PUSH }

data class UpdateUiState(val checking: Boolean = false, val checked: Boolean = false,
    val release: VerifiedUpdate? = null, val error: String? = null, val visible: Boolean = false)

/** One process-wide update session, shared by startup, Settings and notification navigation. */
@Singleton
class UpdateCoordinator internal constructor(
    private val repository: UpdateRepository,
    private val failures: FailureReporter,
    private val scope: CoroutineScope,
    private val installedCode: Int,
    private val qaOrigin: String?,
    private val enabled: Boolean
) {
    @Inject constructor(repository: UpdateRepository, failures: FailureReporter) : this(repository, failures,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), BuildConfig.VERSION_CODE,
        BuildConfig.API_BASE_URL.takeIf { BuildConfig.UPDATE_QA }, !BuildConfig.DEBUG || BuildConfig.UPDATE_QA)

    private val mutable = MutableStateFlow(UpdateUiState())
    val state = mutable.asStateFlow()
    private var automaticChecked = false
    private var explicitRequest = false
    private var dismissedDuringCheck = false
    private val promptedVersions = mutableSetOf<Int>()

    fun check(trigger: UpdateTrigger) {
        if (trigger == UpdateTrigger.AUTOMATIC) {
            if (automaticChecked) return
            automaticChecked = true
        } else explicitRequest = true
        if (!enabled) {
            mutable.update { it.copy(error = "Use the release build to check for Hisab updates.") }
            return
        }
        if (mutable.value.checking) {
            if (trigger != UpdateTrigger.AUTOMATIC) { dismissedDuringCheck = false; mutable.update { it.copy(visible = true) } }
            return
        }
        dismissedDuringCheck = false
        mutable.update { it.copy(checking = true, error = null, visible = it.visible || trigger != UpdateTrigger.AUTOMATIC) }
        scope.launch {
            try {
                val release = withTimeout(20_000) { repository.getLatestRelease() }?.let {
                    it.validate(qaOrigin) ?: throw JsonParseException("Invalid update metadata")
                }?.takeIf { it.isNewerThan(installedCode) }
                val shouldPrompt = !dismissedDuringCheck && release != null && (explicitRequest || release.versionCode !in promptedVersions)
                if (shouldPrompt) promptedVersions.add(checkNotNull(release).versionCode)
                mutable.update { it.copy(checking = false, checked = true, release = release, error = null,
                    visible = explicitRequest || shouldPrompt) }
            } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) {
                mutable.update { it.copy(checking = false, error = "The update check timed out. Try again.", visible = explicitRequest) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val message = failures.report("Check for updates", error).message
                mutable.update { it.copy(checking = false, error = message, visible = explicitRequest) }
            } finally { explicitRequest = false }
        }
    }

    fun dismiss() {
        if (mutable.value.release?.isRequiredFor(installedCode) == true && mutable.value.error == null) return
        mutable.update { it.copy(visible = false) }
        dismissedDuringCheck = true
        explicitRequest = false
    }

    fun showAvailable() { mutable.update { it.copy(visible = true) } }
}
