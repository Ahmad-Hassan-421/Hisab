package com.example.hisab.features.profile.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.util.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MediaStoreTest {
    @Test fun privateVersionsSurviveStoreRestartAndAreRemovedOnRevocationAndAccountSwitch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actor = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val id = UUID.randomUUID().toString()
        val replacement = UUID.randomUUID().toString()
        val session = SessionManager().apply { setSession(actor, "user") }
        var allowed = true
        var reads = 0
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.eraseColor(Color.BLUE); bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output); bitmap.recycle(); output.toByteArray()
        }
        val api = Proxy.newProxyInstance(HisabApiService::class.java.classLoader, arrayOf(HisabApiService::class.java)) { _, method, arguments ->
            when (method.name) {
                "downloadMedia" -> {
                    reads++
                    if (!allowed || session.currentUserId.value != actor) throw HttpException(Response.error<Unit>(404, "{}".toResponseBody()))
                    bytes.toResponseBody("image/jpeg".toMediaType())
                }
                "mediaAccess" -> MediaAccessResponse(if (allowed && session.currentUserId.value == actor)
                    (arguments!![0] as MediaAccessInput).ids.toSet() else emptySet())
                else -> error("Unexpected API call")
            }
        } as HisabApiService
        var store = MediaStore(context, api, session)
        try {
            withTimeout(5000) { store.revision.first { it > 0 } }
            val first = requireNotNull(store.load(actor, id))
            assertArrayEquals(bytes, first.readBytes())
            assertEquals(1, reads)
            store.close()
            store = MediaStore(context, api, session)
            withTimeout(5000) { store.revision.first { it > 0 } }
            assertEquals(first, store.load(actor, id))
            assertEquals(1, reads)
            assertNotEquals(first, store.load(actor, replacement))
            assertEquals(2, reads)
            allowed = false
            store.revalidate()
            assertFalse(first.exists())
            assertNull(store.load(actor, id))
            assertEquals(2, reads)
            allowed = true
            store.revalidate()
            assertNotNull(store.load(actor, id))
            assertEquals(3, reads)
            session.setSession(other, "user")
            withTimeout(5000) { while (first.exists()) delay(20) }
            assertNull(store.load(other, id))
            assertNull(store.load(actor, id))
        } finally {
            store.erase(actor); store.erase(other); store.close(); session.clear()
        }
    }
}
