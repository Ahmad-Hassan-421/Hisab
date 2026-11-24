package com.example.hisab.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.User
import com.example.hisab.features.auth.presentation.SessionViewModel
import com.example.hisab.features.privacy.data.*
import com.example.hisab.features.privacy.presentation.*
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class StartupFlowTest {
    @Test fun anAcceptedSessionNeverVisitsPolicyReviewWhileAccountAndConsentResolve() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val observed = mutableListOf<PrivacyGate>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.privacy.state.collect { observed += it.gate } }
        try {
            runCurrent()
            assertFalse(fixture.resolved())
            fixture.users.profile.complete(User("actor", "Alice", "alice@example.invalid"))
            runCurrent()
            assertFalse(fixture.resolved())
            fixture.source.answer.complete(PrivacyStatus(true, "v1", 100))
            runCurrent()
            assertTrue(fixture.resolved())
            assertEquals(PrivacyGate.Continue, fixture.privacy.state.value.gate)
            assertFalse(observed.contains(PrivacyGate.Review))
        } finally { observer.cancel(); fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun aStalledConnectionEndsStartupWithoutClearingAValidSavedSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent(); advanceTimeBy(12_001); runCurrent()
            assertTrue(fixture.resolved())
            assertEquals(PrivacyGate.Unavailable, fixture.privacy.state.value.gate)
            assertFalse(fixture.auth.restoring.value)
            assertEquals("actor", fixture.session.currentUserId.value)
            assertEquals("saved-token", fixture.tokens.getToken())
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun offlineContinuationRequiresTheSameAccountsCachedAcknowledgmentAndNoRequiredReview() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(cached = "v1")
        try {
            runCurrent()
            fixture.source.answer.completeExceptionally(ConnectException()); runCurrent()
            assertEquals(PrivacyGate.Continue, fixture.privacy.state.value.gate)
            fixture.session.requirePrivacyReview(); runCurrent()
            assertEquals(PrivacyGate.Unavailable, fixture.privacy.state.value.gate)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun aRequiredNewPolicyCannotBeReplacedWithAStaleOfflineDocument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(cached = "v1")
        try {
            runCurrent()
            fixture.source.answer.complete(PrivacyStatus(false, "v2", null)); runCurrent()
            assertEquals(PrivacyGate.Unavailable, fixture.privacy.state.value.gate)
            assertNull(fixture.tokens.cached)
            fixture.source.document.value = fixture.source.document.value!!.copy(version = "v2", acknowledgmentVersion = "v2")
            fixture.privacy.checkStatus(); runCurrent()
            assertEquals(PrivacyGate.Review, fixture.privacy.state.value.gate)
            assertEquals("v2", fixture.privacy.document.value!!.version)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun anIncompleteStoredSessionOpensSignInWithoutCallingPrivateApis() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(token = null)
        try {
            runCurrent()
            assertTrue(fixture.resolved())
            assertNull(fixture.session.currentUserId.value)
            assertEquals(0, fixture.users.calls)
            assertEquals(0, fixture.source.calls)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun rejectedSessionsAreRevokedAndLateProfilesCannotOverwriteAnotherAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.tokens.owner = "bob"
            fixture.session.setSession("bob", "user")
            fixture.users.profile.complete(User("actor", "Alice", "", role = "admin")); runCurrent()
            assertEquals("bob", fixture.session.currentUserId.value)
            assertFalse(fixture.session.isAdmin.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val rejected = Fixture()
        try {
            runCurrent()
            rejected.users.profile.completeExceptionally(HttpException(Response.error<User>(401, "{}".toResponseBody()))); runCurrent()
            assertNull(rejected.session.currentUserId.value)
            assertNull(rejected.tokens.getToken())
            assertTrue(rejected.session.requiresSignIn.value)
        } finally { rejected.close(); Dispatchers.resetMain() }
    }

    private class Fixture(cached: String? = null, token: String? = "saved-token") {
        val tokens = Credentials(cached, token)
        val session = SessionManager()
        val users = Users()
        val source = Policies()
        val reporter = FailureReporter({ true }, {})
        val auth = SessionViewModel(session, users, tokens, reporter)
        val privacy = PrivacyViewModel(source, session, tokens, reporter)
        fun resolved() = startupResolved(auth.restoring.value, session.currentUserId.value, privacy.state.value.gate)
        fun close() { auth.viewModelScope.cancel(); privacy.viewModelScope.cancel() }
    }
    private class Credentials(var cached: String?, var savedToken: String?) : SessionCredentials {
        var owner: String? = "actor"
        override fun getUserId() = owner
        override fun getToken() = savedToken
        override fun clearAll() { owner = null; savedToken = null; cached = null }
        override fun cachePrivacyVersion(userId: String, version: String?) { if (owner == userId) cached = version }
        override fun cachedPrivacyVersion(userId: String) = cached.takeIf { owner == userId }
    }
    private class Policies : PrivacySource {
        val document = MutableStateFlow<PrivacyDocument?>(PrivacyDocument("v1", "v1", "Today", listOf(PrivacySection("Notice", "Test notice"))))
        override val policy: StateFlow<PrivacyDocument?> = document
        val answer = CompletableDeferred<PrivacyStatus>()
        var calls = 0
        override suspend fun bundledPolicy() = PrivacyDocument("v1", "v1", "Today", emptyList())
        override suspend fun refreshPolicy() = document.value!!
        override suspend fun status(): PrivacyStatus { calls++; return answer.await() }
        override suspend fun acknowledge(version: String) = PrivacyStatus(true, version, 100)
    }
    private class Users : UserRepository {
        val profile = CompletableDeferred<User?>()
        var calls = 0
        override suspend fun fetchRemoteUser(userId: String, allowCached: Boolean): User? { calls++; return profile.await() }
        override fun getCurrentUser(userId: String): Flow<User?> = flowOf(null)
        override suspend fun createUserDocument(user: User) = Unit
        override suspend fun saveUser(user: User) = Unit
        override suspend fun updateUser(user: User) = Unit
        override suspend fun uploadProfilePicture(uri: Uri): String = error("Unused")
        override suspend fun deleteUser(userId: String) = Unit
    }
}
