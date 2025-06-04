package org.multipaz.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableData
import platform.Foundation.NSDataCompressionAlgorithmZlib
import platform.Foundation.compressUsingAlgorithm
import platform.Foundation.decompressedDataUsingAlgorithm

@OptIn(ExperimentalForeignApi::class)
actual fun deflate(data: ByteArray, compressionLevel: Int): ByteArray {
    require(compressionLevel >=0 && compressionLevel <= 9) {
        "Compression level $compressionLevel is invalid, must be between 0 and 9"
    }
    // Note: This API doesn't allow setting the compressionLevel, it's always hardcoded to 5
    val data = data.toNSData().mutableCopy() as NSMutableData
    data.compressUsingAlgorithm(
        algorithm = NSDataCompressionAlgorithmZlib,
        error = null
    )
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
actual fun inflate(compressedData: ByteArray): ByteArray {
    val d = compressedData.toNSData()
    val ret = d.decompressedDataUsingAlgorithm(
        algorithm = NSDataCompressionAlgorithmZlib,
        error = null
    )
    if (ret == null) {
        throw IllegalArgumentException("Error decompressing data")
    }
    return ret.toByteArray()
}
