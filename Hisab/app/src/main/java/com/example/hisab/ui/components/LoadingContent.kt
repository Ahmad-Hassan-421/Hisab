package com.example.hisab.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun ActionLabel(label: String, busyLabel: String, busy: Boolean) {
    Box(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
                Text(busyLabel)
            }
        } else {
            Text(label)
        }
    }
}

enum class SkeletonKind { HISABS, TRANSACTIONS, INVITATIONS, MEMBERS, ARCHIVES, BALANCES }

private val LocalSkeletonOpacity = staticCompositionLocalOf<State<Float>> { mutableStateOf(0.12f) }

@Composable
private fun skeletonMotionEnabled(): Boolean {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(systemAnimationsEnabled(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = systemAnimationsEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

private fun systemAnimationsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
    else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

@Composable
private fun SkeletonRoot(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val opacity = if (skeletonMotionEnabled()) {
        rememberInfiniteTransition(label = "Loading pulse").animateFloat(0.08f, 0.16f,
            infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "Loading opacity")
    } else rememberUpdatedState(0.12f)
    CompositionLocalProvider(LocalSkeletonOpacity provides opacity) {
        Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ListSkeleton(label: String, modifier: Modifier = Modifier, avatar: Boolean = false,
    kind: SkeletonKind = if (avatar) SkeletonKind.MEMBERS else SkeletonKind.HISABS, rows: Int = 4) {
    SkeletonRoot(label, modifier) {
        repeat(rows.coerceIn(1, 6)) { SkeletonCard(kind) }
    }
}

@Composable
private fun SkeletonCard(kind: SkeletonKind) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kind == SkeletonKind.TRANSACTIONS) {
                Placeholder(Modifier.width(68.dp).height(12.dp))
                Placeholder(Modifier.width(126.dp).height(24.dp))
                Placeholder(Modifier.fillMaxWidth(0.8f).height(14.dp))
                Placeholder(Modifier.fillMaxWidth(0.6f).height(14.dp))
                Placeholder(Modifier.width(112.dp).height(10.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Placeholder(Modifier.size(44.dp), circle = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Placeholder(Modifier.fillMaxWidth(0.7f).height(16.dp))
                        Placeholder(Modifier.fillMaxWidth(0.45f).height(11.dp))
                    }
                    if (kind == SkeletonKind.HISABS) Placeholder(Modifier.width(24.dp).height(18.dp))
                }
                when (kind) {
                    SkeletonKind.BALANCES -> {
                        Placeholder(Modifier.fillMaxWidth(0.6f).height(16.dp))
                        Placeholder(Modifier.fillMaxWidth(0.5f).height(16.dp))
                    }
                    SkeletonKind.INVITATIONS, SkeletonKind.ARCHIVES -> {
                        Placeholder(Modifier.fillMaxWidth(0.9f).height(12.dp))
                        Placeholder(Modifier.fillMaxWidth(0.7f).height(12.dp))
                        if (kind == SkeletonKind.ARCHIVES) Placeholder(Modifier.fillMaxWidth(0.55f).height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Placeholder(Modifier.weight(1f).height(36.dp))
                            Placeholder(Modifier.weight(1f).height(36.dp))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun HisabDetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonRoot("Loading Hisab and balances", modifier) {
        Placeholder(Modifier.fillMaxWidth(0.65f).height(28.dp))
        Placeholder(Modifier.fillMaxWidth(0.35f).height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Placeholder(Modifier.weight(1f).height(40.dp))
            Placeholder(Modifier.weight(1f).height(40.dp))
        }
        repeat(3) { SkeletonCard(SkeletonKind.BALANCES) }
    }
}

@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    SkeletonRoot("Loading profile", modifier.padding(top = 32.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Placeholder(Modifier.size(120.dp), circle = true)
            Placeholder(Modifier.width(144.dp).height(24.dp))
            Placeholder(Modifier.width(106.dp).height(14.dp))
            Placeholder(Modifier.width(166.dp).height(14.dp))
            Placeholder(Modifier.fillMaxWidth(0.8f).height(48.dp))
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier, circle: Boolean = false) {
    val opacity = LocalSkeletonOpacity.current
    val color = MaterialTheme.colorScheme.onSurface
    // Read animation state during drawing so loading never recomposes the surrounding list each frame.
    Box(modifier.clip(if (circle) CircleShape else RoundedCornerShape(6.dp)).drawBehind {
        drawRect(color.copy(alpha = opacity.value))
    })
}
