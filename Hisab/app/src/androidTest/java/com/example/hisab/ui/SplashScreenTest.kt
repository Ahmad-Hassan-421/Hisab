package com.example.hisab.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hisab.R
import com.example.hisab.ui.theme.HisabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplashScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun animatedSplashRendersAndFinishes() {
        var finished = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HisabTheme { SplashScreen(onFinished = { finished = true }) }
        }
        compose.onNodeWithContentDescription("Hisab App Logo").assertExists()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("Created by Hantalyze").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertTrue(finished) }
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
