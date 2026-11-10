package com.hisab.server.plugins

import com.mongodb.MongoException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException

internal class RequestFailure(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

fun Application.configureErrors() {
    install(StatusPages) {
        exception<CancellationException> { _, cause -> throw cause }
        exception<RequestFailure> { call, cause -> call.respond(cause.status, cause.message) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, "Invalid request body") }
        exception<MongoException> { call, cause ->
            call.application.log.warn("MongoDB operation failed (${cause.javaClass.simpleName})")
            call.respond(HttpStatusCode.ServiceUnavailable, "Database operation unavailable")
        }
        exception<Exception> { call, cause ->
            call.application.log.error("Request failed (${cause.javaClass.simpleName})")
            call.respond(HttpStatusCode.InternalServerError, "Request failed")
        }
    }
}
