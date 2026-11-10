package com.hisab.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import java.net.URI

fun Application.configureCORS() {
    val origins = System.getenv("CORS_ALLOWED_ORIGINS").orEmpty().split(',')
        .map(String::trim).filter(String::isNotEmpty)
        .map { raw ->
            val origin = runCatching { URI(raw) }.getOrNull()
            require(origin != null && origin.scheme in listOf("https", "http") &&
                origin.host != null && origin.userInfo == null && origin.rawPath.isNullOrEmpty() &&
                origin.rawQuery == null && origin.rawFragment == null
            ) { "CORS_ALLOWED_ORIGINS must contain comma-separated origins" }
            origin
        }
    if (origins.isEmpty()) return

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        origins.forEach { origin -> allowHost(origin.rawAuthority, schemes = listOf(origin.scheme)) }
    }
}
