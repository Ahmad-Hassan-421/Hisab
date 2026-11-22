package com.example.hisab.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hisab.ui.components.DynamicLogo
import com.example.hisab.ui.theme.LocalThemeIsDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val currentOnFinished = rememberUpdatedState(onFinished)
    val dark = LocalThemeIsDark.current
    val accent = if (dark) Color(0xFF8CE8F2) else Color(0xFFA56A3F)
    val foreground = if (dark) Color(0xFFE9F5FA) else Color(0xFF493320)
    val background = if (dark) Color(0xFF070E18) else Color(0xFFFAF5ED)

    // A finite timeline respects the system animator duration scale and cannot loop
    // indefinitely. Recomposition uses the latest callback without restarting it.
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1_800, easing = LinearEasing))
        currentOnFinished.value()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(if (dark) listOf(Color(0xFF070E18), Color(0xFF142635), background)
                else listOf(Color(0xFFFAF5ED), Color(0xFFFFFCF7), Color(0xFFF0E4D4))))
        ) {
            val emblemSize = minOf(256.dp, maxWidth - 64.dp, maxHeight * 0.42f).coerceAtLeast(120.dp)
            Canvas(Modifier.fillMaxSize()) {
                val phase = progress.value
                val center = Offset(size.width * (0.45f + phase * 0.1f), size.height * 0.38f)
                drawCircle(Brush.radialGradient(
                    listOf(accent.copy(alpha = if (dark) 0.15f else 0.10f), Color.Transparent),
                    center, size.width * 0.8f), size.width * 0.8f, center)
                repeat(8) { index ->
                    val angle = index * PI / 4 + phase * 0.35
                    val radius = size.width * (0.28f + (index % 3) * 0.09f)
                    val point = Offset(center.x + cos(angle).toFloat() * radius,
                        center.y + sin(angle).toFloat() * radius * 0.7f)
                    drawCircle(accent.copy(alpha = 0.22f * sin(phase * PI).toFloat()),
                        if (index % 2 == 0) 2.dp.toPx() else 1.dp.toPx(), point)
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(emblemSize + 36.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val phase = progress.value
                        val reveal = (phase / 0.45f).coerceIn(0f, 1f)
                        drawCircle(accent.copy(alpha = 0.16f * reveal), size.minDimension * 0.49f,
                            style = Stroke(1.dp.toPx()))
                        drawArc(accent.copy(alpha = 0.50f * reveal), -130f + phase * 55f, 66f,
                            useCenter = false, style = Stroke(2.dp.toPx()),
                            topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                            size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()))
                    }
                    DynamicLogo(
                        modifier = Modifier.size(emblemSize).graphicsLayer {
                            val entrance = FastOutSlowInEasing.transform((progress.value / 0.58f).coerceIn(0f, 1f))
                            val float = sin(progress.value * PI).toFloat()
                            alpha = (progress.value / 0.18f).coerceIn(0f, 1f)
                            rotationY = -24f * (1f - entrance)
                            rotationX = 12f * (1f - entrance)
                            rotationZ = -4f * (1f - entrance)
                            scaleX = 0.86f + 0.14f * entrance + 0.025f * float
                            scaleY = scaleX
                            translationY = 18.dp.toPx() * (1f - entrance) - 6.dp.toPx() * float
                            cameraDistance = 18f * density
                            shadowElevation = (if (dark) 18.dp else 12.dp).toPx()
                            shape = RoundedCornerShape(28.dp)
                            clip = true
                        }.clip(RoundedCornerShape(28.dp))
                            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                            .drawWithContent {
                                drawContent()
                                val sweep = ((progress.value - 0.25f) / 0.65f).coerceIn(0f, 1f)
                                val x = -size.width + sweep * size.width * 3f
                                drawRect(Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                                    Offset(x - size.width * 0.3f, 0f), Offset(x, size.height)))
                            },
                        size = emblemSize
                    )
                }
                Spacer(Modifier.height(22.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        val reveal = FastOutSlowInEasing.transform(((progress.value - 0.25f) / 0.35f).coerceIn(0f, 1f))
                        alpha = reveal
                        translationY = 12.dp.toPx() * (1f - reveal)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hisab", fontSize = 40.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp, color = foreground)
                    Spacer(Modifier.height(10.dp))
                    Text("Shared ledger for clear, fair balances", fontSize = 14.sp,
                        textAlign = TextAlign.Center, color = foreground.copy(alpha = 0.78f))
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(42.dp, 2.dp).background(accent.copy(alpha = 0.65f), RoundedCornerShape(1.dp)))
                    Spacer(Modifier.height(14.dp))
                    Text("Created by Hantalyze", fontSize = 12.sp,
                        textAlign = TextAlign.Center, color = foreground.copy(alpha = 0.68f))
                }
            }
        }
    }
}
