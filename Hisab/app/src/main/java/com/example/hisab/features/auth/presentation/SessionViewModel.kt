package com.example.hisab.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.SessionCredentials
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val tokenManager: SessionCredentials,
    private val failures: FailureReporter
) : ViewModel() {

    val currentUserId: StateFlow<String?> = sessionManager.currentUserId
    val role: StateFlow<String?> = sessionManager.role
    val isAdmin: StateFlow<Boolean> = sessionManager.isAdmin
    val requiresSignIn: StateFlow<Boolean> = sessionManager.requiresSignIn
    val privacyReviewNeeded: StateFlow<Boolean> = sessionManager.privacyReviewNeeded
    private val mutableRestoring = MutableStateFlow(true)
    val restoring = mutableRestoring.asStateFlow()
    private var restoreJob: Job? = null
    
    init {
        checkAutoLogin()
    }
    
    fun checkAutoLogin() {
        if (restoreJob?.isActive == true) return
        val userId = tokenManager.getUserId()?.takeIf { it.isNotBlank() }
        if (userId == null || tokenManager.getToken().isNullOrBlank()) {
            tokenManager.clearAll()
            sessionManager.clear()
            mutableRestoring.value = false
            return
        }
        mutableRestoring.value = true
        restoreJob = viewModelScope.launch {
            if (tokenManager.getUserId() == userId && sessionManager.currentUserId.value != userId) {
                sessionManager.setSession(userId = userId, role = "user")
            }
            try {
                val remoteUser = withTimeout(12_000) { userRepository.fetchRemoteUser(userId, allowCached = false) }
                if (remoteUser != null && tokenManager.getUserId() == userId) {
                    sessionManager.setSession(userId = userId, role = remoteUser.role)
                }
            } catch (_: TimeoutCancellationException) {
                failures.report("Restore session profile", java.net.SocketTimeoutException())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401 && tokenManager.getUserId() == userId) {
                    tokenManager.clearAll()
                    sessionManager.expire()
                }
                failures.report("Restore session profile", e)
            } finally {
                mutableRestoring.value = false
            }
        }
    }
}
