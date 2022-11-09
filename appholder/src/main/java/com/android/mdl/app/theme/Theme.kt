package com.android.mdl.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val lightColorPalette = lightColorScheme(
    primary = ThemeLightPrimary,
    onPrimary = ThemeLightOnPrimary,
    primaryContainer = ThemeLightPrimaryContainer,
    onPrimaryContainer = ThemeLightPrimaryContainer,
)

private val darkColorPalette = darkColorScheme(
    primary = ThemeDarkPrimary,
    onPrimary = ThemeDarkOnPrimary,
    primaryContainer = ThemeDarkPrimaryContainer,
    onPrimaryContainer = ThemeDarkPrimaryContainer,
)

@Composable
fun HolderAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {

    val colors = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> darkColorPalette
        else -> lightColorPalette
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}