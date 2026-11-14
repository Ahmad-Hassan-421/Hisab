package com.example.hisab.features.ledger.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hisab.features.ledger.presentation.MemberBalanceUI

@Composable
internal fun LedgerMemberPicker(
    members: List<MemberBalanceUI>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (members.isEmpty()) {
        Text("No eligible members.")
        return
    }
    val selected = members.firstOrNull { it.user.id == selectedId }
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Hide members" else selected?.user?.name ?: "Select a member")
    }
    if (expanded) {
        Column(Modifier.selectableGroup()) {
            members.forEach { member ->
                val isSelected = member.user.id == selectedId
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .selectable(isSelected, role = Role.RadioButton, onClick = {
                            onSelect(member.user.id)
                            expanded = false
                        })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(member.user.name)
                }
            }
        }
    }
}
