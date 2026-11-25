package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.presentation.MemberBalanceUI
import com.example.hisab.ui.components.PageFooter
import com.example.hisab.util.AppFailure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerMemberPicker(
    members: List<MemberBalanceUI>,
    selectedId: String,
    nextCursor: String? = null,
    loading: Boolean = false,
    failure: AppFailure? = null,
    onLoadMore: () -> Unit = {},
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = members.firstOrNull { it.user.id == selectedId }
    OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(selected?.user?.name ?: "Select a member")
    }
    if (expanded && enabled) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Text("Select a member", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) {
            if (members.isEmpty()) item {
                when {
                    loading -> com.example.hisab.ui.components.ListSkeleton("Loading members", rows = 3,
                        kind = com.example.hisab.ui.components.SkeletonKind.MEMBERS)
                    failure != null -> Column(Modifier.padding(12.dp)) {
                        Text(failure.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onLoadMore) { Text("Retry members") }
                    }
                    nextCursor == null -> Text("No eligible members.", Modifier.padding(12.dp))
                }
            }
            items(members, key = { it.user.id }, contentType = { "member-picker" }) { member ->
                val isSelected = member.user.id == selectedId
                Row(Modifier.fillMaxWidth().selectable(isSelected, role = Role.RadioButton, onClick = {
                    onSelect(member.user.id)
                    expanded = false
                }).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(member.user.name)
                }
            }
            if (members.isNotEmpty() || !loading && failure == null && nextCursor != null) item(key = "member-picker-footer") {
                PageFooter(nextCursor, loading, failure, onLoadMore)
            }
        }
    }
}
