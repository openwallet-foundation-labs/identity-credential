package org.multipaz.appsupport.ui.presentment

import org.multipaz.credential.Credential

/**
 * A data structure representing a credential which can be presented.
 *
 * This structure may hold up to two credentials, one which can be presented with either
 * with signing (or doesn't have a key) and one which can be presented using key agreement.
 * The latter requires obtaining a suitable public key from the recipient, of the same curve,
 * then perform Key Agreement, and then use a KDF to derive a key and use it to e.g. compute
 * a MAC.
 *
 * This functionality is needed for the case where an application wants to use _mdoc MAC Authentication_
 * according to ISO/IEC 18013-5:2021 in the case where the mdoc reader can accept this (e.g.
 * EReaderKey matches DeviceKey for proximity presentations) and _mdoc ECDSA/EdDSA Authentication_
 * for the other cases.
 *
 * @property credential a credential which doesn't require the use of a Key Agreement algorithm.
 * @property credentialKeyAgreement a credential which requires the use of a Key Agreement algorithm.
 */
data class CredentialForPresentment(
    val credential: Credential?,
    val credentialKeyAgreement: Credential?
)
