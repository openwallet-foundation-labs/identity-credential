package org.multipaz.compose

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun AppTheme(content: @Composable () -> Unit) {
    val darkScheme = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkScheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        } else {
            if (darkScheme) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
        }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

actual fun decodeImage(encodedData: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(encodedData, 0, encodedData.size).asImageBitmap()
}
