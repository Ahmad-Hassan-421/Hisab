package com.example.hisab.features.profile.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal suspend fun uploadCroppedImage(context: Context, uri: Uri, upload: suspend (MultipartBody.Part) -> String): String =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 5 * 1024 * 1024) throw java.io.IOException("Choose an image smaller than 5 MB")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw java.io.IOException("Could not open selected picture")
        upload(MultipartBody.Part.createFormData("image", "crop.jpg", bytes.toRequestBody("image/jpeg".toMediaType())))
    }
