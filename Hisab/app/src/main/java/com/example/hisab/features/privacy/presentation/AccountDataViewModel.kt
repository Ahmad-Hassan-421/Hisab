package com.example.hisab.features.privacy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.features.privacy.data.*
import com.example.hisab.features.ledger.data.HisabEntryDto
import com.example.hisab.features.profile.data.UserDto
import com.example.hisab.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.example.hisab.ui.components.FeedbackKind

enum class DataPanel { HOME, ARCHIVES, RECOVERED, HISTORY, MEMBERS, ACCOUNTS, SUBMITTED }
data class AccountDataState(
    val panel: DataPanel = DataPanel.HOME,
    val cards: List<ArchiveCard> = emptyList(),
    val entries: List<HisabEntryDto> = emptyList(),
    val members: List<HistoricalMember> = emptyList(),
    val accounts: List<UserDto> = emptyList(),
    val selected: ArchiveCard? = null,
    val filter: String = "all",
    val receipt: RecoveryReceipt? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val cursor: String? = null,
    val failure: AppFailure? = null,
    val pageFailure: AppFailure? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageKind: FeedbackKind = FeedbackKind.INFO
)

@HiltViewModel
class AccountDataViewModel @Inject constructor(
    private val source: AccountDataSource,
    private val cleanup: AccountLocalCleanup,
    private val session: SessionManager,
    private val failures: FailureReporter
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountDataState())
    val state = mutable.asStateFlow()
    private var load: Job? = null
    private var action: Job? = null
    private var generation = 0

    init { viewModelScope.launch { session.currentUserId.collect {
        load?.cancel(); action?.cancel(); generation++; mutable.value = AccountDataState()
    } } }

    fun open(panel: DataPanel, selected: ArchiveCard? = state.value.selected, filter: String = "all") {
        if (state.value.busy) return
        load?.cancel(); generation++
        mutable.value = AccountDataState(panel = panel, selected = selected, filter = filter)
        if (panel !in setOf(DataPanel.HOME, DataPanel.SUBMITTED)) refresh()
    }
    fun back() = open(when (state.value.panel) {
        DataPanel.HISTORY, DataPanel.MEMBERS -> DataPanel.RECOVERED
        else -> DataPanel.HOME
    })
    fun refresh(background: Boolean = false) = fetch(false, background)
    fun more() = fetch(true, false)

    private fun fetch(append: Boolean, background: Boolean) {
        if (load?.isActive == true || state.value.busy) return
        val actor = session.currentUserId.value ?: return
        val before = state.value
        if (append && before.cursor == null) return
        if (background && before.cards.isEmpty() && before.failure != null) return
        val stamp = generation
        load = viewModelScope.launch {
            mutable.update { it.copy(loading = !append && !background, loadingMore = append, failure = if (append || background) it.failure else null,
                pageFailure = null) }
            try {
                val cursor = before.cursor.takeIf { append }
                when (before.panel) {
                    DataPanel.ARCHIVES, DataPanel.RECOVERED -> {
                        val page = if (before.panel == DataPanel.ARCHIVES) source.archives(cursor) else source.recovered(cursor)
                        if (current(actor, stamp)) mutable.update {
                            val keep = append || background
                            it.copy(cards = (if (append) it.cards + page.items else page.items + if (keep) it.cards else emptyList())
                                .distinctBy(ArchiveCard::id).filter { card -> card.expiresAt == null || card.expiresAt > (page.serverTime ?: System.currentTimeMillis()) },
                                cursor = if (background && it.cards.size > page.items.size) it.cursor else page.nextCursor)
                        }
                    }
                    DataPanel.HISTORY -> {
                        val page = source.entries(requireNotNull(before.selected).id, before.filter, cursor)
                        if (current(actor, stamp)) mutable.update { it.copy(entries = ((if (append) it.entries else emptyList()) + page.items).distinctBy(HisabEntryDto::id), cursor = page.nextCursor) }
                    }
                    DataPanel.MEMBERS -> {
                        val page = source.members(requireNotNull(before.selected).id, cursor)
                        if (current(actor, stamp)) mutable.update { it.copy(members = ((if (append) it.members else emptyList()) + page.items).distinctBy(HistoricalMember::id), cursor = page.nextCursor) }
                    }
                    DataPanel.ACCOUNTS -> {
                        val accounts = source.accounts().filter { it.id == actor }
                        if (current(actor, stamp)) mutable.update { it.copy(accounts = accounts, cursor = null) }
                    }
                    else -> Unit
                }
                if (current(actor, stamp)) mutable.update { it.copy(failure = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (current(actor, stamp)) mutable.update {
                    val failure = failures.report("Load private account data", error)
                    if (append) it.copy(pageFailure = failure) else it.copy(failure = failure)
                }
            } finally { if (current(actor, stamp)) mutable.update { it.copy(loading = false, loadingMore = false) } }
        }
    }

    fun recover(card: ArchiveCard) = perform("Recover Hisab") { actor, stamp ->
        val receipt = source.recover(card.id)
        if (current(actor, stamp)) mutable.update { it.copy(panel = DataPanel.SUBMITTED, receipt = receipt, cards = emptyList(), cursor = null,
            message = "Recovery request submitted. Your copy will normally be ready within 3–5 minutes.", messageKind = FeedbackKind.SUCCESS) }
    }
    fun deleteCopy(card: ArchiveCard) = perform("Permanently delete recovered Hisab") { actor, stamp ->
        source.deleteCopy(card.id)
        if (current(actor, stamp)) mutable.update { it.copy(cards = it.cards.filterNot { row -> row.id == card.id }, message = "Recovered Hisab permanently deleted", messageKind = FeedbackKind.SUCCESS) }
    }
    fun deleteAccount(accountId: String, password: String, onDeleted: () -> Unit = {}) {
        if (accountId != session.currentUserId.value) return
        perform("Permanently delete account") { _, _ ->
            source.deleteAccount(DeleteAccountInput(accountId, password))
            // Once the server confirms deletion, never retain a usable session if local cleanup fails.
            withContext(NonCancellable) {
                try { cleanup.erase(accountId); onDeleted() } finally { if (session.currentUserId.value == accountId) session.expire() }
            }
        }
    }
    private fun perform(operation: String, block: suspend (String, Int) -> Unit) {
        if (action?.isActive == true) return
        val actor = session.currentUserId.value ?: return
        load?.cancel()
        val stamp = generation
        mutable.update { it.copy(busy = true, message = null, failure = null, loading = false, loadingMore = false) }
        action = viewModelScope.launch {
            try { block(actor, stamp) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (current(actor, stamp)) mutable.update {
                val failure = failures.report(operation, error)
                it.copy(message = failure.message, messageKind = if (failure in setOf(AppFailure.FORBIDDEN, AppFailure.SESSION_EXPIRED)) FeedbackKind.WARNING else FeedbackKind.ERROR)
            } }
            finally { if (current(actor, stamp)) mutable.update { it.copy(busy = false) } }
        }
    }
    private fun current(actor: String, stamp: Int) = session.currentUserId.value == actor && generation == stamp
    fun consumeMessage() = mutable.update { it.copy(message = null) }
}
