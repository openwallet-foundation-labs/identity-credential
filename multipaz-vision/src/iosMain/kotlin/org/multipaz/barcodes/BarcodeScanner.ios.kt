package org.multipaz.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import cocoapods.GoogleMLKit.MLKBarcode
import cocoapods.GoogleMLKit.MLKBarcodeFormatPDF417
import cocoapods.GoogleMLKit.MLKBarcodeFormatQRCode
import cocoapods.GoogleMLKit.MLKBarcodeScanner
import cocoapods.MLKitVision.MLKVisionImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import org.multipaz.compose.camera.CameraImage
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGPoint
import platform.Foundation.NSValue
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
actual fun scanBarcode(cameraImage: CameraImage): List<Barcode> {
    return scanBarcode(MLKVisionImage(cameraImage.uiImage))
}

@OptIn(ExperimentalForeignApi::class)
actual fun scanBarcode(image: ImageBitmap): List<Barcode> {
    return scanBarcode(MLKVisionImage(image.toUIImage()!!))
}

@OptIn(ExperimentalForeignApi::class)
private fun scanBarcode(image: MLKVisionImage): List<Barcode> {
    val scanner = MLKBarcodeScanner.barcodeScanner()
    val ret = mutableListOf<Barcode>()

    val barcodes = scanner.resultsInImage(
        image = image as objcnames.protocols.MLKCompatibleImageProtocol,
        error = null
    ) as List<MLKBarcode>?
    if (barcodes != null) {
        for (barcode in barcodes) {
            val cornerPoints = mutableListOf<Offset>()
            for (nsv in barcode.cornerPoints as List<NSValue>) {
                memScoped {
                    val cp = alloc<CGPoint>()
                    nsv.getValue(cp.reinterpret(), 16UL)
                    cornerPoints.add(Offset(cp.x.toFloat(), cp.y.toFloat()))
                }
            }
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
                var barcode = Barcode(
                    format = format,
                    boundingBox = boundingBox,
                    cornerPoints = cornerPoints,
                    text = barcode.rawValue ?: barcode.URL?.url ?: ""
                )
                ret.add(barcode)
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
