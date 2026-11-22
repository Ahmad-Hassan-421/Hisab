package com.hisab.server.features.health

import com.hisab.server.database.MongoClientProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

fun Route.healthRoutes(databaseProbe: suspend () -> Unit = { MongoClientProvider.ping() }) {
    healthEndpoint("/health", "{\"status\":\"ok\"}", "{\"status\":\"unavailable\"}",
        ContentType.Application.Json, databaseProbe)
    healthEndpoint("/healthz", "ok", "unavailable", ContentType.Text.Plain, databaseProbe)
    healthEndpoint("/ready", "{\"status\":\"ready\"}", "{\"status\":\"not_ready\"}",
        ContentType.Application.Json, databaseProbe)
}

private fun Route.healthEndpoint(
    path: String,
    healthyBody: String,
    unavailableBody: String,
    contentType: ContentType,
    databaseProbe: suspend () -> Unit
) {
    suspend fun ApplicationCall.respondHealth(headOnly: Boolean) {
        val ready = databaseReady(databaseProbe)
        val content = TextContent(
            if (ready) healthyBody else unavailableBody,
            contentType.withParameter("charset", "UTF-8"),
            if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        )
        response.header(HttpHeaders.CacheControl, "no-store")
        if (headOnly) {
            // HEAD has GET's representation headers and status, but no response body.
            respond(object : OutgoingContent.NoContent() {
                override val status = content.status
                override val contentType = content.contentType
                override val contentLength = content.contentLength
            })
        } else {
            respond(content)
        }
    }
    get(path) { call.respondHealth(headOnly = false) }
    head(path) { call.respondHealth(headOnly = true) }
}

private suspend fun databaseReady(probe: suspend () -> Unit): Boolean = try {
    withTimeoutOrNull(2_000) { probe(); true } == true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
