package org.multipaz.graphhash

import kotlinx.io.bytestring.ByteString

/**
 * Hashing interface.
 *
 * Computes hash of the input data.
 */
interface HashBuilder {
    fun update(data: ByteString)
    fun build(): ByteString
}