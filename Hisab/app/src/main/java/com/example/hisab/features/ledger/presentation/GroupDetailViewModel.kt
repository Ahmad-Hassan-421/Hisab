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
import java.util.UUID
import javax.inject.Inject

data class DebtDetail(
    val userId: String,
    val userName: String,
    val amount: Double
)

data class MemberBalanceUI(
    val user: User,
    val totalDebt: Double,
    val totalReceive: Double,
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
    val isUpdatingLedger: Boolean = false,
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
        if (groupId.isBlank()) return
        
        ledgerObservation?.cancel()
        ledgerObservation = viewModelScope.launch {
            val uid = sessionManager.currentUserId.filterNotNull().first()
            _uiState.update { it.copy(isLoading = true, currentUserId = uid) }
            try {
                val groups = groupRepository.getGroupsForUser(uid)
                val group = groups.find { it.id == groupId }
                if (group == null) {
                    _uiState.update { it.copy(isLoading = false, group = null, requestError = "Hisab group is unavailable") }
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
                _uiState.update { it.copy(group = group, recommendedUsers = recommended) }
                observeBalances(users)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, requestError = "Could not load Hisab. Try again.") }
            }
        }
    }

    private suspend fun observeBalances(users: List<User>) {
        var wasSettled = false
        hisabRepository.getEntriesForGroup(groupId).collect { entries ->
            val balances = calculateBalances(users, entries)
            // Preserve expanded states
            val currentExpanded = _uiState.value.members.associate { it.user.id to it.isExpanded }
            val updatedBalances = balances.map { it.copy(isExpanded = currentExpanded[it.user.id] == true) }
            
            val allClear = entries.isNotEmpty() && balances.isNotEmpty() &&
                balances.all { it.totalDebt == 0.0 && it.totalReceive == 0.0 }
            
            _uiState.update { it.copy(
                members = updatedBalances, 
                entries = entries, 
                isLoading = false,
                showClearRecordsDialog = allClear && (!wasSettled || it.showClearRecordsDialog)
            ) }
            wasSettled = allClear
        }
    }

    private fun calculateBalances(users: List<User>, entries: List<HisabEntry>): List<MemberBalanceUI> {
        val names = users.associate { it.id to it.name }
        val balances = LedgerAmounts.netByMember(entries)
        return users.map { user ->
            val pairs = balances[user.id].orEmpty()
            val debts = pairs.filterValues { it.signum() > 0 }
            val receives = pairs.filterValues { it.signum() < 0 }
            MemberBalanceUI(
                user = user,
                totalDebt = debts.values.sumOf { it }.toDouble(),
                totalReceive = receives.values.sumOf { -it }.toDouble(),
                debtBreakdown = debts.map { (id, amount) -> DebtDetail(id, names[id] ?: "Unknown", amount.toDouble()) },
                receiveBreakdown = receives.map { (id, amount) -> DebtDetail(id, names[id] ?: "Unknown", -amount.toDouble()) }
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

    fun addAmountSpecific(debtorId: String, amount: Double, description: String) {
        val creditorId = _uiState.value.currentUserId
        if (creditorId.isBlank() || debtorId == creditorId) return
        if (!validateAmount(amount)) return
        
        updateLedger {
            val entry = HisabEntry(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                debtorId = debtorId,
                creditorId = creditorId,
                amount = amount,
                description = description,
                timestamp = System.currentTimeMillis()
            )
            hisabRepository.createHisabEntry(entry)
        }
    }

    fun addAmountSplit(amount: Double, description: String) {
        val creditorId = _uiState.value.currentUserId
        val members = _uiState.value.group?.members ?: return
        if (creditorId.isBlank() || members.size <= 1) return

        val otherMembers = members.filter { it != creditorId }
        if (otherMembers.isEmpty()) return
        if (!validateAmount(amount)) return
        val amounts = LedgerAmounts.split(amount, otherMembers.size)
        if (amounts.any { it == 0.0 }) {
            _uiState.update { it.copy(requestError = "The amount is too small to split among all members.") }
            return
        }
        
        updateLedger {
            val entries = otherMembers.zip(amounts).map { (debtorId, splitAmount) ->
                HisabEntry(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    debtorId = debtorId,
                    creditorId = creditorId,
                    amount = splitAmount,
                    description = description,
                    timestamp = System.currentTimeMillis()
                )
            }
            hisabRepository.createBatchEntries(entries)
        }
    }

    fun someonePaid(debtorId: String, creditorId: String, amount: Double, description: String) {
        recordRepayments(creditorId, mapOf(debtorId to amount), description)
    }

    fun recordRepayments(creditorId: String, amounts: Map<String, Double>, description: String) {
        if (amounts.isEmpty()) return
        val due = _uiState.value.members.firstOrNull { it.user.id == creditorId }
            ?.receiveBreakdown.orEmpty().associate { it.userId to it.amount }
        if (amounts.any { (debtor, amount) -> !LedgerAmounts.isValid(amount) || amount > (due[debtor] ?: 0.0) }) {
            _uiState.update { it.copy(requestError = "Each repayment must be within the amount owed.") }
            return
        }
        updateLedger {
            val entries = amounts.map { (debtorId, amount) ->
                HisabEntry(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    debtorId = creditorId, // Reverse the original debt to record repayment.
                    creditorId = debtorId,
                    amount = amount,
                    description = description,
                    timestamp = System.currentTimeMillis()
                )
            }
            hisabRepository.createBatchEntries(entries)
        }
    }
    
    fun sendInvite(uniqueId: String) {
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
                _uiState.update { it.copy(
                    isSendingRequest = false,
                    requestError = e.message ?: "Failed to send invite"
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
                _uiState.update { it.copy(requestError = "Could not leave Hisab. Try again.") }
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

    private fun updateLedger(update: suspend () -> Unit) {
        if (_uiState.value.isUpdatingLedger) return
        _uiState.update { it.copy(isUpdatingLedger = true, requestError = null) }
        viewModelScope.launch {
            try {
                update()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(requestError = "Could not update the ledger. Refresh before trying again.") }
            } finally {
                _uiState.update { it.copy(isUpdatingLedger = false) }
            }
        }
    }

    private fun validateAmount(amount: Double): Boolean {
        if (LedgerAmounts.isValid(amount)) return true
        _uiState.update { it.copy(requestError = "Enter a positive amount up to PKR 1 billion with at most three decimal places.") }
        return false
    }
}
