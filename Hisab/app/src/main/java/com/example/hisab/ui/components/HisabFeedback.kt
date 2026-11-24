package com.example.hisab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class FeedbackKind(val label: String) { SUCCESS("Success"), INFO("Information"), WARNING("Warning"), ERROR("Error") }

data class HisabFeedback(val state: SnackbarHostState, val post: (String, FeedbackKind) -> Unit)

val LocalHisabFeedback = staticCompositionLocalOf<HisabFeedback?> { null }

private data class FeedbackVisuals(override val message: String, val kind: FeedbackKind) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction = true
    override val duration = if (kind == FeedbackKind.ERROR || kind == FeedbackKind.WARNING)
        SnackbarDuration.Long else SnackbarDuration.Short
}

suspend fun SnackbarHostState.showFeedback(message: String, kind: FeedbackKind) {
    if (currentSnackbarData?.visuals?.message == message) return
    currentSnackbarData?.dismiss()
    showSnackbar(FeedbackVisuals(message, kind))
}

@Composable
fun FeedbackEffect(message: String?, kind: FeedbackKind, onConsumed: () -> Unit) {
    val feedback = LocalHisabFeedback.current
    LaunchedEffect(message, kind, feedback) {
        if (message != null) {
            feedback?.post?.invoke(message, kind)
            onConsumed()
        }
    }
}

@Composable
fun rememberHisabFeedback(): HisabFeedback {
    val state = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        HisabFeedback(state) { message, kind -> scope.launch { state.showFeedback(message, kind) } }
    }
}

@Composable
fun HisabFeedbackHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        val kind = (data.visuals as? FeedbackVisuals)?.kind ?: FeedbackKind.INFO
        val icon = when (kind) {
            FeedbackKind.SUCCESS -> Icons.Default.CheckCircle
            FeedbackKind.INFO -> Icons.Default.Info
            FeedbackKind.WARNING -> Icons.Default.Warning
            FeedbackKind.ERROR -> Icons.Default.Error
        }
        val colors = MaterialTheme.colorScheme
        val foreground = when (kind) {
            FeedbackKind.SUCCESS -> colors.onPrimaryContainer
            FeedbackKind.WARNING -> colors.onTertiaryContainer
            FeedbackKind.ERROR -> colors.onErrorContainer
            FeedbackKind.INFO -> colors.inverseOnSurface
        }
        Snackbar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            dismissAction = {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss ${kind.label.lowercase()} message", tint = foreground)
                }
            },
            containerColor = when (kind) {
                FeedbackKind.SUCCESS -> colors.primaryContainer
                FeedbackKind.WARNING -> colors.tertiaryContainer
                FeedbackKind.ERROR -> colors.errorContainer
                FeedbackKind.INFO -> colors.inverseSurface
            },
            contentColor = foreground
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${kind.label}. ${data.visuals.message}"
                }
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(kind.label, style = MaterialTheme.typography.labelMedium)
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
