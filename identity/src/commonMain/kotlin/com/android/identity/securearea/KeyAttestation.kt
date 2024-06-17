package com.android.identity.securearea

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcPublicKey

/**
 * Base class for key attestations.
 *
 * Key attestations are used to prove to a remote party that a key was generated in
 * a specific Secure Area. Each [SecureArea] subclass may subclass this class to
 * provide the attestation in a format specific to the Secure Area. If the Secure
 * Area lacks the ability to generate attestations this base class may be used.
 *
 * @param publicKey the key that the attestation is for.
 */
@CborSerializable
open class KeyAttestation(
    val publicKey: EcPublicKey
) {

    override fun equals(other: Any?): Boolean = other is KeyAttestation && publicKey == other.publicKey

    override fun hashCode(): Int = publicKey.hashCode()

    companion object {
    }
}