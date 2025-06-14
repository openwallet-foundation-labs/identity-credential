package org.multipaz.compose.qrcode

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Generates a QR code.
 *
 * This is guaranteed to include a quiet zone four modules wide.
 *
 * @param url the URL with the contents of the QR code.
 * @return an [ImageBitmap] with the QR code.
 */
expect fun generateQrCode(
    url: String,
): ImageBitmap
