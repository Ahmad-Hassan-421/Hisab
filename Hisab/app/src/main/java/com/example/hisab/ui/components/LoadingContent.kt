package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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

@Composable
fun ListSkeleton(label: String, modifier: Modifier = Modifier, avatar: Boolean = false) {
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (avatar) Placeholder(Modifier.size(40.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Placeholder(Modifier.fillMaxWidth(0.62f).height(14.dp))
                        Placeholder(Modifier.fillMaxWidth(0.36f).height(10.dp))
                    }
                    Placeholder(Modifier.width(54.dp).height(14.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 32.dp)
            .clearAndSetSemantics { contentDescription = "Loading profile" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Placeholder(Modifier.size(120.dp))
        Placeholder(Modifier.width(144.dp).height(24.dp))
        Placeholder(Modifier.width(106.dp).height(14.dp))
        Placeholder(Modifier.width(166.dp).height(14.dp))
        Placeholder(Modifier.fillMaxWidth(0.8f).height(48.dp))
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape))
}
