package org.multipaz.util

/**
 * Compresses data using DEFLATE algorithm according to [RFC 1951](https://www.ietf.org/rfc/rfc1951.txt).
 *
 * @param data the data to compress.
 * @param compressionLevel must be between 0 and 9, both inclusive.
 * @return the compressed data.
 * @throws IllegalArgumentException if [compressionLevel] isn't valid.
 */
expect fun deflate(data: ByteArray, compressionLevel: Int = 5): ByteArray

/**
 * Decompresses data compressed DEFLATE algorithm according to [RFC 1951](https://www.ietf.org/rfc/rfc1951.txt).
 *
 * @param compressedData the compressed data to decompress.
 * @return the decompressed data.
 * @throws IllegalArgumentException if the given data is invalid
 */
expect fun inflate(compressedData: ByteArray): ByteArray
