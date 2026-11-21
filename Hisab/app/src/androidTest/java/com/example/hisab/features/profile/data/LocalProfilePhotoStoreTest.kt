package com.example.hisab.features.profile.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalProfilePhotoStoreTest {
    @Test
    fun photoSurvivesAStoreRestartAndCanOnlyBeReadForItsAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alice = UUID.randomUUID().toString()
        val bob = UUID.randomUUID().toString()
        val crop = File.createTempFile("avatar-fixture-", ".jpg", context.cacheDir)
        var stored: File? = null
        try {
            fun writePhoto(width: Int, height: Int, color: Int) {
                val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    image.eraseColor(color)
                    crop.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                } finally {
                    image.recycle()
                }
            }
            writePhoto(48, 48, Color.RED)
            val first = LocalProfilePhotoStore(context).save(alice, Uri.fromFile(crop))
            stored = first.file
            val restarted = LocalProfilePhotoStore(context)
            assertEquals(first.file, (restarted.load(alice) as LocalProfilePhoto.Selected).file)
            assertEquals(LocalProfilePhoto.Unset, restarted.load(bob))
            assertTrue(first.file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
            assertFalse(first.file.name.contains(alice))

            val original = first.file.readBytes()
            writePhoto(80, 40, Color.BLUE)
            val rejected = runCatching { restarted.save(alice, Uri.fromFile(crop)) }.exceptionOrNull()
            assertTrue(rejected is LocalProfilePhotoStore.InvalidPhoto)
            assertTrue(first.file.readBytes().contentEquals(original))

            writePhoto(512, 512, Color.BLUE)
            val replacement = restarted.save(alice, Uri.fromFile(crop))
            val decoded = BitmapFactory.decodeFile(replacement.file.path)
            try {
                assertEquals(512, decoded.width)
                assertTrue(Color.blue(decoded.getPixel(150, 150)) > 200)
            } finally {
                decoded.recycle()
            }
            replacement.file.writeText("corrupted image")
            assertEquals(LocalProfilePhoto.Removed, restarted.load(alice))

            restarted.remove(alice)
            assertEquals(LocalProfilePhoto.Removed, LocalProfilePhotoStore(context).load(alice))
            assertEquals(0L, first.file.length())
        } finally {
            crop.delete()
            stored?.delete()
        }
        Unit
    }

    @Test
    fun saveProcessRestartFixture() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val account = InstrumentationRegistry.getArguments().getString("photoFixture")
        assumeTrue("Run with a dedicated -e photoFixture argument", account != null)
        val context = instrumentation.targetContext
        val crop = File.createTempFile("restart-avatar-", ".jpg", context.cacheDir)
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(Color.GREEN)
            crop.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            LocalProfilePhotoStore(context).save(requireNotNull(account), Uri.fromFile(crop))
        } finally {
            image.recycle()
            crop.delete()
        }
        Unit
    }

    @Test
    fun readProcessRestartFixture() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val account = InstrumentationRegistry.getArguments().getString("photoFixture")
        assumeTrue("Run with a dedicated -e photoFixture argument", account != null)
        val context = instrumentation.targetContext
        val store = LocalProfilePhotoStore(context)
        val selected = store.load(requireNotNull(account)) as LocalProfilePhoto.Selected
        try {
            val decoded = BitmapFactory.decodeFile(selected.file.path)
            try {
                assertTrue(Color.green(decoded.getPixel(32, 32)) > 200)
            } finally {
                decoded.recycle()
            }
        } finally {
            selected.file.delete()
        }
        Unit
    }
}
