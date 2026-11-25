package com.example.hisab.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.canhub.cropper.*

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SharedPictureControl(ownerKey: String, hasPicture: Boolean, busy: Boolean, enabled: Boolean,
    onPicked: (Uri) -> Unit, onRemove: () -> Unit, onError: () -> Unit) {
    var pickerOwner by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOwner by rememberUpdatedState(ownerKey)
    val currentEnabled by rememberUpdatedState(enabled && !busy)
    val crop = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val matching = pickerOwner == currentOwner && currentEnabled
        pickerOwner = null
        if (matching) {
            if (result.isSuccessful && result.uriContent != null) onPicked(result.uriContent!!)
            else if (result.error != null) onError()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && pickerOwner == currentOwner && currentEnabled) {
            crop.launch(CropImageContractOptions(uri, CropImageOptions(fixAspectRatio = true, aspectRatioX = 1, aspectRatioY = 1,
                cropShape = CropImageView.CropShape.RECTANGLE, outputCompressFormat = Bitmap.CompressFormat.JPEG,
                outputCompressQuality = 88, outputRequestWidth = 512, outputRequestHeight = 512,
                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE)))
        } else pickerOwner = null
    }
    var confirmRemove by rememberSaveable(ownerKey) { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled && !busy, onClick = { pickerOwner = ownerKey; gallery.launch("image/*") }) {
            ActionLabel(if (hasPicture) "Change picture" else "Add picture", "Saving picture…", busy)
        }
        if (hasPicture) TextButton(enabled = enabled && !busy, onClick = { confirmRemove = true }) { Text("Remove picture") }
    }
    if (confirmRemove) HisabAlert(AlertKind.CONFIRM, "Remove shared picture?", "This changes the picture for every active member.",
        onDismiss = { confirmRemove = false }, confirmLabel = "Remove", onConfirm = { confirmRemove = false; onRemove() }, dismissLabel = "Cancel")
}
