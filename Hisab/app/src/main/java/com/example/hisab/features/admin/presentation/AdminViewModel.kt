package com.example.hisab.features.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.features.admin.data.AccountOverview
import com.example.hisab.features.admin.data.ChangeAccountStatus
import com.example.hisab.features.admin.data.DirectoryAccount
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

enum class DirectoryFilter(val value: String, val label: String) {
    All("all", "All accounts"), Active("active", "Active"), Suspended("suspended", "Suspended")
}

data class AdminUiState(
    val overview: AccountOverview? = null,
    val accounts: List<DirectoryAccount> = emptyList(),
    val searchInput: String = "",
    val query: String = "",
    val filter: DirectoryFilter = DirectoryFilter.All,
    val nextCursor: String? = null,
    val busy: Boolean = false,
    val failure: AppFailure? = null,
    val feedback: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val api: HisabApiService,
    private val session: SessionManager,
    private val failures: FailureReporter
) : ViewModel() {
    private val mutableState = MutableStateFlow(AdminUiState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    init {
        viewModelScope.launch {
            session.currentUserId.combine(session.isAdmin) { id, admin -> id.takeIf { admin } }
                .distinctUntilChanged().collect { administrator ->
                    work?.cancel()
                    mutableState.value = AdminUiState()
                    if (administrator != null) refresh()
                }
        }
    }

    fun searchChanged(value: String) = mutableState.update { it.copy(searchInput = value.take(100)) }

    fun search() {
        if (state.value.busy) return
        mutableState.update { it.copy(query = it.searchInput.trim(), accounts = emptyList(), nextCursor = null) }
        refresh()
    }

    fun filter(selected: DirectoryFilter) {
        if (selected == state.value.filter || state.value.busy) return
        mutableState.update { it.copy(filter = selected, accounts = emptyList(), nextCursor = null) }
        refresh()
    }

    fun refresh() = perform("Load admin accounts") { authId -> readPage(authId, cursor = null) }

    fun nextPage() {
        val cursor = state.value.nextCursor ?: return
        perform("Load more admin accounts") { authId -> readPage(authId, cursor) }
    }

    fun changeStatus(account: DirectoryAccount) {
        val status = if (account.status == "active") "suspended" else "active"
        perform("Change account status") { authId ->
            val response = api.changeAccountStatus(account.id, ChangeAccountStatus(status))
            if (!response.isSuccessful) throw HttpException(response)
            if (!current(authId)) return@perform
            mutableState.update {
                it.copy(feedback = if (status == "active") "Account reactivated." else "Account suspended.",
                    accounts = it.accounts.filterNot { entry -> entry.id == account.id }, nextCursor = null)
            }
            readPage(authId, cursor = null)
        }
    }

    fun clearFeedback() = mutableState.update { it.copy(feedback = null) }

    private suspend fun readPage(authId: String, cursor: String?) {
        val request = state.value
        val overview = if (cursor == null) api.getAccountOverview() else request.overview
        val page = api.getAccountDirectory(request.query, request.filter.value, cursor)
        if (!current(authId)) return
        mutableState.update {
            val accounts = if (cursor == null) page.users else (it.accounts + page.users).distinctBy(DirectoryAccount::id)
            it.copy(overview = overview, accounts = accounts, nextCursor = page.nextCursor)
        }
    }

    private fun current(authId: String) = session.currentUserId.value == authId && session.isAdmin.value

    private fun perform(label: String, operation: suspend (String) -> Unit) {
        val authId = session.currentUserId.value ?: return
        if (!current(authId) || state.value.busy) return
        mutableState.update { it.copy(busy = true, failure = null) }
        work = viewModelScope.launch {
            try {
                operation(authId)
            } catch (error: Exception) {
                val failure = failures.report(label, error)
                if (current(authId)) mutableState.update {
                    if (failure == AppFailure.FORBIDDEN || failure == AppFailure.SESSION_EXPIRED) AdminUiState(failure = failure)
                    else it.copy(failure = failure)
                }
            } finally {
                if (current(authId)) mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
