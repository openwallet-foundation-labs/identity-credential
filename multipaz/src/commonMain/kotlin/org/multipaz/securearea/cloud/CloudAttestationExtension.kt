/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.multipaz.securearea.cloud

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteString

/**
 * X.509 Extension used by [CloudSecureArea] to convey attestations for keys.
 *
 * The extension must be put in X.509 certificate for the created key (that is,
 * included in the first certificate in the attestation for the key) at the OID
 * defined by [OID.X509_EXTENSION_MULTIPAZ_CSA_KEY_ATTESTATION] and the payload
 * should be an OCTET STRING containing the bytes of the CBOR conforming to the
 * following CDDL:
 *
 * ```
 * CloudAttestationExtension = {
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
data class CloudAttestationExtension(
    val challenge: ByteString,
    val passphrase: Boolean,
    val userAuthentication: Set<CloudUserAuthType>
) {

    /**
     * Generates the payload of the attestation extension.
     *
     * @return the bytes of the CBOR for the extension.
     */
    fun encode() = ByteString(
        Cbor.encode(
            CborMap.builder()
                .put("challenge", challenge.toByteArray())
                .put("passphrase", passphrase)
                .put("userAuthentication", CloudUserAuthType.encodeSet(userAuthentication))
                .end()
                .build()
        )
    )

    private fun renderByteArray(data: ByteArray): String {
        return if (data.isEmpty()) {
            "<empty>"
        } else {
            "${data.toHex(byteDivider = " ")} (\"${data.decodeToString()}\")"
        }
    }

    /**
     * Pretty-prints the contents of the attestation extension.
     */
    fun prettyPrint(): String {
        val sb = StringBuilder()
        val userAuthRequired = if (userAuthentication.isEmpty()) {
            "None"
        } else {
            userAuthentication.joinToString(" or ")
        }
        sb.append(
            """
                Challenge: ${renderByteArray(challenge.toByteArray())}
                Passphrase Required: $passphrase
                User Authentication Required: $userAuthRequired
            """.trimIndent()
        )
        return sb.toString()
    }

    companion object {
        /**
         * Extracts the challenge from the attestation extension.
         *
         * @param attestationExtensionPayload the bytes of the CBOR for the extension.
         * @return a [CloudAttestationExtension].
         */
        fun decode(attestationExtensionPayload: ByteString): CloudAttestationExtension {
            val map = Cbor.decode(attestationExtensionPayload.toByteArray())
            return CloudAttestationExtension(
                challenge = ByteString(map["challenge"].asBstr),
                passphrase = map["passphrase"].asBoolean,
                userAuthentication = CloudUserAuthType.decodeSet(map["userAuthentication"].asNumber)
            )
        }
    }
}