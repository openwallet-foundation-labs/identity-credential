package org.multipaz.securearea.config

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.securearea.SecureArea

/**
 * Configuration for a specific secure area [SecureArea] to use.
 */
@CborSerializable
sealed class SecureAreaConfiguration(
    /** The value is a string encoded like [Algorithm.name] */
    val algorithm: String,
) {
    companion object
}