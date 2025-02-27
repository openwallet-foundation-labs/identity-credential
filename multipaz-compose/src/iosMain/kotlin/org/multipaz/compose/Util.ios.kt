package org.multipaz.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.io.bytestring.ByteString
import org.jetbrains.skia.Image

@Composable
actual fun AppTheme(content: @Composable () -> Unit) {
    return AppThemeDefault(content)
}

@OptIn(ExperimentalFoundationApi::class)
actual fun decodeImage(encodedData: ByteString): ImageBitmap {
    return Image.makeFromEncoded(encodedData.toByteArray()).toComposeImageBitmap()
}
