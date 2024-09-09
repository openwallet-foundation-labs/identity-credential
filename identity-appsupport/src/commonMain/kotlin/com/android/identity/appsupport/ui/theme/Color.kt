package com.android.identity_credential.wallet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AppTheme {
    val lightColorScheme = IdentityCredentialLightColorScheme
    val darkColorScheme = IdentityCredentialDarkColorScheme
}

/**
 * Identity Credential (IC) Light theme colors - such as for Presentment Prompts
 */
private object ICThemeColorLight {
    // background color
    val surface = Color(0XFFF1F0F6)

    // header & content, buttons cancel, delete and done colors
    val onSurface = Color(0XFF45464E)

    // Passphrase text field text color
    val primary = Color(0XFF45464E)

    // keypad button background
    val secondary = Color(0XFFE4E4EB)

    // keypad button text
    val onSecondary = Color(0XFF373638)

    // keypad button on tap ripple effect (same color as text)
    val scrim = onSecondary
}

val IdentityCredentialLightColorScheme = lightColorScheme(
    // background color
    surface = ICThemeColorLight.surface,
    // header & content, buttons cancel, delete and done colors
    onSurface = ICThemeColorLight.onSurface,
    // Passphrase text field color
    primary = ICThemeColorLight.primary,
    // passphrase PIN keypad button background
    secondary = ICThemeColorLight.secondary,
    // passphrase PIN keypad button text
    onSecondary = ICThemeColorLight.onSecondary,
    // passphrase PIN keypad button on tap ripple effect
    scrim = ICThemeColorLight.scrim
)

/**
 * Identity Credential (IC) Dark theme colors - such as for Presentment Prompts
 */
private object ICThemeColorDark {
    // background
    val surface = Color(0XFF1A1B20)

    // header & content, buttons cancel, delete and done colors
    val onSurface = Color(0XFFC5C6CF)

    // Passphrase text field text color
    val primary = Color(0XFFC5C6CF)

    // keypad button background
    val secondary = Color(0XFF28292F)

    // keypad button text
    val onSecondary = Color(0XFFE3E2E8)

    // keypad button on tap ripple effect (same color as text)
    val scrim = onSecondary
}

val IdentityCredentialDarkColorScheme = darkColorScheme(
    // background color
    surface = ICThemeColorDark.surface,
    // header & content, buttons cancel, delete and done colors
    onSurface = ICThemeColorDark.onSurface,
    // Passphrase text field color
    primary = ICThemeColorDark.primary,
    // passphrase PIN keypad button background
    secondary = ICThemeColorDark.secondary,
    // passphrase PIN keypad button text
    onSecondary = ICThemeColorDark.onSecondary,
    // passphrase PIN keypad button on tap ripple effect
    scrim = ICThemeColorDark.scrim
)