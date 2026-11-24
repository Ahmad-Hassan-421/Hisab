package com.example.hisab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hisab.util.AppFailure

@Composable
fun PageFooter(cursor: String?, loading: Boolean, failure: AppFailure?, onLoad: () -> Unit) {
    if (cursor == null && !loading) return
    // A lazy list composes this item only as the user approaches its end.
    LaunchedEffect(cursor, loading) { if (cursor != null && !loading && failure == null) onLoad() }
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) CircularProgressIndicator()
        else if (failure != null) {
            Text(failure.message)
            TextButton(onClick = onLoad) { Text("Retry loading more") }
        } else TextButton(onClick = onLoad) { Text("Load more") }
    }
}
