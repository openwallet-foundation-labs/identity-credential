package com.android.identity.securearea.config

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea

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