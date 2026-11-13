package com.example.hisab.util

import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.ConnectivityManager
import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

enum class AppFailure(val message: String) {
    OFFLINE("You're offline. Check your connection and try again."),
    UNAVAILABLE("We couldn't connect right now. Please try again shortly."),
    TIMEOUT("This is taking longer than expected. Please try again."),
    INVALID_RESPONSE("We couldn't read the server response. Please try again."),
    SESSION_EXPIRED("Your session has expired. Please sign in again."),
    FORBIDDEN("You don't have permission to perform this action."),
    MISSING("This item is unavailable or has been removed."),
    VALIDATION("Check the information you entered and try again."),
    CONFLICT("The information has changed. Refresh and try again."),
    STORAGE("We couldn't access the saved data. Please try again."),
    RATE_LIMITED("Too many requests. Wait a moment and try again."),
    UNEXPECTED("Something went wrong. Please try again.");

    companion object {
        internal fun classify(error: Throwable, hasNetwork: Boolean): AppFailure = when (error) {
            is CancellationException -> throw error
            is HttpException -> when (error.code()) {
                400, 422 -> VALIDATION
                401 -> SESSION_EXPIRED
                403 -> FORBIDDEN
                404, 410 -> MISSING
                409 -> CONFLICT
                429 -> RATE_LIMITED
                408, 504 -> TIMEOUT
                in 500..599 -> UNAVAILABLE
                else -> UNEXPECTED
            }
            is JsonParseException, is MalformedJsonException, is EOFException, is JSONException -> INVALID_RESPONSE
            is SQLiteException -> STORAGE
            is SecurityException -> FORBIDDEN
            is SocketTimeoutException -> if (hasNetwork) TIMEOUT else OFFLINE
            is IOException -> if (hasNetwork) UNAVAILABLE else OFFLINE
            else -> UNEXPECTED
        }
    }
}

class FailureReporter internal constructor(
    private val hasNetwork: () -> Boolean,
    private val log: (String) -> Unit
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        { context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null },
        { Log.w("Hisab", it) }
    )

    fun report(operation: String, error: Throwable): AppFailure {
        val failure = AppFailure.classify(error, error !is IOException || hasNetwork())
        log("$operation failed (${failure.name}, ${error.javaClass.simpleName})")
        return failure
    }
}
