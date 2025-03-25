package org.multipaz.compose.camera.kmpbitmap

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

actual typealias PlatformImage = UIImage

@OptIn(ExperimentalForeignApi::class)
actual fun platformInitialize(image: PlatformImage): ByteArray {
    val imageDataNS = UIImagePNGRepresentation(image) ?: error("Failed to encode image")
    return imageDataNS.bytes?.let { it as ByteArray } ?: ByteArray(0)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun platformScale(byteArray: ByteArray, width: Int, height: Int): ByteArray {
    val data = NSData.create(bytes = byteArray.usePinned { pinned -> pinned.addressOf(0) }, length = byteArray.size.toULong())
    val uiImage = UIImage(data = data) ?: error("Failed to decode image")
    val size = platform.CoreGraphics.CGSizeMake(width.toDouble(), height.toDouble())
    UIGraphicsBeginImageContext(size)
    val rect = CGRectMake(0.0, 0.0, size.useContents { this.width }, size.useContents { this.height })
    uiImage.drawInRect(rect)
    val scaledImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val scaledData = scaledImage?.let { UIImagePNGRepresentation(it) } ?: error("Failed to encode scaled image")
    return scaledData.bytes?.let { it as ByteArray } ?: ByteArray(0)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun platformDecode(byteArray: ByteArray): PlatformImage {
    val data = NSData.create(bytes = byteArray.usePinned { pinned -> pinned.addressOf(0) }, length = byteArray.size.toULong())
    return UIImage(data = data) ?: error("Failed to decode image")
}

