package com.example.hisab.features.profile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LocalProfilePhoto {
    data object Unset : LocalProfilePhoto
    data object Removed : LocalProfilePhoto
    data class Selected(val file: File, val revision: String) : LocalProfilePhoto
}

@Singleton
class LocalProfilePhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val access = Mutex()

    suspend fun load(accountId: String): LocalProfilePhoto = onDisk {
        val file = accountFile(accountId)
        val stored = AtomicFile(file)
        val photo = try {
            stored.openRead().use { input -> readBounded(input) }
        } catch (error: java.io.FileNotFoundException) {
            if (file.exists()) throw error
            return@onDisk LocalProfilePhoto.Unset
        } catch (_: InvalidPhoto) {
            return@onDisk LocalProfilePhoto.Removed
        }
        if (photo.isEmpty()) LocalProfilePhoto.Removed
        else if (supportedDimensions(photo)) LocalProfilePhoto.Selected(file, fingerprint(photo))
        else LocalProfilePhoto.Removed
    }

    suspend fun save(accountId: String, crop: Uri): LocalProfilePhoto.Selected = onDisk {
        val bytes = context.contentResolver.openInputStream(crop)?.use(::readBounded) ?: throw InvalidPhoto()
        if (!supportedDimensions(bytes)) throw InvalidPhoto()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw InvalidPhoto()
        val path = accountFile(accountId)
        try {
            write(path) { output -> if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw InvalidPhoto() }
        } finally {
            bitmap.recycle()
        }
        val saved = path.inputStream().use(::readBounded)
        LocalProfilePhoto.Selected(path, fingerprint(saved))
    }

    suspend fun remove(accountId: String) = onDisk {
        // An empty record means that the owner explicitly reset a legacy remote avatar on this device.
        write(accountFile(accountId)) { }
    }

    private suspend fun <T> onDisk(operation: () -> T): T =
        withContext(Dispatchers.IO) { access.withLock { operation() } }

    private fun accountFile(accountId: String): File {
        require(accountId.isNotBlank())
        val key = fingerprint(accountId.toByteArray(Charsets.UTF_8))
        return File(context.noBackupFilesDir, "profile_photos/$key.jpg")
    }

    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun write(file: File, action: (java.io.FileOutputStream) -> Unit) {
        val directory = checkNotNull(file.parentFile)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Photo directory unavailable")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            action(output)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun supportedDimensions(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth in 1..512 && bounds.outWidth == bounds.outHeight
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return output.toByteArray()
            if (output.size() + count > 2 * 1024 * 1024) throw InvalidPhoto()
            output.write(buffer, 0, count)
        }
    }

    class InvalidPhoto : IOException("Unsupported profile picture")
}
