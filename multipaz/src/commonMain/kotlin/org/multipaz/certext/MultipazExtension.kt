package org.multipaz.certext

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.util.toHex

/**
 * Certificate extension for Multipaz.
 *
 * This certificate extension may appear in several kinds of certificates. Its payload
 * is a CBOR map with the following well-known keys
 *
 * ```
 * MultipazExtension = {
 *   ? "cloudKeyAttestation" : CloudKeyAttestation,
 *   ? "googleAccount" : GoogleAccount
 * }
 * ```
 *
 * This map may be extended in the future with additional fields.
 *
 * If this extension is included in a X.509 certificate it shall use the OID defined by
 * [org.multipaz.asn1.OID.X509_EXTENSION_MULTIPAZ_EXTENSION] and the payload must be
 * an OCTET STRING containing the bytes of the CBOR conforming to the CDDL defined above.
 *
 * If used for key attestation for [org.multipaz.securearea.cloud.CloudSecureArea]
 * the `cloudKeyAttestation` field must be set and the extension must appear on the
 * X.509 certificate for the created key. See [CloudKeyAttestation] for more details.
 *
 * If used in a reader certificate for reader authentication the `googleAccount` field
 * may be set. See [GoogleAccount] for more details.
 *
 * @property cloudKeyAttestation a [CloudKeyAttestation] or `null`.
 * @property googleAccount a [GoogleAccount] or `null`.
 */
@CborSerializable
data class MultipazExtension(
    val cloudKeyAttestation: CloudKeyAttestation? = null,
    val googleAccount: GoogleAccount? = null
) {
    private fun renderByteArray(data: ByteArray): String {
        return if (data.isEmpty()) {
            "<empty>"
        } else {
            "${data.toHex(byteDivider = " ")} (\"${data.decodeToString()}\")"
        }
    }

    /**
     * Pretty-prints the contents of the extension.
     *
     * @return a string with the pretty-printed representation of the extension.
     */
    fun prettyPrint(): String {
        val sb = StringBuilder()
        cloudKeyAttestation?.let {
            val userAuthRequired = if (it.userAuthentication.isEmpty()) {
                "None"
            } else {
                it.userAuthentication.joinToString(" or ")
            }
            sb.append(
                """
                    Cloud Key Attestation:
                        Challenge: ${renderByteArray(it.challenge.toByteArray())}
                        Passphrase Required: ${it.passphrase}
                        User Authentication Required: $userAuthRequired
                """.trimIndent()
            )
        }
        googleAccount?.let {
            sb.append(
                """
                    Google Account:
                        id: ${it.id}
                        emailAddress: ${it.emailAddress}
                        displayName: ${it.displayName}
                        profilePictureUri: ${it.profilePictureUri}
                """.trimIndent()
            )
        }
        return sb.toString()
    }

    companion object {
    }
}