package org.multipaz.securearea.config

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcCurve
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.SecureArea

/**
 * Configuration for a specific secure area [SecureArea] to use.
 */
@CborSerializable
sealed class SecureAreaConfiguration(
    /** The value is a number encoded like in [KeyPurpose.encodeSet] */
    val purposes: Long,
    /** The value is a number encoded like [EcCurve.coseCurveIdentifier] */
    val curve: Int
) {
    companion object
}