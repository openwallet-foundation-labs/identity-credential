package org.multipaz_credential.wallet.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightThemeColors = lightColorScheme(
    // background color
    surface = ICThemeColorLight.surface,
    // header & content, buttons cancel, delete and done colors
    onSurface = ICThemeColorLight.onSurface,
    // Passphrase text field color
    primary = ICThemeColorLight.primary,
    // passphrase PIN keypad button background
    secondary = ICThemeColorLight.secondary,
    // passphrase PIN keypad button text
    onSecondary =  ICThemeColorLight.onSecondary,
    // passphrase PIN keypad button on tap ripple effect
    scrim = ICThemeColorLight.scrim
)

private val DarkThemeColors = darkColorScheme(
    // background color
    surface = ICThemeColorDark.surface,
    // header & content, buttons cancel, delete and done colors
    onSurface = ICThemeColorDark.onSurface,
    // Passphrase text field color
    primary = ICThemeColorDark.primary,
    // passphrase PIN keypad button background
    secondary = ICThemeColorDark.secondary,
    // passphrase PIN keypad button text
    onSecondary =  ICThemeColorDark.onSecondary,
    // passphrase PIN keypad button on tap ripple effect
    scrim = ICThemeColorDark.scrim
)

@Composable
fun IdentityCredentialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkThemeColors
        else -> LightThemeColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // TODO: statusBarColor is deprecated, new impl needed for compatibility.
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}