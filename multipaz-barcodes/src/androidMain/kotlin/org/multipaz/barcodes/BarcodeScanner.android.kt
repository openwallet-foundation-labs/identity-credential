package org.multipaz.barcodes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode as MLBarcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning

import com.google.mlkit.vision.common.InputImage

actual fun barcodeScannerPlatform(): String {
    return "Android"
}

actual fun scanBarcode(image: ImageBitmap): List<Barcode> {

    val inputImage = InputImage.fromBitmap(
        /* bitmap = */ image.asAndroidBitmap(),
        /* rotationDegrees = */ 0
    )
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            MLBarcode.FORMAT_QR_CODE
        )
        .build()
    val scanner = BarcodeScanning.getClient(options)
    val barcodesTask = scanner.process(inputImage)
    Tasks.await(barcodesTask)
    println("done")
    val barcodes = barcodesTask.result
    println("num barcodes ${barcodes.size}")

    val ret = mutableListOf<Barcode>()

    for (barcode in barcodes) {
        println("barcode ${barcode.valueType} ${barcode.rawValue!!}")
        val boundingBox = Rect(
            left = barcode.boundingBox!!.left.toFloat(),
            top = barcode.boundingBox!!.top.toFloat(),
            right = barcode.boundingBox!!.right.toFloat(),
            bottom = barcode.boundingBox!!.bottom.toFloat()
        )
        val cornerPoints = barcode.cornerPoints!!.map { point ->
            Offset(point.x.toFloat(), point.y.toFloat())
        }
        when (barcode.format) {
            MLBarcode.FORMAT_QR_CODE -> {
                ret.add(
                    Barcode(
                        format = BarcodeFormat.QR_CODE,
                        boundingBox = boundingBox,
                        cornerPoints = cornerPoints,
                        text = barcode.rawValue!!
                    )
                )
            }
        }
    }
    return ret
}
