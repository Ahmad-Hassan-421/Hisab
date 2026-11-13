package com.example.hisab.data.remote.api

import android.util.Log
import com.example.hisab.util.Constants
import com.example.hisab.util.FailureReporter
import com.example.hisab.util.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val tokenManager: TokenManager,
    private val okHttpClient: OkHttpClient,
    private val failures: FailureReporter
) {
    private var webSocket: WebSocket? = null
    private val _incomingMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<JSONObject> = _incomingMessages.asSharedFlow()

    fun connect() {
        val token = tokenManager.getToken() ?: return
        val url = Constants.API_BASE_URL.toHttpUrl().newBuilder().addPathSegment("ws").build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    _incomingMessages.tryEmit(json)
                } catch (e: JSONException) {
                    failures.report("Read realtime message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failures.report("Connect realtime updates", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "App paused")
        webSocket = null
    }
}
