package com.hisab.server.features.paging

import com.hisab.server.plugins.RequestFailure
import com.mongodb.client.model.Filters
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable
import org.bson.Document
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class CursorPage<T>(val items: List<T>, val nextCursor: String? = null, val revision: Long? = null)

internal data class PagePosition(val time: Long?, val id: String, val ceiling: Long, val revision: Long?) {
    fun after(field: String) = if (time == null) {
        Filters.and(Filters.eq(field, null), Filters.lt("_id", id))
    } else {
        Filters.or(Filters.lt(field, time), Filters.eq(field, null),
            Filters.and(Filters.eq(field, time), Filters.lt("_id", id)))
    }
}

internal class CursorCodec(secret: String) {
    private val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    private fun sign(value: ByteArray) = Mac.getInstance("HmacSHA256").run { init(key); doFinal(value) }
    private fun scope(value: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    fun encode(scope: String, position: PagePosition): String {
        val content = "1|${scope(scope)}|${position.time ?: ""}|${position.id}|${position.ceiling}|${position.revision ?: ""}"
            .toByteArray(Charsets.UTF_8)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "${encoder.encodeToString(content)}.${encoder.encodeToString(sign(content))}"
    }

    fun decode(scope: String, value: String): PagePosition? = runCatching {
        require(value.length in 1..768)
        val pieces = value.split('.')
        require(pieces.size == 2)
        val decoder = Base64.getUrlDecoder()
        val bytes = decoder.decode(pieces[0])
        require(MessageDigest.isEqual(sign(bytes), decoder.decode(pieces[1])))
        val parts = bytes.toString(Charsets.UTF_8).split('|')
        require(parts.size == 6 && parts[0] == "1" && parts[1] == scope(scope))
        val time = parts[2].takeIf(String::isNotEmpty)?.toLong()
        require(parts[3].length in 1..256)
        val ceiling = parts[4].toLong().also { require(it >= 0 && (time == null || time <= it)) }
        val revision = parts[5].takeIf(String::isNotEmpty)?.toLong()?.also { require(it >= 0) }
        PagePosition(time, parts[3], ceiling, revision)
    }.getOrNull()
}

private val codec by lazy { CursorCodec(requireNotNull(System.getenv("JWT_SECRET"))) }

internal data class PageQuery(val scope: String, val limit: Int, val position: PagePosition?) {
    val ceiling = position?.ceiling ?: System.currentTimeMillis()
    fun filter(field: String) = Filters.and(
        Filters.or(Filters.lte(field, ceiling), Filters.eq(field, null)),
        position?.after(field) ?: Document())

    fun next(rows: List<Document>, timeField: String, revision: Long? = null): String? =
        if (rows.size <= limit) null else rows[limit - 1].let {
            codec.encode(scope, PagePosition((it[timeField] as? Number)?.toLong(), it.getString("_id"), ceiling, revision))
        }
}

internal fun ApplicationCall.pageQuery(scope: String): PageQuery {
    val limit = (request.queryParameters["limit"] ?: "30").toIntOrNull()?.takeIf { it in 1..100 }
        ?: throw RequestFailure(HttpStatusCode.BadRequest, "Page size must be 1 to 100")
    val position = request.queryParameters["cursor"]?.let {
        codec.decode(scope, it) ?: throw RequestFailure(HttpStatusCode.BadRequest, "Invalid or mismatched page cursor")
    }
    return PageQuery(scope, limit, position)
}
