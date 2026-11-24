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
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = members.firstOrNull { it.user.id == selectedId }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(selected?.user?.name ?: "Select a member")
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Text("Select a member", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) {
            if (members.isEmpty() && nextCursor == null) item { Text("No eligible members.", Modifier.padding(12.dp)) }
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
            item(key = "member-picker-footer") { PageFooter(nextCursor, loading, failure, onLoadMore) }
        }
    }
}
