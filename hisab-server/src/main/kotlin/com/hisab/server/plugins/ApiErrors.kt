package com.hisab.server.plugins

import com.mongodb.MongoException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.util.UUID

internal class RequestFailure(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@Serializable
private data class ApiError(val code: Int, val error: String, val requestId: String)

private const val REQUEST_ID = "X-Request-ID"
private val RequestCorrelation = createApplicationPlugin("RequestCorrelation") {
    onCall { call -> call.response.header(REQUEST_ID, UUID.randomUUID().toString()) }
}

internal suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, message: String = status.description) {
    respond(status, ApiError(status.value, message, response.headers[REQUEST_ID].orEmpty()))
}

fun Application.configureErrors() {
    install(RequestCorrelation)
    install(StatusPages) {
        exception<CancellationException> { _, cause -> throw cause }
        exception<RequestFailure> { call, cause -> call.respondFailure(cause.status, cause.message) }
        exception<BadRequestException> { call, _ -> call.respondFailure(HttpStatusCode.BadRequest, "Invalid request body") }
        exception<MongoException> { call, cause ->
            call.application.log.warn("requestId={} status=503 failure={}", call.response.headers[REQUEST_ID], cause.javaClass.simpleName)
            call.respondFailure(HttpStatusCode.ServiceUnavailable, "Database operation unavailable")
        }
        exception<Exception> { call, cause ->
            call.application.log.error("requestId={} status=500 failure={}", call.response.headers[REQUEST_ID], cause.javaClass.simpleName)
            call.respondFailure(HttpStatusCode.InternalServerError, "Request failed")
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respondFailure(status)
        }
    }
}
