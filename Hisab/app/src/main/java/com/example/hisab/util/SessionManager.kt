package com.example.hisab.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val activityUpdates = changes.asSharedFlow()
    fun noteActivity(owner: String, groupId: String) { if (_currentUserId.value == owner) changes.tryEmit(groupId) }

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _requiresSignIn = MutableStateFlow(false)
    val requiresSignIn: StateFlow<Boolean> = _requiresSignIn.asStateFlow()

    private val _privacyReviewNeeded = MutableStateFlow(false)
    val privacyReviewNeeded: StateFlow<Boolean> = _privacyReviewNeeded.asStateFlow()

    fun requirePrivacyReview() { _privacyReviewNeeded.value = true }
    fun completePrivacyReview() { _privacyReviewNeeded.value = false }

    fun setSession(userId: String, role: String) {
        _requiresSignIn.value = false
        _currentUserId.value = userId
        _role.value = role
        _isAdmin.value = role == "admin"
    }

    fun clear() {
        _currentUserId.value = null
        _role.value = null
        _isAdmin.value = false
        _privacyReviewNeeded.value = false
    }

    fun expire() {
        clear()
        _requiresSignIn.value = true
    }
}
