package com.hisab.server.features.releases

import com.hisab.server.plugins.respondFailure
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long? = null
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class AppUpdateResponse(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sizeBytes: Long? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

object GitHubReleaseClient {
    private val log = LoggerFactory.getLogger(GitHubReleaseClient::class.java)
    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/Ahmad-Hassan-421/Hisab/releases/latest"

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    suspend fun fetchLatestRelease(): Pair<HttpStatusCode, Any> {
        val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
        return try {
            val response: HttpResponse = httpClient.get(GITHUB_RELEASE_URL) {
                header(HttpHeaders.UserAgent, "Hisab-Backend-Ktor")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                if (!githubToken.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $githubToken")
                }
            }

            if (!response.status.isSuccess()) {
                log.warn("Release provider returned status={}", response.status.value)
                if (response.status == HttpStatusCode.NotFound) {
                    HttpStatusCode.NotFound to ErrorResponse("No releases found on GitHub repository")
                } else {
                    HttpStatusCode.BadGateway to ErrorResponse("The release service is unavailable")
                }
            } else {
                val release: GitHubRelease = response.body()
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset == null) {
                    HttpStatusCode.NotFound to ErrorResponse("No APK is available for the latest release")
                } else {
                    val parsedVersion = release.tagName.filter { it.isDigit() }.toIntOrNull() ?: 1
                    val updateResponse = AppUpdateResponse(
                        versionCode = parsedVersion,
                        versionName = release.tagName,
                        releaseNotes = release.body,
                        downloadUrl = apkAsset.browserDownloadUrl,
                        sizeBytes = apkAsset.size?.takeIf { it > 0 }
                    )
                    HttpStatusCode.OK to updateResponse
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Release lookup failed ({})", e.javaClass.simpleName)
            HttpStatusCode.BadGateway to ErrorResponse("The release service is unavailable")
        }
    }
}

fun Route.releaseRoutes() {
    val handleLatestRelease: suspend (ApplicationCall) -> Unit = { call ->
        val (status, result) = GitHubReleaseClient.fetchLatestRelease()
        when (result) {
            is AppUpdateResponse -> call.respond(status, result)
            is ErrorResponse -> call.respondFailure(status, result.error)
            else -> call.respondFailure(HttpStatusCode.InternalServerError, "Request failed")
        }
    }

    route("/api/releases") {
        get("/latest") {
            handleLatestRelease(call)
        }
    }

    route("/releases") {
        get("/latest") {
            handleLatestRelease(call)
        }
    }
}
