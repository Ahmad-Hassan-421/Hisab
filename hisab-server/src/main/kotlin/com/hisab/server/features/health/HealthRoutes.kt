package com.hisab.server.features.health

import com.hisab.server.database.MongoClientProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

fun Route.healthRoutes(databaseProbe: suspend () -> Unit = { MongoClientProvider.ping() }) {
    get("/health") {
        val ready = databaseReady(databaseProbe)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(
            if (ready) "{\"status\":\"ok\"}" else "{\"status\":\"unavailable\"}",
            ContentType.Application.Json,
            if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        )
    }
    get("/healthz") {
        val ready = databaseReady(databaseProbe)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(if (ready) "ok" else "unavailable",
            status = if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
    }
    get("/ready") {
        val ready = databaseReady(databaseProbe)
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(
            if (ready) "{\"status\":\"ready\"}" else "{\"status\":\"not_ready\"}",
            ContentType.Application.Json,
            if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        )
    }
}

private suspend fun databaseReady(probe: suspend () -> Unit): Boolean = try {
    withTimeoutOrNull(2_000) { probe(); true } == true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}
