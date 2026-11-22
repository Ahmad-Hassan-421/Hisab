package com.example.hisab.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.R
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SplashScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightSplashRendersAndFinishesOnce() = verifySplash(dark = false)

    @Test
    fun darkSplashRendersAndFinishesOnce() = verifySplash(dark = true)

    private fun verifySplash(dark: Boolean) {
        var finishes = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HisabTheme(darkTheme = dark) { SplashScreen(onFinished = { finishes++ }) }
        }
        compose.onNodeWithContentDescription("Hisab App Logo").assertExists()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("Created by Hantalyze").assertIsDisplayed()
        if (Build.VERSION.SDK_INT >= 26) {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val background = image.getPixel(4, 4)
            assertTrue(if (dark) Color.red(background) < 100 else Color.red(background) > 200)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = requireNotNull(context.getExternalFilesDir("splash-previews"))
            val name = if (dark) "dark.png" else "light.png"
            File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1, finishes) }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(1, finishes) }
    }

    @Test
    fun recompositionUsesLatestCompletionCallback() {
        val useLatest = mutableStateOf(false)
        var previousCalls = 0
        var latestCalls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HisabTheme {
                SplashScreen(onFinished = if (useLatest.value) ({ latestCalls++ }) else ({ previousCalls++ }))
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { useLatest.value = true }
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle {
            assertEquals(0, previousCalls)
            assertEquals(1, latestCalls)
        }
    }

    @Test
    fun androidStartingWindowHasNoLogo() {
        if (Build.VERSION.SDK_INT < 31) return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_Hisab_Starting, true) }
        val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowSplashScreenAnimatedIcon))
        try {
            val icon = attributes.getDrawable(0)
            assertTrue(icon is ColorDrawable)
            assertEquals(Color.TRANSPARENT, (icon as ColorDrawable).color)
        } finally {
            attributes.recycle()
        }
    }
}
