package org.multipaz.compose.camera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.multipaz.util.Logger
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGBitmapInfo
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCopyICCProfile
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceGetModel
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGDataProviderRef
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGImageGetBitmapInfo
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetColorSpace
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrderDefault
import platform.CoreGraphics.kCGBitmapFloatComponents
import platform.CoreGraphics.kCGColorSpaceModelRGB
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferCGBitmapContextCompatibilityKey
import platform.CoreVideo.kCVPixelBufferCGImageCompatibilityKey
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.dataWithBytesNoCopy
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.darwin.UInt8Var
import platform.posix.memcpy
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.max
import kotlin.math.min

/**
 * Collection of utilities tried to efficiently convert the ImageBuffer to CIImage or ImageBitmap for Composable UI.
 */

private val ciContext = CIContext()
private val TAG = "ImageBufferUtilities"

/** Basic conversion. UIImage seems to be incompatible (not displayed). Affected by 9 sec internal GC. */
@OptIn(ExperimentalForeignApi::class)
internal fun CMSampleBufferRef.toImageBitmap(): ImageBitmap? {
    val pixelBuffer = CMSampleBufferGetImageBuffer(this) ?: return null
    val ciImage = platform.CoreImage.CIImage.imageWithCVPixelBuffer(pixelBuffer)
    val uiImage = platform.UIKit.UIImage.imageWithCIImage(ciImage)

    return uiImage.toImageBitmap()
}

/** Simplified conversion of CVImageBuffer to UIImage using platform CInterop. for CoreGraphics and CoreImage. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun convertCVImageBufferToUIImage(imageBuffer: CVImageBufferRef): UIImage? {
    // Ensure the image buffer is a CVPixelBufferRef (depends on camera settings).
    val pixelBuffer = imageBuffer
    if (pixelBuffer == null) {
        Logger.d(TAG, "CVImageBuffer is not a CVPixelBuffer")
        return null
    }

    // Lock the base address of the pixel buffer. This makes the pixel data accessible.
    val lockResult = CVPixelBufferLockBaseAddress(pixelBuffer, 0u) // No flags.
    if (lockResult != 0) {
        Logger.d(TAG, "Failed to lock pixel buffer base address: $lockResult")
        return null
    }

    val uiImage = autoreleasepool {

        val ciImage = CIImage.imageWithCVImageBuffer(imageBuffer)
        val cgImage = ciContext.createCGImage(ciImage, ciImage.extent())

        // Unlock the base address of the pixel buffer before returning.
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)

        if (cgImage != null) {
            val resultImage = UIImage(cgImage, scale = 1.0, UIImageOrientation.UIImageOrientationUp)
            CGImageRelease(cgImage)
            resultImage
        } else {
            null
        }
    }

    return uiImage
}

/**
 * Converts a CVImageBufferRef to a UIImage as need to display it in platform KMP Composable.
 * Leveraging the internal frame conversion and direct CGImage constructor.
 *
 * @param imageBuffer The CVImageBufferRef to convert.
 * @return A UIImage representation of the image buffer, or null if the conversion fails.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun convertCVImageBufferToUIImageDirect(imageBuffer: CVImageBufferRef): UIImage? {
    // Ensure the image buffer is a CVPixelBufferRef
    val pixelBuffer = imageBuffer
    if (pixelBuffer == null) {
        Logger.d(TAG, "CVImageBuffer is not a CVPixelBuffer")
        return null
    }

    // Lock the base address of the pixel buffer. This makes the pixel data accessible.
    // The CVPixelBufferLockBaseAddress documentation advises checking the return value.
    val lockResult = CVPixelBufferLockBaseAddress(pixelBuffer, 0u) // 0u for no flags
    if (lockResult != 0) {
        Logger.d(TAG, "Failed to lock pixel buffer base address: $lockResult")
        return null
    }

    // Use memScoped to manage memory allocated for CGImageRef and CGColorSpaceRef
    return memScoped {
        // Get pixel buffer properties
        val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
        val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
        val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
        val pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer)

        // Create a CGColorSpaceRef. For most image formats from video, DeviceRGB is suitable.
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        if (colorSpace == null) {
            Logger.d(TAG, "Failed to create CGColorSpace")
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
            return@memScoped null
        }

        // Create a CGBitmapContext. This is a graphics context that draws into a bitmap.
        // We don't strictly need a context to create a CGImage, but it's a common pattern
        // when working with pixel data and provides a way to render into the buffer.
        // However, for direct CGImage creation from a CVPixelBuffer, a data provider is more direct.
        // Let's use the data provider approach for clarity in this conversion context.

        // Create a CGDataProvider from the pixel buffer data.
        // We'll use NSData's dataWithBytesNoCopy to wrap the pixel data without copying.
        val data = NSData.dataWithBytesNoCopy(
            bytes = baseAddress,
            length = (bytesPerRow * height).toULong()
        )
        if (data == null) {
            Logger.d(TAG, "Failed to create NSData from pixel buffer")
            cgColorSpaceRelease(colorSpace)
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
            return@memScoped null
        }

        val dataProvider = data.createCGDataProvider()
        if (dataProvider == null) {
            Logger.d(TAG, "Failed to create CGDataProvider from NSData")
            cgColorSpaceRelease(colorSpace)
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
            return@memScoped null
        }

        // Determine the CGImageAlphaInfo based on the pixel format.
        // This is important for correct interpretation of the pixel data.
        // TODO: Correct format not found, might be dependent of the particular camera capture settings as well.
        val alphaInfo = when (pixelFormat) {
            // Format typically received from iPhone cameras (the working Alpha channel info is unknown upfront).
//            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaLast // RGB, no Alpha
//            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaFirst // RGB, no Alpha
//            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst // RGB, no Alpha
//            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaNoneSkipLast // RGB, no Alpha
//            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst // RGB, no Alpha
            kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange -> CGImageAlphaInfo.kCGImageAlphaPremultipliedLast // RGB, no Alpha

            // Other less common possibilities. Add more pixel format mappings as needed when changing camera settings.
            platform.CoreVideo.kCVPixelFormatType_32BGRA -> CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst // BGRA, Alpha Premultiplied
            platform.CoreVideo.kCVPixelFormatType_32ARGB -> CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst // ARGB, Alpha Premultiplied
            platform.CoreVideo.kCVPixelFormatType_32RGBA -> CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst // RGBA, Alpha Premultiplied
            platform.CoreVideo.kCVPixelFormatType_24RGB -> CGImageAlphaInfo.kCGImageAlphaNone // RGB, no Alpha
            else -> {
                Logger.d(TAG, "Unsupported pixel format: $pixelFormat")
                cgDataProviderRelease(dataProvider)
                cgColorSpaceRelease(colorSpace)
                CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
                return@memScoped null
            }
        }

        // Create a CGImage from the pixel data provider.
        val cgImage: CGImageRef? = CGImageCreate(
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u, // Assuming 8 bits per color component (common for video)
            bitsPerPixel = (bytesPerRow / width * 8).toULong(), // Calculate bits per pixel
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = kCGBitmapByteOrderDefault or alphaInfo.value, // Pass the alpha info value
            provider = dataProvider,
            decode = null, // Decode array, not needed for direct pixel data
            shouldInterpolate = true, // Should interpolate when drawing
            intent = CGColorRenderingIntent.kCGRenderingIntentDefault // Default rendering intent
        )

        // Release the color space and data provider as they are now retained by the CGImage
        cgColorSpaceRelease(colorSpace)
        cgDataProviderRelease(dataProvider)


        var uiImage: UIImage? = null
        if (cgImage != null) {
            // Create a UIImage from the CGImage.
            uiImage = UIImage(cGImage = cgImage)

            // Release the CGImage as it's now retained by the UIImage.
            CGImageRelease(cgImage)
        } else {
            Logger.d(TAG, "Failed to create CGImage")
        }

        // Unlock the base address of the pixel buffer now that we are done accessing the data.
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)

        return@memScoped uiImage
    }
}

// Helper function to create a CGDataProvider from NSData (requires bridging)
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.createCGDataProvider(): CGDataProviderRef? {
    // This bridges NSData to a format suitable for CGDataProvider.
    // In Swift/Objective-C, you might use CGDataProviderCreateWithCFData.
    // In Kotlin, we can use NSData's bytes and length to create the provider.
    // This is a slightly more manual approach than a direct bridge.

    val bytes = this.bytes
    val length = this.length

    // CGDataProviderCreateWithData takes data, data size, and release callback.
    // We provide null for the release callback as NSData manages the memory.
    return CGDataProviderCreateWithData(
        info = null,
        data = bytes,
        size = length,
        releaseData = null // No release callback needed as NSData owns the memory
    )
}

// Define platform-specific release functions (Core Foundation objects need explicit release)
@OptIn(ExperimentalForeignApi::class)
internal fun cgColorSpaceRelease(colorSpace: CGColorSpaceRef?) {
    CGColorSpaceRelease(colorSpace)
}

@OptIn(ExperimentalForeignApi::class)
internal fun cgDataProviderRelease(dataProvider: CGDataProviderRef?) {
    CGDataProviderRelease(dataProvider)
}

/**
 * Creates a Compose ImageBitmap from a CGImage without using Skia's Image class.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal fun createComposeImageBitmapFromCGImage(cgImage: CGImageRef): ImageBitmap {
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()

    val dataProvider = CGImageGetDataProvider(cgImage) ?: throw IllegalStateException("CGImage data provider is null")
    val data = CGDataProviderCopyData(dataProvider) ?: throw IllegalStateException("CGImage data is null")

    try {
        Logger.d(TAG, "Conv CGImageGetWidth: $width")

        val bitmapInfo: CGBitmapInfo = CGImageGetBitmapInfo(cgImage)
        val cgColorSpace = CGImageGetColorSpace(cgImage) ?: throw IllegalStateException("Color space null")
        val skColorSpace = convertCGColorSpaceToSkColorSpace(cgColorSpace) // Throwable

        // Check for unsupported bitmap info using bitwise AND
        if (bitmapInfo.and(kCGBitmapFloatComponents) == kCGBitmapFloatComponents) {
            throw IllegalStateException("Unsupported bitmap format: Float components")
        }

        val dataLength = CFDataGetLength(data).toInt()
        Logger.d(TAG, "Conv dataLength: $dataLength")
        // Skia Bitmap initialization
        val imageInfo = ImageInfo(
            width = width,
            height = height,
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.PREMUL,
            colorSpace = skColorSpace
        )

        val skiaBitmap = Bitmap().apply {
            allocPixels(imageInfo)
        }
        Logger.d(TAG, "Conv skiaBitmap: $skiaBitmap")

        val buffer = ByteArray(dataLength)
        data.let { src ->
            buffer.usePinned { dst ->
                memcpy(dst.addressOf(0).reinterpret<ByteVar>().pointed.ptr, src, dataLength.toULong())
            }
        }

        Logger.d(TAG, "Conv buffer: $buffer")

        skiaBitmap.installPixels(imageInfo, buffer, dataLength)

        Logger.d(TAG, "Conv skiaBitmapInitialized, return: $skiaBitmap")

        return skiaBitmap.asComposeImageBitmap()
    }
    catch (e: Exception) {
        Logger.d(TAG, "Conv Exception caught: $e")
        throw e
    } finally {
        Logger.d(TAG, "Conv release data")
        CFRelease(data)
    }
}

/** Leveraging the JetBrains Skia library. Very Slow. Icc profile conversion problem. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun convertCGColorSpaceToSkColorSpace(cgColorSpace: CGColorSpaceRef): ColorSpace {
    val iccProfile = CGColorSpaceCopyICCProfile(cgColorSpace)

    return if (iccProfile != null) {
        val dataLength = CFDataGetLength(iccProfile).toInt()
        val dataPtr = CFDataGetBytePtr(iccProfile)
            ?: throw IllegalStateException("Could not get ICC profile bytes")

        val colorSpaceData = ByteArray(dataLength)
        colorSpaceData.usePinned { pinnedColorSpace ->
            memcpy(
                pinnedColorSpace.addressOf(0),
                dataPtr.reinterpret<ByteVar>(),
                dataLength.toULong()
            )
        }

        CFRelease(iccProfile)
        // No color space implemented for this colorSpaceData.
        ColorSpace.sRGBLinear
    } else {
        // Check if the color space is device RGB.
        val colorSpaceModel = CGColorSpaceGetModel(cgColorSpace)

        if (colorSpaceModel == kCGColorSpaceModelRGB) {
            // Use a default sRGB color space.
            Logger.d(TAG, "No ICC color profile, using default deviceRGB")
            ColorSpace.sRGB
        } else {
            // If you want to handle other cases specifically, do it here.
            Logger.d(TAG, "No ICC color profile and not deviceRGB, using default sRGBLinear")
            ColorSpace.sRGBLinear
            // TODO: Needs actual (colorSpaceData); removed for now as there is no way to assign new colorSpace data.
        }
    }
}

/**
 * Conversion works, but color planes recoding appears wrong (b/w picture) or toSkiaImage() is  using wrong palette.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun cvpToImageBitmap(pixelBuffer: CVPixelBufferRef): ImageBitmap {

    CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
    val lumaBaseAddress = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0u)
    val chromaBaseAddress = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1u)

    val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
    val height = CVPixelBufferGetHeight(pixelBuffer).toInt()

    val lumaBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0u).toInt()
    val chromaBytesPerRow = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 1u).toInt()
    CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)

    val rgbaImage = ByteArray(4 * width * height) // TODO might exceed int size of the ByteArray for larger res. frame.

    memScoped {

        val lumaBuffer = allocArray<UInt8Var>(lumaBytesPerRow * height)
        memcpy(lumaBuffer, lumaBaseAddress, lumaBytesPerRow.toULong() * height.toULong())

        val chromaBuffer = allocArray<UInt8Var>(chromaBytesPerRow * height)
        memcpy(chromaBuffer, chromaBaseAddress, chromaBytesPerRow.toULong() * height.toULong())
        // TODO: Crashing here with EXC_BAD_ACCESS sometimes. Not catchable. CInterop internal feature.

        for (x in 0 until width) {
            for (y in 0 until height) {
                val lumaIndex = x + y * lumaBytesPerRow.toInt()
                val chromaIndex = (y / 2) * chromaBytesPerRow + (x / 2) * 2
                val yp = lumaBuffer[lumaIndex]
                val cb = chromaBuffer[chromaIndex]
                val cr = chromaBuffer[chromaIndex + 1]

                val ri = yp + (1.402 * (cr.toDouble() - 128)).toUInt()
                val gi = yp - (0.34414 * (cb.toDouble() - 128) - 0.71414 * (cr.toDouble() - 128)).toUInt()
                val bi = yp + (1.772 * (cb.toDouble() - 128)).toUInt()

                val r = min(max(ri, 0u), 255u).toByte()
                val g = min(max(gi, 0u), 255u).toByte()
                val b = min(max(bi, 0u), 255u).toByte()

                rgbaImage[(x + y * width) * 4] = b
                rgbaImage[(x + y * width) * 4 + 1] = g
                rgbaImage[(x + y * width) * 4 + 2] = r
                rgbaImage[(x + y * width) * 4 + 3] = 255.toByte()
            }
        }
    }

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val dataProvider: CGDataProviderRef = CGDataProviderCreateWithData(null, rgbaImage.refTo(0), (4 * width * height).toULong(), null)!!
    val vCGBitmapInfoByteOrder32LittleRawValue = 0b010000000000000u // Not exposed by cInterop.

    // Original Swift code:
    // let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.NoneSkipFirst.rawValue | CGBitmapInfo.ByteOrder32Little.rawValue)
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or vCGBitmapInfoByteOrder32LittleRawValue
    val cgImage: CGImageRef = CGImageCreate(width.toULong(), height.toULong(), 8u, 32u, (width * 4).toULong(),
        colorSpace!!, bitmapInfo, dataProvider, null, true, CGColorRenderingIntent.kCGRenderingIntentDefault)!!
    val image = UIImage(cgImage)

    return image.toImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toSkiaImage(): Image? {
    val imageRef = this.CGImage ?: return null

    val width = CGImageGetWidth(imageRef).toInt()
    val height = CGImageGetHeight(imageRef).toInt()

    val bytesPerRow = CGImageGetBytesPerRow(imageRef)
    val data = CGDataProviderCopyData(CGImageGetDataProvider(imageRef))
    val bytePointer = CFDataGetBytePtr(data)
    val length = CFDataGetLength(data)

    val alphaType = when (CGImageGetAlphaInfo(imageRef)) {
        CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst,
        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast -> ColorAlphaType.PREMUL
        CGImageAlphaInfo.kCGImageAlphaFirst,
        CGImageAlphaInfo.kCGImageAlphaLast -> ColorAlphaType.UNPREMUL
        CGImageAlphaInfo.kCGImageAlphaNone,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipLast -> ColorAlphaType.OPAQUE
        else -> ColorAlphaType.UNKNOWN
    }

    val byteArray = ByteArray(length.toInt()) { index ->
        bytePointer!![index].toByte()
    }

    CFRelease(data)
    CGImageRelease(imageRef)

    val skiaColorSpace = ColorSpace.sRGB
    val colorType = ColorType.RGBA_8888

    // Convert RGBA to BGRA
    for (i in byteArray.indices step 4) {
        val r = byteArray[i]
        val g = byteArray[i + 1]
        val b = byteArray[i + 2]
        val a = byteArray[i + 3]

        byteArray[i] = b
        byteArray[i + 2] = r
    }

    return Image.makeRaster(
        imageInfo = ImageInfo(
            width = width,
            height = height,
            colorType = colorType,
            alphaType = alphaType,
            colorSpace = skiaColorSpace
        ),
        bytes = byteArray,
        rowBytes = bytesPerRow.toInt(),
    )
}

/** Not used. Pixel color format converter. */
@OptIn(ExperimentalForeignApi::class)
private fun convertToBGRA(inputPixelBuffer: CVPixelBufferRef): CVPixelBufferRef? {
    return memScoped {
        val dictionary = CFDictionaryCreateMutable(null, 3, null, null)
        CFDictionaryAddValue(
            dictionary, kCVPixelBufferPixelFormatTypeKey,
            CFBridgingRetain(NSNumber(long = kCVPixelFormatType_32BGRA.toLong()))
        )
        CFDictionaryAddValue(dictionary, kCVPixelBufferCGBitmapContextCompatibilityKey, kCFBooleanTrue)
        CFDictionaryAddValue(dictionary, kCVPixelBufferCGImageCompatibilityKey, kCFBooleanTrue)

        val outputPixelBufferPtr = alloc<CVPixelBufferRefVar>()
        val status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            CVPixelBufferGetWidth(inputPixelBuffer),
            CVPixelBufferGetHeight(inputPixelBuffer),
            kCVPixelFormatType_32BGRA,
            dictionary,
            outputPixelBufferPtr.ptr
        )
        CFRelease(dictionary)

        if (status == kCVReturnSuccess) {
            val outputPixelBuffer = outputPixelBufferPtr.value
            // Lock the pixel buffers
            CVPixelBufferLockBaseAddress(inputPixelBuffer, 0u)
            CVPixelBufferLockBaseAddress(outputPixelBuffer, 0u)

            // Get the base addresses
            val inputBaseAddress = CVPixelBufferGetBaseAddress(inputPixelBuffer)?.toLong() ?: return null
            val outputBaseAddress = CVPixelBufferGetBaseAddress(outputPixelBuffer)?.toLong() ?: return null

            // Calculate the row bytes
            val inputBytesPerRow: Long = CVPixelBufferGetBytesPerRow(inputPixelBuffer).toLong()
            val outputBytesPerRow: Long = CVPixelBufferGetBytesPerRow(outputPixelBuffer).toLong()
            val height = CVPixelBufferGetHeight(inputPixelBuffer).toInt()

            // Copy data row by row
            for (row in 0 until height) {
                val rowLong = row.toLong()
                memcpy(
                    (outputBaseAddress + rowLong * outputBytesPerRow).toCPointer<ByteVar>(),
                    (inputBaseAddress + rowLong * inputBytesPerRow).toCPointer<ByteVar>(),
                    minOf(inputBytesPerRow, outputBytesPerRow).convert()
                )
            }

            // Unlock the pixel buffers
            CVPixelBufferUnlockBaseAddress(inputPixelBuffer, 0u)
            CVPixelBufferUnlockBaseAddress(outputPixelBuffer, 0u)
            Logger.d(TAG, "ConvertToBGRA: Conversion done")
            return outputPixelBuffer
        } else {
            Logger.d(TAG, "ConvertToBGRA: Error creating pixel buffer: $status")
            return null
        }
    }
}

private fun UIImage.toImageBitmap(): ImageBitmap {
    val skiaImage = this.toSkiaImage() ?: return ImageBitmap(1, 1)
    return skiaImage.toComposeImageBitmap()
}