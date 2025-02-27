package org.multipaz.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.io.bytestring.ByteString

@Composable
expect fun AppTheme(content: @Composable () -> Unit)

@Composable
fun AppThemeDefault(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

expect fun decodeImage(encodedData: ByteString): ImageBitmap
