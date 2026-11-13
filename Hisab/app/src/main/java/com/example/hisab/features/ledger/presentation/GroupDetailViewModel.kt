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
    val isLoading: Boolean = false,
    val loadFailure: AppFailure? = null,
    val isUpdatingLedger: Boolean = false,
    val ledgerFormError: String? = null,
    val ledgerNeedsRefresh: Boolean = false,
    val showClearRecordsDialog: Boolean = false,
    val isSendingRequest: Boolean = false,
    val requestError: String? = null,
    val requestSuccessMessage: String? = null
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

    init {
        loadData()
    }

    fun loadData() {
        if (groupId.isBlank() || _uiState.value.isLoading) return
        
        ledgerObservation?.cancel()
        ledgerObservation = viewModelScope.launch {
            val uid = sessionManager.currentUserId.filterNotNull().first()
            _uiState.update { it.copy(isLoading = true, currentUserId = uid) }
            try {
                val groups = groupRepository.getGroupsForUser(uid)
                val group = groups.find { it.id == groupId }
                if (group == null) {
                    _uiState.update {
                        it.copy(isLoading = false, group = null, entries = emptyList(), members = emptyList(),
                            showClearRecordsDialog = false, loadFailure = AppFailure.MISSING)
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
                _uiState.update {
                    it.copy(group = group, recommendedUsers = recommended, loadFailure = null,
                        ledgerNeedsRefresh = false, ledgerFormError = if (it.ledgerNeedsRefresh) null else it.ledgerFormError)
                }
                observeBalances(users)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Load Hisab", e)
                _uiState.update { it.copy(isLoading = false, loadFailure = failure) }
            }
        }
    }

    private suspend fun observeBalances(users: List<User>) {
        var wasSettled = false
        hisabRepository.getEntriesForGroup(groupId).collect { entries ->
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
                isLoading = false,
                showClearRecordsDialog = allClear && (!wasSettled || it.showClearRecordsDialog)
            ) }
            wasSettled = allClear
        }
    }

    private fun calculateBalances(
        users: List<User>,
        balances: Map<String, Map<String, BigDecimal>>
    ): List<MemberBalanceUI> {
        val names = users.associate { it.id to it.name }
        return users.map { user ->
            val pairs = balances[user.id].orEmpty()
            val debts = pairs.filterValues { it.signum() > 0 }
            val receives = pairs.filterValues { it.signum() < 0 }
            MemberBalanceUI(
                user = user,
                totalDebt = debts.values.sumOf { it },
                totalReceive = receives.values.sumOf { -it },
                debtBreakdown = debts.map { (id, amount) -> DebtDetail(id, names[id] ?: "Unknown", amount) },
                receiveBreakdown = receives.map { (id, amount) -> DebtDetail(id, names[id] ?: "Unknown", -amount) }
            )
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
        val due = _uiState.value.members.firstOrNull { it.user.id == creditorId }
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
    
    fun leaveGroup(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                groupRepository.leaveGroup(groupId)
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = failures.report("Leave Hisab", e)
                _uiState.update { it.copy(requestError = failure.message) }
            }
        }
    }

    fun dismissClearRecordsDialog() {
        _uiState.update { it.copy(showClearRecordsDialog = false) }
    }

    fun clearRecords() {
        updateLedger {
            hisabRepository.clearGroupEntries(groupId)
            _uiState.update { it.copy(showClearRecordsDialog = false) }
        }
    }

    fun clearLedgerFormError() = _uiState.update { it.copy(ledgerFormError = null) }

    private fun updateLedger(onSaved: (() -> Unit)? = null, update: suspend () -> Unit) {
        if (_uiState.value.isUpdatingLedger || _uiState.value.ledgerNeedsRefresh) return
        _uiState.update { it.copy(isUpdatingLedger = true, requestError = null, ledgerFormError = null) }
        viewModelScope.launch {
            try {
                update()
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
                    if (onSaved == null) it.copy(requestError = message, ledgerNeedsRefresh = needsRefresh)
                    else it.copy(ledgerFormError = message, ledgerNeedsRefresh = needsRefresh)
                }
            } finally {
                _uiState.update { it.copy(isUpdatingLedger = false) }
            }
        }
    }

    private fun validateAmount(amount: Double): Boolean {
        if (LedgerAmounts.isValid(amount)) return true
        _uiState.update { it.copy(ledgerFormError = LedgerAmounts.AMOUNT_ERROR) }
        return false
    }
}
