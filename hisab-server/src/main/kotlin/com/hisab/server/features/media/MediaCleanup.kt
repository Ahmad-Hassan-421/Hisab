package com.hisab.server.features.media

import com.hisab.server.database.MongoClientProvider as DB
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Sorts
import org.bson.Document
import java.util.Date

/** A grace period and live references protect uploads and independent recovered copies. */
internal suspend fun cleanupMedia(after: String?): String? {
    val assets = DB.mediaAssets.find(and(lt("createdAt", System.currentTimeMillis() - 86_400_000L),
        after?.let { gt("_id", it) } ?: Document())).projection(Document("_id", 1))
        .sort(Sorts.ascending("_id")).limit(30).toList()
    for (asset in assets) DB.transaction { session ->
        val id = asset.getString("_id")
        val alive = DB.mediaReferences.countDocuments(session, and(eq("assetId", id),
            or(exists("expiresAt", false), gt("expiresAt", Date()))))
        if (alive == 0L) DB.mediaAssets.deleteOne(session, eq("_id", id))
    }
    return assets.lastOrNull()?.getString("_id")
}
