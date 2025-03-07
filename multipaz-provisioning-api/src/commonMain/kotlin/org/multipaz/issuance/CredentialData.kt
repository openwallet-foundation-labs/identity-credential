package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcPublicKey
import kotlinx.datetime.Instant

/**
 * This data structure contains a data for a credential, minted by the issuer.
 */
@CborSerializable
data class CredentialData(
    /**
     * The secure-area bound key that the credential is for.
     */
    val secureAreaBoundKey: EcPublicKey?,

    /**
     * The credential is not valid until this point in time.
     */
    val validFrom: Instant,

    /**
     * The credential is not valid after this point in time.
     */
    val validUntil: Instant,

    /**
     * The format of the [data].
     */
    val format: CredentialFormat,

    /**
     * The data encoded in the format specified by [format].
     */
    val data: ByteArray,
)