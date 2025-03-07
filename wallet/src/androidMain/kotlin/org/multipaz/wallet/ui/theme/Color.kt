package org.multipaz_credential.wallet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Identity Credential (IC) Light theme colors - such as for Presentment Prompts
 */
object ICThemeColorLight {
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

/**
 * Identity Credential (IC) Dark theme colors - such as for Presentment Prompts
 */
object ICThemeColorDark {
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