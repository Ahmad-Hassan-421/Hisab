package com.example.hisab.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.components.DynamicLogo
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.82f) }
    val rotation = remember { Animatable(-10f) }
    val alpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        rotation.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(500))
        delay(150)
        titleAlpha.animateTo(1f, animationSpec = tween(450))
        delay(700)
        onFinished()
    }

    val backgroundBrush = if (MaterialTheme.colorScheme.background == Color(0xFF121212)) {
        Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF020617)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEAF2FF), Color(0xFFFFFFFF), Color(0xFFDCEBFF)))
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DynamicLogo(
                modifier = Modifier
                    .size(168.dp)
                    .scale(scale.value)
                    .graphicsLayer { rotationY = rotation.value },
                size = 168.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Hisab",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { this.alpha = titleAlpha.value }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Shared ledger for clear, fair balances",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Created by Hantalyze",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
            )
        }
    }
}
