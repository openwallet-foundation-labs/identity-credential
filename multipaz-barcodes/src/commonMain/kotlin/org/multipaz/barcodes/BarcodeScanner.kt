package org.multipaz.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

enum class BarcodeFormat {
    QR_CODE,
    PDF417,
}

data class Barcode(
    val format: BarcodeFormat,
    val boundingBox: Rect,
    val cornerPoints: List<Offset>,
    val text: String
)

expect fun scanBarcode(image: ImageBitmap): List<Barcode>
