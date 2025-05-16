package org.multipaz.barcodes

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode as MLBarcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning

import com.google.mlkit.vision.common.InputImage
import org.multipaz.compose.camera.CameraImage

@OptIn(ExperimentalGetImage::class)
actual fun scanBarcode(cameraImage: CameraImage): List<Barcode> {
    val imageProxy = cameraImage.imageProxy
    val inputImage = InputImage.fromMediaImage(
        /* image = */ imageProxy.image!!,
        /* rotationDegrees = */ 0
    )
    return scanBarcode(inputImage)
}

actual fun scanBarcode(image: ImageBitmap): List<Barcode> {
    val inputImage = InputImage.fromBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* rotationDegrees = */ 0
    )
    return scanBarcode(inputImage)
}

private fun scanBarcode(inputImage: InputImage): List<Barcode> {
    val options = BarcodeScannerOptions.Builder()
        .enableAllPotentialBarcodes()
        .build()
    val scanner = BarcodeScanning.getClient(options)
    val barcodesTask = scanner.process(inputImage)
    Tasks.await(barcodesTask)
    val barcodes = barcodesTask.result

    val ret = mutableListOf<Barcode>()

    for (barcode in barcodes) {
        val boundingBox = Rect(
            left = barcode.boundingBox!!.left.toFloat(),
            top = barcode.boundingBox!!.top.toFloat(),
            right = barcode.boundingBox!!.right.toFloat(),
            bottom = barcode.boundingBox!!.bottom.toFloat()
        )
        val cornerPoints = barcode.cornerPoints!!.map { point ->
            Offset(point.x.toFloat(), point.y.toFloat())
        }
        val format = when (barcode.format) {
            MLBarcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            MLBarcode.FORMAT_PDF417 -> BarcodeFormat.PDF417
            else -> null
        }
        if (format != null) {
            ret.add(
                Barcode(
                    format = format,
                    boundingBox = boundingBox,
                    cornerPoints = cornerPoints,
                    text = barcode.rawValue ?: barcode.url?.url ?: ""
                )
            )
        }
    }
    return ret
}
