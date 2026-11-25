package com.example.hisab.features.ledger.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.db.HisabEntry
import com.example.hisab.features.groups.domain.HisabGroup
import com.example.hisab.data.db.User
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.features.ledger.domain.LedgerAmounts
import com.example.hisab.features.ledger.data.LedgerOverview
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.SessionManager
import com.example.hisab.util.AppFailure
import com.example.hisab.util.FailureReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class DebtDetail(
    val userId: String,
    val userName: String,
    val amount: BigDecimal
)

data class MemberBalanceUI(
    val user: User,
    val totalDebt: BigDecimal,
    val totalReceive: BigDecimal,
    val debtBreakdown: List<DebtDetail>,
    val receiveBreakdown: List<DebtDetail>,
    val isExpanded: Boolean = false
)

data class GroupDetailUiState(
    val group: HisabGroup? = null,
    val members: List<MemberBalanceUI> = emptyList(),
    val entries: List<HisabEntry> = emptyList(),
    val recommendedUsers: List<User> = emptyList(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadFailure: AppFailure? = null,
    val isUpdatingLedger: Boolean = false,
    val ledgerFormError: String? = null,
    val ledgerNeedsRefresh: Boolean = false,
    val hasSettledRecords: Boolean = false,
    val showClearRecordsDialog: Boolean = false,
    val clearRecordsError: String? = null,
    val isSendingRequest: Boolean = false,
    val isLeaving: Boolean = false,
    val leaveError: String? = null,
    val requestError: String? = null,
    val requestSuccessMessage: String? = null,
    val recordsCleared: Boolean = false,
    val entryCount: Long = 0,
    val nextMemberCursor: String? = null,
    val loadingMembers: Boolean = false,
    val memberPageFailure: AppFailure? = null,
    val historyOpen: Boolean = false,
    val historyOtherUserId: String? = null,
    val historyKind: String = "all",
    val historyEntries: List<HisabEntry> = emptyList(),
    val historyCursor: String? = null,
    val historyLoading: Boolean = false,
    val historyFailure: AppFailure? = null,
    val memberNames: Map<String, String> = emptyMap(),
    val suggestionCursor: String? = null,
    val loadingSuggestions: Boolean = false,
    val suggestionFailure: AppFailure? = null,
    val isSavingPicture: Boolean = false
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val requestRepository: RequestRepository,
    private val hisabRepository: HisabRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val failures: FailureReporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()
    private var ledgerObservation: Job? = null
    private var memberJob: Job? = null
    private var historyJob: Job? = null
    private var suggestionJob: Job? = null
    private var historyGeneration = 0
    private var overview: LedgerOverview? = null
    private var pictureJob: Job? = null
    private var pictureRefresh: Job? = null
    private var pictureGeneration = 0L

    init {
        viewModelScope.launch {
            sessionManager.currentUserId.collectLatest { userId ->
                ledgerObservation?.cancel()
                memberJob?.cancel()
                suggestionJob?.cancel()
                pictureJob?.cancel()
                pictureRefresh?.cancel()
                closeHistory()
                overview = null
                _uiState.value = GroupDetailUiState()
                if (userId != null) loadData()
            }
        }
    }

    fun loadData() {
        if (groupId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, loadFailure = AppFailure.MISSING) }
            return
        }
        if (_uiState.value.isUpdatingLedger || (ledgerObservation?.isActive == true && _uiState.value.isLoading)) return
        
        ledgerObservation?.cancel()
        memberJob?.cancel()
        _uiState.update { it.copy(loadingMembers = false) }
        ledgerObservation = viewModelScope.launch {
            val uid = sessionManager.currentUserId.filterNotNull().first()
            _uiState.update { it.copy(isLoading = true, isRefreshing = it.group != null, currentUserId = uid) }
            try {
                val remoteOverview = hisabRepository.getOverview(groupId)
                if (remoteOverview != null) {
                    val page = hisabRepository.getMembersPage(groupId)
                    if (sessionManager.currentUserId.value != uid) return@launch
                    overview = remoteOverview
                    val self = remoteOverview.self.let { dto -> User(dto.id, dto.name, dto.email,
                        dto.profilePicUrl.orEmpty(), dto.themePreference ?: "system", dto.role ?: "user",
                        dto.uniqueId.orEmpty(), dto.createdAt ?: 0, dto.updatedAt ?: 0) }
                    applyOverview(remoteOverview, (listOf(self) + page.items).distinctBy { it.id }, refreshed = true)
                    _uiState.update { it.copy(nextMemberCursor = page.nextCursor, loadingMembers = false, memberPageFailure = null) }
                    return@launch
                }
                val groups = groupRepository.getGroupsForUser(uid)
                if (sessionManager.currentUserId.value != uid) return@launch
                val group = groups.find { it.id == groupId }
                if (group == null) {
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, group = null, entries = emptyList(), members = emptyList(),
                            hasSettledRecords = false, showClearRecordsDialog = false, loadFailure = AppFailure.MISSING)
                    }
                    return@launch
                }
                hisabRepository.syncGroupEntries(groupId)
                val users = coroutineScope {
                    (group.members + group.exMembers).distinct().map { memberId ->
                        async { userRepository.fetchRemoteUser(memberId) ?: User(memberId, "Unknown member", "") }
                    }.awaitAll()
                }
                val otherIds = groups.flatMap { it.members }.toSet() - group.members.toSet() - uid
                val recommended = otherIds.mapNotNull { userRepository.fetchRemoteUser(it) }
                if (sessionManager.currentUserId.value != uid) return@launch
                _uiState.update {
                    it.copy(group = group, recommendedUsers = recommended, loadFailure = null,
                        ledgerNeedsRefresh = false, clearRecordsError = null,
                        ledgerFormError = if (it.ledgerNeedsRefresh) null else it.ledgerFormError)
                }
                observeBalances(users, uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load Hisab", e)
                if (sessionManager.currentUserId.value == uid) {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, loadFailure = failure) }
                }
            }
        }
    }

    private suspend fun observeBalances(users: List<User>, userId: String) {
        var wasSettled = _uiState.value.hasSettledRecords.takeIf { _uiState.value.entries.isNotEmpty() }
        hisabRepository.getEntriesForGroup(groupId).collect { entries ->
            if (sessionManager.currentUserId.value != userId) return@collect
            val pairBalances = LedgerAmounts.netByMember(entries)
            val balances = calculateBalances(users, pairBalances)
            // Preserve expanded states
            val currentExpanded = _uiState.value.members.associate { it.user.id to it.isExpanded }
            val updatedBalances = balances.map { it.copy(isExpanded = currentExpanded[it.user.id] == true) }
            
            val allClear = entries.isNotEmpty() &&
                pairBalances.values.all { pairs -> pairs.values.all { it.signum() == 0 } }
            
            _uiState.update { it.copy(
                members = updatedBalances, 
                entries = entries, 
                entryCount = entries.size.toLong(),
                isLoading = false,
                isRefreshing = false,
                hasSettledRecords = allClear,
                showClearRecordsDialog = allClear && (wasSettled == false || it.showClearRecordsDialog)
            ) }
            wasSettled = allClear
        }
    }

    private fun calculateBalances(
        users: List<User>,
        balances: Map<String, Map<String, BigDecimal>>,
        authoritativeNames: Map<String, String> = emptyMap()
    ): List<MemberBalanceUI> {
        val names = authoritativeNames + users.associate { it.id to it.name }
        return users.map { user ->
            val pairs = balances[user.id].orEmpty()
            val debts = pairs.filterValues { it.signum() > 0 }
            val receives = pairs.filterValues { it.signum() < 0 }
            MemberBalanceUI(
                user = user,
                totalDebt = debts.values.sumOf { it },
                totalReceive = receives.values.sumOf { -it },
                debtBreakdown = debts.map { (id, amount) -> DebtDetail(id, names[id] ?: if (id.startsWith("deleted-")) "Deleted account" else "Former member", amount) },
                receiveBreakdown = receives.map { (id, amount) -> DebtDetail(id, names[id] ?: if (id.startsWith("deleted-")) "Deleted account" else "Former member", -amount) }
            )
        }
    }

    private fun applyOverview(value: LedgerOverview, users: List<User>, refreshed: Boolean = false) {
        val balances = mutableMapOf<String, MutableMap<String, BigDecimal>>()
        value.pairs.forEach { pair ->
            val amount = pair.amount.toBigDecimal()
            balances.getOrPut(pair.firstId) { mutableMapOf() }[pair.secondId] = amount
            balances.getOrPut(pair.secondId) { mutableMapOf() }[pair.firstId] = -amount
        }
        val allowed = (value.group.members + value.group.exMembers.orEmpty()).toSet()
        val expanded = _uiState.value.members.associate { it.user.id to it.isExpanded }
        val members = calculateBalances(users.filter { it.id in allowed }, balances, value.names).map {
            it.copy(isExpanded = expanded[it.user.id] == true)
        }
        val group = value.group.let { HisabGroup(it.id, it.name, it.members, it.exMembers.orEmpty(), it.createdAt, it.memberCount ?: it.members.size, it.pictureUrl.orEmpty(), it.pictureUpdatedAt) }
        _uiState.update { it.copy(group = group, members = members, entryCount = value.entryCount,
            memberNames = value.names, hasSettledRecords = value.settled,
            showClearRecordsDialog = value.settled && (it.showClearRecordsDialog || refreshed && !it.hasSettledRecords && it.entryCount > 0),
            isLoading = if (refreshed) false else it.isLoading,
            isRefreshing = if (refreshed) false else it.isRefreshing,
            loadFailure = if (refreshed) null else it.loadFailure,
            ledgerNeedsRefresh = if (refreshed) false else it.ledgerNeedsRefresh,
            clearRecordsError = if (refreshed) null else it.clearRecordsError) }
    }

    fun setPicture(uri: android.net.Uri?) {
        val actor = sessionManager.currentUserId.value ?: return
        if (_uiState.value.isSavingPicture || _uiState.value.isUpdatingLedger || actor !in _uiState.value.group?.members.orEmpty()) return
        pictureGeneration++
        pictureRefresh?.cancel()
        _uiState.update { it.copy(isSavingPicture = true) }
        pictureJob = viewModelScope.launch {
            try {
                val path = groupRepository.setPicture(groupId, uri)
                if (sessionManager.currentUserId.value != actor) return@launch
                _uiState.update { it.copy(group = it.group?.copy(pictureUrl = path),
                    requestSuccessMessage = if (path.isBlank()) "Shared picture removed" else "Shared picture updated") }
                overview = overview?.let { it.copy(group = it.group.copy(pictureUrl = path)) }
                loadData()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (sessionManager.currentUserId.value == actor) _uiState.update {
                    it.copy(requestError = failures.report("Change shared picture", error).message)
                }
            } finally {
                if (sessionManager.currentUserId.value == actor) _uiState.update { it.copy(isSavingPicture = false) }
            }
        }
    }

    fun reportPictureError() { _uiState.update { it.copy(requestError = "Couldn't open that picture. Choose another image.") } }

    fun refreshPictures() {
        val actor = sessionManager.currentUserId.value ?: return
        if (_uiState.value.group == null || pictureRefresh?.isActive == true || _uiState.value.isSavingPicture) return
        val generation = pictureGeneration
        pictureRefresh = viewModelScope.launch {
            try {
                val ids = _uiState.value.members.map { it.user.id }
                // Keep metadata queries bounded even after loading many member pages.
                for (batch in ids.chunked(100).ifEmpty { listOf(emptyList()) }) {
                    val value = groupRepository.pictures(groupId, batch) ?: return@launch
                    if (sessionManager.currentUserId.value != actor || _uiState.value.isSavingPicture || generation != pictureGeneration) return@launch
                    _uiState.update { state -> state.copy(group = state.group?.copy(pictureUrl = value.pictureUrl.orEmpty(), pictureUpdatedAt = value.pictureUpdatedAt),
                        members = state.members.map { member -> value.members[member.user.id]?.let { member.copy(user = member.user.copy(profilePicUrl = it)) } ?: member }) }
                    overview = overview?.let { it.copy(group = it.group.copy(pictureUrl = value.pictureUrl.orEmpty(), pictureUpdatedAt = value.pictureUpdatedAt)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is retrofit2.HttpException && error.code() in setOf(401, 403, 404) && sessionManager.currentUserId.value == actor) {
                    _uiState.update { it.copy(group = null, members = emptyList(), loadFailure = failures.report("Refresh Hisab pictures", error)) }
                }
            }
        }
    }

    fun loadMoreMembers() {
        val uid = sessionManager.currentUserId.value ?: return
        val cursor = _uiState.value.nextMemberCursor ?: return
        if (memberJob?.isActive == true || _uiState.value.isLoading) return
        _uiState.update { it.copy(loadingMembers = true, memberPageFailure = null) }
        memberJob = viewModelScope.launch {
            try {
                val page = hisabRepository.getMembersPage(groupId, cursor)
                if (sessionManager.currentUserId.value != uid) return@launch
                overview?.let { value -> applyOverview(value, (_uiState.value.members.map { it.user } + page.items).distinctBy { it.id }) }
                _uiState.update { it.copy(nextMemberCursor = page.nextCursor, loadingMembers = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load more members", e)
                if (sessionManager.currentUserId.value == uid) _uiState.update { it.copy(loadingMembers = false, memberPageFailure = failure) }
            }
        }
    }

    fun closeHistory() {
        historyGeneration++
        historyJob?.cancel()
        _uiState.update { it.copy(historyOpen = false, historyEntries = emptyList(), historyCursor = null,
            historyLoading = false, historyFailure = null) }
    }

    fun loadHistory(otherUserId: String? = null, kind: String = "all", reset: Boolean = true) {
        val uid = sessionManager.currentUserId.value ?: return
        if (!reset && (historyJob?.isActive == true || _uiState.value.historyCursor == null)) return
        if (reset) {
            historyGeneration++
            historyJob?.cancel()
            _uiState.update { it.copy(historyOpen = true, historyOtherUserId = otherUserId, historyKind = kind,
                historyEntries = emptyList(), historyCursor = null, historyFailure = null) }
        }
        val generation = historyGeneration
        val cursor = if (reset) null else _uiState.value.historyCursor
        _uiState.update { it.copy(historyLoading = true, historyFailure = null) }
        historyJob = viewModelScope.launch {
            try {
                val page = hisabRepository.getEntriesPage(groupId, cursor, kind, otherUserId)
                if (sessionManager.currentUserId.value != uid || generation != historyGeneration) return@launch
                if (overview != null && page.revision != overview?.revision) {
                    val refreshed = requireNotNull(hisabRepository.getOverview(groupId))
                    check(refreshed.revision == page.revision) { "The Hisab changed. Refresh to continue" }
                    if (sessionManager.currentUserId.value != uid || generation != historyGeneration) return@launch
                    overview = refreshed
                    applyOverview(refreshed, _uiState.value.members.map { it.user }, refreshed = true)
                }
                _uiState.update { it.copy(historyEntries = (it.historyEntries + page.items).distinctBy { entry -> entry.id },
                    historyCursor = page.nextCursor, historyLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load transaction history", e)
                if (sessionManager.currentUserId.value == uid && generation == historyGeneration) _uiState.update {
                    it.copy(historyLoading = false, historyFailure = failure)
                }
            }
        }
    }

    fun loadMoreHistory() {
        val state = _uiState.value
        loadHistory(state.historyOtherUserId, state.historyKind, reset = state.historyFailure == AppFailure.CONFLICT)
    }

    fun paymentParticipants(): List<MemberBalanceUI> {
        val state = _uiState.value
        val me = state.members.firstOrNull { it.user.id == state.currentUserId } ?: return emptyList()
        val ids = (me.debtBreakdown + me.receiveBreakdown).map { it.userId }.toSet()
        return ids.map { id ->
            state.members.firstOrNull { it.user.id == id } ?: run {
                val payable = me.debtBreakdown.firstOrNull { it.userId == id }?.amount ?: BigDecimal.ZERO
                val receivable = me.receiveBreakdown.firstOrNull { it.userId == id }?.amount ?: BigDecimal.ZERO
                val user = User(id, state.memberNames[id] ?: "Former member", "")
                MemberBalanceUI(user, receivable, payable,
                    if (receivable.signum() > 0) listOf(DebtDetail(state.currentUserId, "You", receivable)) else emptyList(),
                    if (payable.signum() > 0) listOf(DebtDetail(state.currentUserId, "You", payable)) else emptyList())
            }
        }
    }

    fun loadSuggestedMembers(reset: Boolean = true) {
        val uid = sessionManager.currentUserId.value ?: return
        if (suggestionJob?.isActive == true) return
        val cursor = if (reset) null else _uiState.value.suggestionCursor ?: return
        _uiState.update { it.copy(loadingSuggestions = true, suggestionFailure = null) }
        suggestionJob = viewModelScope.launch {
            try {
                val page = hisabRepository.getSuggestedMembersPage(groupId, cursor)
                if (sessionManager.currentUserId.value == uid) _uiState.update {
                    it.copy(recommendedUsers = ((if (reset) emptyList() else it.recommendedUsers) + page.items).distinctBy { user -> user.id },
                        suggestionCursor = page.nextCursor, loadingSuggestions = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load member suggestions", e)
                if (sessionManager.currentUserId.value == uid) _uiState.update { it.copy(loadingSuggestions = false, suggestionFailure = failure) }
            }
        }
    }

    fun toggleMemberExpanded(userId: String) {
        _uiState.update { state ->
            state.copy(members = state.members.map {
                if (it.user.id == userId) it.copy(isExpanded = !it.isExpanded) else it
            })
        }
    }

    fun addDirectDebt(creditorId: String, amount: Double, description: String, onSaved: () -> Unit) {
        val debtorId = _uiState.value.currentUserId
        if (debtorId.isBlank() || creditorId == debtorId) return
        if (!validateAmount(amount)) return

        updateLedger(onSaved) {
            val entry = HisabEntry(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                debtorId = debtorId,
                creditorId = creditorId,
                amount = amount,
                description = description,
                timestamp = System.currentTimeMillis(),
                type = "DIRECT_DEBT"
            )
            hisabRepository.createHisabEntry(entry)
        }
    }

    fun addAmountSpecific(debtorId: String, amount: Double, description: String, onSaved: () -> Unit) {
        val creditorId = _uiState.value.currentUserId
        if (creditorId.isBlank() || debtorId == creditorId) return
        if (!validateAmount(amount)) return
        
        updateLedger(onSaved) {
            val entry = HisabEntry(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                debtorId = debtorId,
                creditorId = creditorId,
                amount = amount,
                description = description,
                timestamp = System.currentTimeMillis(),
                type = "EXPENSE"
            )
            hisabRepository.createHisabEntry(entry)
        }
    }

    fun addAmountSplit(amount: Double, description: String, onSaved: () -> Unit) {
        val creditorId = _uiState.value.currentUserId
        val members = _uiState.value.group?.members ?: return
        if (creditorId !in members || members.distinct().size <= 1) {
            _uiState.update { it.copy(ledgerFormError = "At least two members are needed to split an expense.") }
            return
        }
        if (!validateAmount(amount)) return

        val splitShares = LedgerAmounts.splitExpense(amount, creditorId, members)
        if (splitShares == null) {
            _uiState.update { it.copy(ledgerFormError = "The amount is too small to split among all members.") }
            return
        }
        
        updateLedger(onSaved) {
            val entries = splitShares.map { share ->
                HisabEntry(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    debtorId = share.debtorId,
                    creditorId = creditorId,
                    amount = share.amount,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    type = "EXPENSE"
                )
            }
            hisabRepository.createBatchEntries(entries)
        }
    }

    fun someonePaid(debtorId: String, creditorId: String, amount: Double, description: String, onSaved: () -> Unit) {
        recordRepayments(creditorId, mapOf(debtorId to amount), description, onSaved)
    }

    fun recordRepayments(creditorId: String, amounts: Map<String, Double>, description: String, onSaved: () -> Unit) {
        if (amounts.isEmpty()) return
        val due = (_uiState.value.members + paymentParticipants()).firstOrNull { it.user.id == creditorId }
            ?.receiveBreakdown.orEmpty().associate { it.userId to it.amount }
        if (amounts.any { (debtor, amount) ->
                !LedgerAmounts.isValid(amount) || BigDecimal.valueOf(amount) > (due[debtor] ?: BigDecimal.ZERO)
            }) {
            _uiState.update { it.copy(ledgerFormError = "Each repayment must be within the amount owed.") }
            return
        }
        updateLedger(onSaved) {
            val entries = amounts.map { (debtorId, amount) ->
                HisabEntry(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    debtorId = creditorId, // Reverse the original debt to record repayment.
                    creditorId = debtorId,
                    amount = amount,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    type = "REPAYMENT"
                )
            }
            hisabRepository.createBatchEntries(entries)
        }
    }
    
    fun sendInvite(uniqueId: String) {
        if (_uiState.value.isSendingRequest) return
        if (uniqueId.length != 12) {
            _uiState.update { it.copy(requestError = "Must be a 12-digit User ID") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingRequest = true, requestError = null, requestSuccessMessage = null) }
            try {
                requestRepository.sendRequest(uniqueId, groupId)
                _uiState.update { it.copy(
                    isSendingRequest = false,
                    requestSuccessMessage = "Invitation sent successfully!"
                )}
            } catch (e: Exception) {
                val failure = failures.report("Send invitation", e)
                _uiState.update { it.copy(
                    isSendingRequest = false,
                    requestError = failure.message
                )}
            }
        }
    }
    
    fun clearMessages() {
        _uiState.update { it.copy(requestError = null, requestSuccessMessage = null) }
    }

    fun acknowledgeRecordsCleared() = _uiState.update { it.copy(recordsCleared = false) }
    
    fun leaveGroup(onComplete: () -> Unit) {
        if (_uiState.value.isLeaving) return
        _uiState.update { it.copy(isLeaving = true, leaveError = null) }
        viewModelScope.launch {
            try {
                groupRepository.leaveGroup(groupId)
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Leave Hisab", e)
                _uiState.update { it.copy(leaveError = failure.message) }
            } finally {
                _uiState.update { it.copy(isLeaving = false) }
            }
        }
    }

    fun dismissClearRecordsDialog() {
        if (_uiState.value.isUpdatingLedger) return
        _uiState.update { it.copy(showClearRecordsDialog = false, clearRecordsError = null) }
    }

    fun requestClearRecords() {
        _uiState.update { state ->
            if (state.hasSettledRecords && !state.isUpdatingLedger && !state.isRefreshing && !state.ledgerNeedsRefresh) {
                state.copy(showClearRecordsDialog = true)
            } else state
        }
    }

    fun clearRecords() {
        if (!_uiState.value.showClearRecordsDialog) return
        updateLedger {
            hisabRepository.clearGroupEntries(groupId)
            _uiState.update { it.copy(showClearRecordsDialog = false, recordsCleared = true) }
        }
    }

    fun clearLedgerFormError() = _uiState.update { it.copy(ledgerFormError = null) }

    private fun updateLedger(onSaved: (() -> Unit)? = null, update: suspend () -> Unit) {
        if (_uiState.value.isUpdatingLedger || _uiState.value.isRefreshing || _uiState.value.ledgerNeedsRefresh) return
        _uiState.update { it.copy(isUpdatingLedger = true, requestError = null, ledgerFormError = null, clearRecordsError = null) }
        viewModelScope.launch {
            var saved = false
            try {
                update()
                saved = true
                onSaved?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Update ledger", e)
                val needsRefresh = failure in setOf(AppFailure.TIMEOUT, AppFailure.UNAVAILABLE,
                    AppFailure.INVALID_RESPONSE, AppFailure.STORAGE, AppFailure.CONFLICT)
                val message = if (needsRefresh) "Reload this Hisab to confirm the current balance before trying again."
                    else failure.message
                _uiState.update {
                    if (onSaved == null) it.copy(clearRecordsError = message, ledgerNeedsRefresh = needsRefresh)
                    else it.copy(ledgerFormError = message, ledgerNeedsRefresh = needsRefresh)
                }
            } finally {
                _uiState.update { it.copy(isUpdatingLedger = false) }
            }
            if (saved && overview != null) loadData()
        }
    }

    private fun validateAmount(amount: Double): Boolean {
        if (LedgerAmounts.isValid(amount)) return true
        _uiState.update { it.copy(ledgerFormError = LedgerAmounts.AMOUNT_ERROR) }
        return false
    }
}
