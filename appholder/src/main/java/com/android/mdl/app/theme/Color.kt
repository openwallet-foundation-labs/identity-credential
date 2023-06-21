package com.android.mdl.app.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

//Light Theme
val ThemeLightPrimary = Color(0xFF476810)
val ThemeLightOnPrimary = Color(0xFFFFFFFF)
val ThemeLightPrimaryContainer = Color(0xFFC7F089)

//Dark Theme
val ThemeDarkPrimary = Color(0xFFACD370)
val ThemeDarkOnPrimary = Color(0xFF213600)
val ThemeDarkPrimaryContainer = Color(0xFF324F00)

val GreenLight = Color(0xFF00E676)
val GreenDark = Color(0xFF34A853)
val GreenGradient = Brush.linearGradient(colors = listOf(GreenDark, GreenLight))

val BlueLight = Color(0xFF00B0FF)
val BlueDark = Color(0xFF4285F4)
val BlueGradient = Brush.linearGradient(colors = listOf(BlueDark, BlueLight))

val YellowLight = Color(0xFFFFEA00)
val YellowDark = Color(0xFFFBBC05)
val YellowGradient = Brush.linearGradient(colors = listOf(YellowDark, YellowLight))

val RedLight = Color(0xFFFF5722)
val RedDark = Color(0xFFF44336)
val RedGradient = Brush.linearGradient(colors = listOf(RedDark, RedLight))