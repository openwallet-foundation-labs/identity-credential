package org.multipaz.compose.camera.kmpbitmap

/** KMP image format processing. */
class KmpBitmap {
    /** Suggested internal representation is PNG-encoded image pixels data, ready for perm. storage. */
    var imageData: ByteArray = ByteArray(0)

    /** Encode native image to PNG image pixels data (e.g. for saving in the DB). */
    fun initialize(image: PlatformImage) {
        imageData = platformInitialize(image)
    }

    /**
     * Common native image scaling method.
     * Not used a.t.m., as implemented internally in Android only (for performance).
     */
    fun scaleTo(width: Int, height: Int) {
        imageData = platformScale(imageData, width, height)
    }

    /** Decode PNG-encoded image pixels data to native image format (e.g. for displaying). */
    fun decode(): PlatformImage {
        return platformDecode(imageData)
    }
}

/** Platform-specific image implementation. */
expect class PlatformImage

// These functions are expected to be implemented on each platform for native-format image processing in this class.
expect fun platformInitialize(image: PlatformImage): ByteArray

expect fun platformScale(byteArray: ByteArray, width: Int, height: Int): ByteArray

expect fun platformDecode(byteArray: ByteArray): PlatformImage

