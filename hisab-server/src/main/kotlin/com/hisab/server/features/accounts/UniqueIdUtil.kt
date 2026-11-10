package com.hisab.server.features.accounts

import com.hisab.server.models.UserDocument
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.eq

object UniqueIdUtil {
    suspend fun generate(usersCollection: CoroutineCollection<UserDocument>): String {
        while (true) {
            val candidate = (1..12).map { (0..9).random() }.joinToString("")
            val exists = usersCollection.findOne(UserDocument::uniqueId eq candidate)
            if (exists == null) {
                return candidate
            }
        }
    }
}
