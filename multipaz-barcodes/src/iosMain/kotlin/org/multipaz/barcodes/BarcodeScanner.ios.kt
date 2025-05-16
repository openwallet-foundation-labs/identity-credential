package org.multipaz.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import cocoapods.GoogleMLKit.MLKBarcode
import cocoapods.GoogleMLKit.MLKBarcodeFormatPDF417
import cocoapods.GoogleMLKit.MLKBarcodeFormatQRCode
import platform.UIKit.UIImage
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGImageAlphaInfo
import cocoapods.GoogleMLKit.MLKBarcodeScanner
import cocoapods.MLKitVision.MLKVisionImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents
import objcnames.protocols.MLKCompatibleImageProtocol

// TODO: untested but should work (!)
@OptIn(ExperimentalForeignApi::class)
actual fun scanBarcode(image: ImageBitmap): List<Barcode> {
    val image = MLKVisionImage(image.toUIImage()!!)
    val scanner = MLKBarcodeScanner.barcodeScanner()
    val ret = mutableListOf<Barcode>()
    scanner.processImage(image as MLKCompatibleImageProtocol) { barcodes, error ->
        if (barcodes != null) {
            barcodes as List<MLKBarcode>
            for (barcode in barcodes) {
                val cornerPoints = mutableListOf<Offset>()  // TODO
                val boundingBox = barcode.frame.useContents {
                    Rect(
                        left = origin.x.toFloat(),
                        top = origin.y.toFloat(),
                        right = (origin.x + size.width).toFloat(),
                        bottom = (origin.y + size.height).toFloat()
                    )
                }

                val format = when (barcode.format) {
                    MLKBarcodeFormatQRCode -> BarcodeFormat.QR_CODE
                    MLKBarcodeFormatPDF417 -> BarcodeFormat.PDF417
                    else -> null
                }
                if (format != null) {
                    ret.add(
                        Barcode(
                            format = format,
                            boundingBox = boundingBox,
                            cornerPoints = cornerPoints,
                            text = barcode.rawValue!!
                        )
                    )
                }
            }
        }
    }
    return ret
}

@OptIn(ExperimentalForeignApi::class)
private fun ImageBitmap.toUIImage(): UIImage? {
    val width = this.width
    val height = this.height
    val buffer = IntArray(width * height)

    this.readPixels(buffer)

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context = CGBitmapContextCreate(
        data = buffer.refTo(0),
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = (4 * width).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
    )

    val cgImage = CGBitmapContextCreateImage(context)
    return cgImage?.let { UIImage.imageWithCGImage(it) }
}
