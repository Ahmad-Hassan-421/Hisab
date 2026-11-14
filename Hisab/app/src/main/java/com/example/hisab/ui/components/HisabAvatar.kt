package com.example.hisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.example.hisab.util.Constants

@Composable
fun HisabAvatar(url: String?, modifier: Modifier = Modifier, description: String? = null) {
    val source = remember(url) { Constants.profileImageUrl(url) }
    var loaded by remember(source) { mutableStateOf(false) }
    Box(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).semantics {
            description?.let { contentDescription = it }
        },
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                Icons.Default.Person, contentDescription = null,
                modifier = Modifier.fillMaxSize(0.56f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (source != null) {
            AsyncImage(
                model = source,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onLoading = { loaded = false },
                onError = { loaded = false }
            )
        }
    }
}
