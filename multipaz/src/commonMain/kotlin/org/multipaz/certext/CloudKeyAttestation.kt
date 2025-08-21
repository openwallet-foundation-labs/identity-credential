package org.multipaz.certext

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.annotation.CborSerializationImplemented
import org.multipaz.cbor.buildCborMap
import org.multipaz.securearea.cloud.CloudUserAuthType

/**
 * A key attestation for a key that exists in [org.multipaz.securearea.cloud.CloudSecureArea]
 * which can be included in [MultipazExtension] in the certificate for the key being attested to.
 *
 * The CDDL is defined as:
 * ```
 * CloudKeyAttestation = {
 *   "challenge" : bstr,
 *   "passphrase": bool,
 *   "userAuthentication: CloudUserAuthentication
 * }
 *
 * ; The following values are defined for the kind of user authentication required.
 * ;
 * ;  0: No user authentication required for using the key
 * ;  1: Authentication is required for use of the key, only PIN/Passcode can be used.
 * ;  2: Authentication is required for use of the key, only biometrics can be used.
 * ;  3: Authentication is required for use of the key, either PIN/Passcode or biometrics can be used.
 * ;
 * CloudUserAuthentication = uint
 * ```
 *
 * This map may be extended in the future with additional fields.
 *
 * @property challenge the challenge, for freshness.
 * @property passphrase whether a passphrase is required to use the key.
 * @property userAuthentication the allowed ways to authenticate.
 */
@CborSerializationImplemented(schemaId = "jdsToUmJqDZ_sgJi0U5IDD1-PQlDUWfA1VEPyFYO3PE")
data class CloudKeyAttestation(
    val challenge: ByteString,
    val passphrase: Boolean,
    val userAuthentication: Set<CloudUserAuthType>
) {
    fun toDataItem() = buildCborMap {
        put("challenge", challenge.toByteArray())
        put("passphrase", passphrase)
        put("userAuthentication", CloudUserAuthType.Companion.encodeSet(userAuthentication))
    }

    companion object {
        fun fromDataItem(dataItem: DataItem) = CloudKeyAttestation(
            challenge = ByteString(dataItem["challenge"].asBstr),
            passphrase = dataItem["passphrase"].asBoolean,
            userAuthentication = CloudUserAuthType.Companion.decodeSet(dataItem["userAuthentication"].asNumber)
        )
    }
}
