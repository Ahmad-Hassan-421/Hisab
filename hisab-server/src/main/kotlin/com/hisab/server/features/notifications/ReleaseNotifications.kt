package com.hisab.server.features.notifications

import com.hisab.server.database.MongoClientProvider as DB
import com.hisab.server.features.privacy.lockAccount
import com.hisab.server.features.releases.GitHubReleaseClient
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.bson.Document
import java.util.Date
import java.util.UUID

internal suspend fun announceVerifiedRelease(versionName: String, versionCode: Int) {
    val tag = "v$versionName"
    DB.releaseNotificationJobs.updateOne(eq("_id", tag), Document("\$setOnInsert", Document("versionName", versionName)
        .append("versionCode", versionCode).append("status", "pending").append("cursor", "").append("leaseUntil", 0L)
        .append("verifiedAt", System.currentTimeMillis())), UpdateOptions().upsert(true))
}

internal suspend fun advanceReleaseAudience(id: String) {
    val now = System.currentTimeMillis()
    val lease = UUID.randomUUID().toString()
    val job = DB.releaseNotificationJobs.findOneAndUpdate(and(eq("_id", id), eq("status", "pending"), lte("leaseUntil", now)),
        Updates.combine(Updates.set("lease", lease), Updates.set("leaseUntil", now + 120_000)),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)) ?: return
    val version = (job["versionCode"] as Number).toInt()
    val devices = DB.devices.find(and(eq("enabled", true), lt("versionCode", version), gt("expiresAt", Date()),
        gt("ownerId", job.getString("cursor").orEmpty()))).projection(Document("ownerId", 1))
        .sort(com.mongodb.client.model.Sorts.ascending("ownerId")).limit(1000).maxTime(3, java.util.concurrent.TimeUnit.SECONDS).toList()
    val owners = devices.map { it.getString("ownerId") }.distinct().take(100)
    for (owner in owners) DB.transaction { session ->
        if (DB.users.findOne(session, eq("_id", owner)) != null) {
            lockAccount(session, owner)
            queueNotification(session, owner, "UPDATE", id, "A new Hisab update is available",
                "Hisab ${job.getString("versionName")} is ready. Open the app to review what's new and update safely.")
            val eventId = UUID.nameUUIDFromBytes("UPDATE:$owner:$id".toByteArray(Charsets.UTF_8)).toString()
            DB.notificationOutbox.updateOne(session, eq("_id", eventId), Updates.set("targetVersionCode", version))
        }
    }
    DB.releaseNotificationJobs.updateOne(and(eq("_id", id), eq("lease", lease)), Updates.combine(
        Updates.set("leaseUntil", 0L), Updates.set("cursor", owners.lastOrNull() ?: job.getString("cursor").orEmpty()),
        Updates.set("status", if (devices.size < 1000 && owners.size < 100) "complete" else "pending")))
}

fun Application.startReleaseNotificationWorker() {
    // Phase 13 enables this only after the compatible backend and stable release assets are verified.
    if (System.getenv("HISAB_RELEASE_NOTIFICATIONS_ENABLED") != "true" || !PushGateway.configured) return
    val worker = launch(Dispatchers.IO) {
        var nextLookup = 0L
        while (isActive) {
            try {
                if (System.currentTimeMillis() >= nextLookup) {
                    val (_, release) = GitHubReleaseClient.fetchLatestRelease()
                    if (release != null) announceVerifiedRelease(release.versionName, release.versionCode)
                    nextLookup = System.currentTimeMillis() + 15 * 60_000
                }
                DB.releaseNotificationJobs.find(and(eq("status", "pending"), lte("leaseUntil", System.currentTimeMillis())))
                    .limit(3).toList().forEach { advanceReleaseAudience(it.getString("_id")) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { log.warn("Release notification discovery will retry ({})", error.javaClass.simpleName) }
            delay(10_000)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) { worker.cancel() }
}
