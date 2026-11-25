package com.example.hisab.features.profile.data

import android.content.Context
import android.util.AtomicFile
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.util.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class MediaAccessInput(val ids: List<String>)
data class MediaAccessResponse(val allowed: Set<String>)
data class GroupPictures(val pictureUrl: String, val pictureUpdatedAt: Long, val members: Map<String, String>)

/** Private, bounded immutable-version cache. Network authorization is rechecked while the app is visible. */
@Singleton
class MediaStore @Inject constructor(@ApplicationContext private val context: Context,
    private val api: HisabApiService, private val session: SessionManager) {
    private val disk = Mutex()
    private val locks = Array(32) { Mutex() }
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    val account = session.currentUserId
    private val revoked = mutableSetOf<String>()
    private val root get() = File(context.noBackupFilesDir, "media_cache")
    private fun directory(actor: String) = File(root, MessageDigest.getInstance("SHA-256").digest(actor.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) })
    private fun file(actor: String, id: String) = File(directory(actor), "$id.jpg")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            var previous: String? = null
            account.collect { actor ->
                disk.withLock {
                    previous?.takeIf { it != actor }?.let { directory(it).deleteRecursively() }
                    if (actor != null) root.listFiles()?.filter { it != directory(actor) }?.forEach { it.deleteRecursively() }
                    revoked.clear()
                    _revision.update { it + 1 }
                    previous = actor
                }
            }
        }
    }

    suspend fun load(actor: String, id: String): File? = withContext(Dispatchers.IO) {
        require(Regex("^[a-f0-9-]{36}$").matches(id))
        locks[(id.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val cached = disk.withLock {
                if (account.value != actor || id in revoked) return@withContext null
                file(actor, id).takeIf { it.isFile }?.also { it.setLastModified(System.currentTimeMillis()) }
            }
            if (cached != null) return@withContext cached
            try {
                val bytes = api.downloadMedia(id).use { body ->
                    body.byteStream().use { source ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 512 * 1024) throw java.io.IOException("Picture too large")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
                if (bytes.size < 3 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) throw java.io.IOException("Invalid picture")
                disk.withLock {
                    if (account.value != actor || id in revoked) return@withContext null
                    val target = file(actor, id)
                    if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw java.io.IOException("Cache unavailable")
                    val atomic = AtomicFile(target)
                    val output = atomic.startWrite()
                    try { output.write(bytes); atomic.finishWrite(output) }
                    catch (error: Exception) { atomic.failWrite(output); throw error }
                    prune(actor)
                    target
                }
            } catch (error: CancellationException) { throw error }
            catch (error: HttpException) {
                if (error.code() in setOf(401, 403, 404)) disk.withLock {
                    if (account.value == actor) { revoked.add(id); file(actor, id).delete(); _revision.update { it + 1 } }
                }
                null
            } catch (_: java.io.IOException) { null }
        }
    }

    suspend fun revalidate() = withContext(Dispatchers.IO) {
        val actor = account.value ?: return@withContext
        val ids = disk.withLock { (directory(actor).listFiles()?.map { it.nameWithoutExtension }
            ?.filter { Regex("^[a-f0-9-]{36}$").matches(it) }.orEmpty() + revoked).distinct() }
        for (batch in ids.chunked(50)) {
            try {
                val allowed = api.mediaAccess(MediaAccessInput(batch)).allowed
                disk.withLock {
                    if (account.value != actor) return@withContext
                    val removed = batch.filterNot { it in allowed }
                    val changed = removed.any { it !in revoked } || allowed.any { it in revoked }
                    revoked.removeAll(allowed)
                    removed.forEach { file(actor, it).delete(); revoked.add(it) }
                    if (revoked.size > 2048) revoked.clear()
                    if (changed) _revision.update { it + 1 }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { return@withContext } // Offline: retain this account's previously authorized versions.
        }
    }

    suspend fun erase(actor: String) = withContext(Dispatchers.IO) { disk.withLock {
        directory(actor).deleteRecursively(); _revision.update { it + 1 }
    } }
    fun close() { scope.cancel() }
    private fun prune(actor: String) {
        val files = directory(actor).listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (entry in files) {
            if (bytes <= 32L * 1024 * 1024) break
            bytes -= entry.length(); entry.delete()
        }
    }
}
