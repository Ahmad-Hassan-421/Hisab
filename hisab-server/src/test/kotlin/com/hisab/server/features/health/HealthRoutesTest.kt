package com.hisab.server.features.health

import com.mongodb.MongoException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRoutesTest {
    @Test
    fun healthyDatabaseReturnsMinimalPublicJson() = testApplication {
        environment { config = MapApplicationConfig() }
        var probes = 0
        application { routing { healthRoutes { probes++ } } }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("{\"status\":\"ok\"}", response.bodyAsText())
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith(ContentType.Application.Json.toString()))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(1, probes)

        assertEquals(HttpStatusCode.OK, client.get("/health") {
            header(HttpHeaders.Authorization, "Bearer expired-token")
        }.status)
    }

    @Test
    fun databaseFailureReturns503WithoutExceptionDetails() = testApplication {
        environment { config = MapApplicationConfig() }
        application { routing { healthRoutes { throw MongoException("Private database details") } } }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("{\"status\":\"unavailable\"}", response.bodyAsText())
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun unexpectedProbeFailureAlsoReturns503() = testApplication {
        environment { config = MapApplicationConfig() }
        application { routing { healthRoutes { error("Private connection configuration") } } }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("{\"status\":\"unavailable\"}", response.bodyAsText())
    }

    @Test
    fun databaseTimeoutReturns503() = testApplication {
        environment { config = MapApplicationConfig() }
        application { routing { healthRoutes { delay(10_000) } } }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("{\"status\":\"unavailable\"}", response.bodyAsText())
    }

    @Test
    fun existingReadinessRoutesUseTheSameDatabaseCheck() = testApplication {
        environment { config = MapApplicationConfig() }
        var available = true
        application { routing { healthRoutes { check(available) } } }
        assertEquals("ok", client.get("/healthz").bodyAsText())
        assertEquals("{\"status\":\"ready\"}", client.get("/ready").bodyAsText())
        available = false
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/healthz").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
    }

    @Test
    fun uptimeRobotHeadMatchesGetHeadersAndChecksTheDatabaseForEveryEndpoint() = testApplication {
        environment { config = MapApplicationConfig() }
        var probes = 0
        application { routing { healthRoutes { probes++ } } }

        for (path in listOf("/health", "/healthz", "/ready")) {
            val get = client.get(path)
            val head = client.head(path) {
                header(HttpHeaders.UserAgent, "Mozilla/5.0+(compatible; UptimeRobot/2.0; http://www.uptimerobot.com/)")
                header(HttpHeaders.Authorization, "Bearer expired-token")
            }
            assertEquals(HttpStatusCode.OK, head.status)
            assertEquals("", head.bodyAsText())
            assertEquals(get.headers[HttpHeaders.ContentType], head.headers[HttpHeaders.ContentType])
            assertEquals(get.headers[HttpHeaders.ContentLength], head.headers[HttpHeaders.ContentLength])
            assertEquals("no-store", head.headers[HttpHeaders.CacheControl])
        }
        assertEquals(6, probes)
    }

    @Test
    fun headReportsDatabaseFailureWithoutReturningABody() = testApplication {
        environment { config = MapApplicationConfig() }
        application { routing { healthRoutes { throw MongoException("Private database details") } } }
        for (path in listOf("/health", "/healthz", "/ready")) {
            val get = client.get(path)
            val head = client.head(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, head.status)
            assertEquals("", head.bodyAsText())
            assertEquals(get.headers[HttpHeaders.ContentType], head.headers[HttpHeaders.ContentType])
            assertEquals(get.headers[HttpHeaders.ContentLength], head.headers[HttpHeaders.ContentLength])
            assertEquals("no-store", head.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun headDatabaseTimeoutReturns503() = testApplication {
        environment { config = MapApplicationConfig() }
        application { routing { healthRoutes { delay(10_000) } } }
        val response = client.head("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("", response.bodyAsText())
    }
}
