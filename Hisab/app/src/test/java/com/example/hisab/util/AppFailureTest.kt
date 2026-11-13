package com.example.hisab.util

import android.database.sqlite.SQLiteException
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException

class AppFailureTest {
    @Test
    fun mapsHttpErrorsWithoutExposingResponseBodies() {
        val expected = mapOf(
            400 to AppFailure.VALIDATION,
            401 to AppFailure.SESSION_EXPIRED,
            403 to AppFailure.FORBIDDEN,
            404 to AppFailure.MISSING,
            409 to AppFailure.CONFLICT,
            410 to AppFailure.MISSING,
            422 to AppFailure.VALIDATION,
            429 to AppFailure.RATE_LIMITED,
            500 to AppFailure.UNAVAILABLE,
            503 to AppFailure.UNAVAILABLE,
            504 to AppFailure.TIMEOUT
        )
        for ((status, failure) in expected) {
            val error = HttpException(Response.error<Unit>(status, "private backend details".toResponseBody()))
            assertEquals(failure, AppFailure.classify(error, hasNetwork = true))
            assertFalse(failure.message.contains("private backend details"))
        }
    }

    @Test
    fun distinguishesOfflineTimeoutAndServerConnectionFailure() {
        assertEquals(AppFailure.OFFLINE, AppFailure.classify(ConnectException(), hasNetwork = false))
        assertEquals(AppFailure.UNAVAILABLE, AppFailure.classify(ConnectException(), hasNetwork = true))
        assertEquals(AppFailure.TIMEOUT, AppFailure.classify(SocketTimeoutException(), hasNetwork = true))
    }

    @Test
    fun classifiesMalformedResponsesStorageAndUnexpectedFailures() {
        assertEquals(AppFailure.INVALID_RESPONSE, AppFailure.classify(JsonSyntaxException("payload"), true))
        assertEquals(AppFailure.STORAGE, AppFailure.classify(SQLiteException(), true))
        assertEquals(AppFailure.UNEXPECTED, AppFailure.classify(IllegalStateException("internal detail"), true))
    }

    @Test(expected = CancellationException::class)
    fun propagatesCancellation() {
        AppFailure.classify(CancellationException(), true)
    }
}
