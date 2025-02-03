package com.android.identity.securearea

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509CertChain

/**
 * Class for key attestations.
 *
 * Key attestations are used to prove to a remote party that a key was generated in
 * a specific Secure Area.
 *
 * If the Secure Area implementation supports attestation with X.509 certificate chains,
 * the [certChain] parameter contains the chain. Not all Secure Area implementations
 * support attestation.
 *
 * @param publicKey the key that the attestation is for.
 * @param certChain If set, this contains a X.509 certificate chain attesting to the key.
 */
@CborSerializable
data class KeyAttestation(
    val publicKey: EcPublicKey,
    val certChain: X509CertChain?
) {
    companion object
}
