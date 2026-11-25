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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.hisab.features.profile.data.LocalProfilePhoto
import com.example.hisab.util.Constants
import com.example.hisab.features.profile.data.MediaStore
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.Group

val LocalMediaStore = staticCompositionLocalOf<MediaStore?> { null }
private data class CachedPicture(val actor: String, val id: String, val file: java.io.File?)

@Composable
fun HisabAvatar(url: String?, modifier: Modifier = Modifier, description: String? = null,
                localPhoto: LocalProfilePhoto = LocalProfilePhoto.Unset, group: Boolean = false) {
    val context = LocalContext.current
    val media = LocalMediaStore.current
    val actor = media?.account?.collectAsState()?.value
    val revision = media?.revision?.collectAsState()?.value
    val id = url?.removePrefix("/media/")?.takeIf { url.startsWith("/media/") && Regex("^[a-f0-9-]{36}$").matches(it) }
    val cached by produceState<CachedPicture?>(null, media, actor, id, revision) {
        value = if (media != null && actor != null && id != null) CachedPicture(actor, id, media.load(actor, id)) else null
    }
    val privateFile = cached?.takeIf { it.actor == actor && it.id == id }?.file
    val source = remember(context, url, localPhoto, privateFile) {
        when (localPhoto) {
            LocalProfilePhoto.Unset -> if (id != null) privateFile else Constants.profileImageUrl(url)
            LocalProfilePhoto.Removed -> null
            is LocalProfilePhoto.Selected -> ImageRequest.Builder(context).data(localPhoto.file)
                .memoryCacheKey("${localPhoto.file.path}:${localPhoto.revision}").build()
        }
    }
    var loaded by remember(source) { mutableStateOf(false) }
    Box(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).semantics {
            description?.let { contentDescription = it }
        },
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                if (group) Icons.Default.Group else Icons.Default.Person, contentDescription = null,
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
