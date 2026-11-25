package com.hisab.server.features.privacy

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.realtime.WebSocketManager
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import io.ktor.server.application.*
import kotlinx.coroutines.*
import java.io.File

fun Application.startPrivacyWorker(uploadDirectory: File) {
    val worker = launch(Dispatchers.IO) {
        var mediaCursor: String? = null
        while (isActive) {
            try {
                val jobs = DB.recoveryJobs.find(and(eq("status", "pending"), lte("dueAt", System.currentTimeMillis())))
                    .sort(Sorts.ascending("dueAt")).limit(10).toList()
                for (job in jobs) {
                    val id = job.getString("_id")
                    try {
                        deliverRecovery(id)
                        WebSocketManager.notifyUser(job.getString("ownerId"), "RECOVERY_UPDATED")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        // Failure details and data never go into the user-visible job or logs.
                        log.warn("Recovery attempt failed ({})", failure.javaClass.simpleName)
                        val attempts = (job["attempts"] as? Number)?.toInt() ?: 0
                        DB.recoveryJobs.updateOne(and(eq("_id", id), eq("status", "pending")), Updates.combine(
                            Updates.inc("attempts", 1), Updates.set("dueAt", System.currentTimeMillis() + 15_000L),
                            Updates.set("status", if (attempts >= 4) "failed" else "pending")))
                    }
                }
                val files = DB.fileCleanup.find().limit(30).toList()
                for (record in files) {
                    val name = record.getString("fileName")
                    if (name != null && legacyFileName.matches(name) &&
                        DB.accountDirectory.countDocuments(eq("profilePicUrl", "/static/uploads/$name")) == 0L) {
                        val target = File(uploadDirectory, name).canonicalFile
                        check(target.parentFile == uploadDirectory.canonicalFile)
                        check(!target.exists() || target.delete())
                    }
                    DB.fileCleanup.deleteOne(eq("_id", record.getString("_id")))
                }
                mediaCursor = com.hisab.server.features.media.cleanupMedia(mediaCursor)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { log.warn("Privacy worker will retry ({})", failure.javaClass.simpleName) }
            delay(15_000L)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) { worker.cancel() }
}
