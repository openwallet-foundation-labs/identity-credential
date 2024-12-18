package com.android.identity.device

import com.android.identity.cbor.annotation.CborSerializable

/**
 * A platform-issued statement vouching for the integrity of the wallet app.
 *
 * A device attestation can be validated on server (which is **not** running on the platform
 * that produced [DeviceAttestation]).
 */
@CborSerializable
sealed class DeviceAttestation {
    companion object
}