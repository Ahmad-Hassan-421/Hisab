package com.example.hisab.features.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.hisab.BuildConfig
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.util.SessionManager
import com.example.hisab.util.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

fun schedulePushSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<PushDeviceWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
    WorkManager.getInstance(context).enqueueUniqueWork("hisab-push-device", ExistingWorkPolicy.REPLACE, request)
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.resultValue(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

@Singleton
class PushCoordinator @Inject constructor(@ApplicationContext private val context: Context, private val state: PushState,
    private val tokens: TokenManager, private val session: SessionManager, private val api: HisabApiService) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun start() {
        FirebaseMessaging.getInstance().isAutoInitEnabled = tokens.getUserId()?.let { state.enabled(it) } == true
        scope.launch {
            var previous: String? = null
            session.currentUserId.collectLatest { actor ->
                if (previous != actor) {
                    FirebaseMessaging.getInstance().isAutoInitEnabled = actor != null && state.enabled(actor)
                    NotificationManagerCompat.from(context).cancelAll()
                    if (actor != null || previous != null || state.pendingRevoke() > 0) schedulePushSync(context)
                    previous = actor
                }
            }
        }
        if (state.pendingRevoke() > 0) schedulePushSync(context)
    }
    fun enabled(owner: String) = state.enabled(owner) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun decide(owner: String, enabled: Boolean) {
        if (tokens.getUserId() != owner) return
        state.decide(owner, enabled)
        FirebaseMessaging.getInstance().isAutoInitEnabled = enabled
        if (!enabled) NotificationManagerCompat.from(context).cancelAll()
        schedulePushSync(context)
    }
    suspend fun synchronize(): Boolean {
        val proof = state.installation()
        val revoke = state.pendingRevoke()
        if (revoke > 0) {
            api.revokePushDevice(RevokePushDevice(proof.installationId, proof.installationSecret, revoke))
            state.revoked(revoke)
        }
        val owner = tokens.getUserId() ?: return true
        if (tokens.getToken().isNullOrBlank()) return true
        val enabled = enabled(owner)
        val token = if (enabled) FirebaseMessaging.getInstance().token.resultValue().also { state.setToken(it) } else state.token()
        if (token == null || tokens.getUserId() != owner) return true
        val revision = state.nextBinding()
        api.registerPushDevice(PushDevice(proof.installationId, proof.installationSecret, token, BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_CODE, enabled, revision), owner)
        if (tokens.getUserId() == owner) state.savePreferences(owner, api.getPushPreferences().preferences)
        return true
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushWorkerEntryPoint { fun pushCoordinator(): PushCoordinator }

class PushDeviceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, PushWorkerEntryPoint::class.java).pushCoordinator().synchronize()
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: retrofit2.HttpException) {
        if (error.code() in setOf(400, 401, 403, 428)) Result.failure() else Result.retry()
    } catch (_: Exception) { Result.retry() }
}
