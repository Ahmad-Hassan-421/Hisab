package com.example.hisab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hisab.R
import com.example.hisab.ui.theme.LocalThemeIsDark

@Composable
fun DynamicLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    val isDarkTheme = LocalThemeIsDark.current
    
    val logoRes = if (isDarkTheme) {
        R.drawable.logo_dark_theme
    } else {
        R.drawable.logo_light_theme
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = "Hisab App Logo",
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentScale = contentScale
        )
    }
}
