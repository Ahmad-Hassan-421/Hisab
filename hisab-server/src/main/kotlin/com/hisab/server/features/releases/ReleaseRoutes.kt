package com.hisab.server.features.releases

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
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
    val downloadUrl: String
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
                val errorBody = response.bodyAsText()
                log.warn("GitHub API returned non-success: status=${response.status.value}, body=$errorBody")
                if (response.status == HttpStatusCode.NotFound) {
                    HttpStatusCode.NotFound to ErrorResponse("No releases found on GitHub repository")
                } else {
                    response.status to ErrorResponse("GitHub API returned ${response.status.value}")
                }
            } else {
                val release: GitHubRelease = response.body()
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset == null) {
                    HttpStatusCode.NotFound to ErrorResponse("No APK asset found in latest release (${release.tagName})")
                } else {
                    val parsedVersion = release.tagName.filter { it.isDigit() }.toIntOrNull() ?: 1
                    val updateResponse = AppUpdateResponse(
                        versionCode = parsedVersion,
                        versionName = release.tagName,
                        releaseNotes = release.body,
                        downloadUrl = apkAsset.browserDownloadUrl
                    )
                    HttpStatusCode.OK to updateResponse
                }
            }
        } catch (e: Exception) {
            log.error("Failed to query GitHub Releases API", e)
            HttpStatusCode.InternalServerError to ErrorResponse(e.localizedMessage ?: "Internal server error")
        }
    }
}

fun Route.releaseRoutes() {
    val handleLatestRelease: suspend (ApplicationCall) -> Unit = { call ->
        val (status, result) = GitHubReleaseClient.fetchLatestRelease()
        when (result) {
            is AppUpdateResponse -> call.respond(status, result)
            is ErrorResponse -> call.respond(status, result)
            else -> call.respond(status, result.toString())
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
