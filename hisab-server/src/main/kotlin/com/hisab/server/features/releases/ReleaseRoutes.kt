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
    val size: Long? = null,
    val digest: String? = null
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class AppUpdateResponse(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val required: Boolean
)

@Serializable
internal data class ReleaseManifest(
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val sizeBytes: Long,
    val sha256: String
)

private const val releaseBase = "https://github.com/Ahmad-Hassan-421/Hisab/releases/download/"
private val releaseTag = Regex("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
private val sha256Pattern = Regex("[a-f0-9]{64}")
private const val manifestName = "hisab-update.json"

internal fun verifiedRelease(release: GitHubRelease, metadata: ReleaseManifest): AppUpdateResponse? {
    if (!releaseTag.matches(release.tagName) || metadata.versionName != release.tagName.removePrefix("v") ||
        metadata.applicationId != "com.example.hisab" || metadata.versionCode !in 1..2_100_000_000 ||
        metadata.sizeBytes <= 0 || !sha256Pattern.matches(metadata.sha256)) return null
    val apkName = "hisab-${release.tagName}.apk"
    val apk = release.assets.singleOrNull { it.name == apkName } ?: return null
    val url = "$releaseBase${release.tagName}/$apkName"
    if (apk.browserDownloadUrl != url || apk.size != metadata.sizeBytes || apk.digest != "sha256:${metadata.sha256}") {
        return null
    }
    return AppUpdateResponse(metadata.versionCode, metadata.versionName, release.body.orEmpty().take(4_000), url,
        metadata.sizeBytes, metadata.sha256, required = false)
}

private object GitHubReleaseClient {
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

    suspend fun fetchLatestRelease(): Pair<HttpStatusCode, AppUpdateResponse?> {
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
                if (response.status == HttpStatusCode.NotFound) HttpStatusCode.NoContent to null
                else HttpStatusCode.BadGateway to null
            } else {
                val release: GitHubRelease = response.body()
                val asset = release.assets.singleOrNull { it.name == manifestName }
                val manifestUrl = "$releaseBase${release.tagName}/$manifestName"
                if (!releaseTag.matches(release.tagName) || asset?.browserDownloadUrl != manifestUrl ||
                    (asset.size ?: 0L) !in 1L..4096L) return HttpStatusCode.BadGateway to null
                val manifest = httpClient.get(manifestUrl)
                if (!manifest.status.isSuccess()) return HttpStatusCode.BadGateway to null
                val raw = manifest.bodyAsText()
                if (raw.length > 4096) return HttpStatusCode.BadGateway to null
                val update = verifiedRelease(release, jsonConfig.decodeFromString<ReleaseManifest>(raw))
                if (update == null) HttpStatusCode.BadGateway to null else HttpStatusCode.OK to update
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Release lookup failed ({})", e.javaClass.simpleName)
            HttpStatusCode.BadGateway to null
        }
    }
}

fun Route.releaseRoutes() {
    val handleLatestRelease: suspend (ApplicationCall) -> Unit = { call ->
        val (status, result) = GitHubReleaseClient.fetchLatestRelease()
        if (result != null) call.respond(status, result)
        else if (status == HttpStatusCode.NoContent) call.respond(status)
        else call.respondFailure(status, "The release service is unavailable")
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
