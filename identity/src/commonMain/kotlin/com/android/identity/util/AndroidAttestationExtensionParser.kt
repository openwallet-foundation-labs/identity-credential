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
package com.android.identity.util

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.ASN1Boolean
import com.android.identity.asn1.ASN1Integer
import com.android.identity.asn1.ASN1Object
import com.android.identity.asn1.ASN1OctetString
import com.android.identity.asn1.ASN1Sequence
import com.android.identity.asn1.ASN1Set
import com.android.identity.asn1.ASN1TaggedObject
import com.android.identity.crypto.X509Cert
import kotlinx.io.bytestring.ByteString

// This code is based on https://github.com/google/android-key-attestation
class AndroidAttestationExtensionParser(cert: X509Cert) {
    enum class SecurityLevel {
        SOFTWARE,
        TRUSTED_ENVIRONMENT,
        STRONG_BOX
    }

    enum class VerifiedBootState {
        UNKNOWN,
        GREEN,
        YELLOW,
        ORANGE,
        RED
    }

    val attestationSecurityLevel: SecurityLevel
    val attestationVersion: Int

    val keymasterSecurityLevel: SecurityLevel
    val keymasterVersion: Int

    val attestationChallenge: ByteArray
    val uniqueId: ByteArray

    val verifiedBootState: VerifiedBootState

    val applicationSignatureDigests: List<ByteString>

    private val softwareEnforcedAuthorizations: Map<Int, ASN1Object>
    private val teeEnforcedAuthorizations: Map<Int, ASN1Object>

    init {
        val attestationExtensionBytes = cert.getExtensionValue(KEY_DESCRIPTION_OID)
        require(attestationExtensionBytes != null && attestationExtensionBytes.isNotEmpty()) {
            "Couldn't find keystore attestation extension."
        }
        val extAsn1 = ASN1.decode(attestationExtensionBytes)
            ?: throw IllegalArgumentException("ASN.1 parsing failed")
        val seq = extAsn1 as ASN1Sequence

        attestationVersion = getIntegerFromAsn1(seq.elements[ATTESTATION_VERSION_INDEX])
        this.attestationSecurityLevel = securityLevelToEnum(
            getIntegerFromAsn1(seq.elements[ATTESTATION_SECURITY_LEVEL_INDEX])
        )

        keymasterVersion = getIntegerFromAsn1(seq.elements[KEYMASTER_VERSION_INDEX])
        this.keymasterSecurityLevel = securityLevelToEnum(
            getIntegerFromAsn1(seq.elements[KEYMASTER_SECURITY_LEVEL_INDEX])
        )
        attestationChallenge =
            (seq.elements[ATTESTATION_CHALLENGE_INDEX] as ASN1OctetString).value
        uniqueId = (seq.elements[UNIQUE_ID_INDEX] as ASN1OctetString).value

        softwareEnforcedAuthorizations = getAuthorizationMap(
            (seq.elements[SW_ENFORCED_INDEX] as ASN1Sequence).elements
        )
        teeEnforcedAuthorizations = getAuthorizationMap(
            (seq.elements[TEE_ENFORCED_INDEX] as ASN1Sequence).elements
        )

        val rootOfTrustSeq = findAuthorizationListEntry(teeEnforcedAuthorizations, 704) as ASN1Sequence?
        verifiedBootState =
            if (rootOfTrustSeq != null) {
                when (getIntegerFromAsn1(rootOfTrustSeq.elements[2])) {
                    0 -> VerifiedBootState.GREEN
                    1 -> VerifiedBootState.YELLOW
                    2 -> VerifiedBootState.ORANGE
                    3 -> VerifiedBootState.RED
                    else -> VerifiedBootState.UNKNOWN
                }
            } else {
                VerifiedBootState.UNKNOWN
            }

        val encodedAttestationApplicationId = getSoftwareAuthorizationByteString(709)
            ?: throw IllegalArgumentException("No software authorization")
        val attestationApplicationIdSeq = (ASN1.decode(encodedAttestationApplicationId)
            ?: throw IllegalArgumentException("ASN.1 parsing failed")) as ASN1Sequence

        val signatureDigestSet = attestationApplicationIdSeq.elements[1] as ASN1Set
        val digests = mutableListOf<ByteString>()
        for (element in signatureDigestSet.elements) {
            val octetString = element as ASN1OctetString
            digests.add(ByteString(octetString.value))
        }
        applicationSignatureDigests = digests
    }

    val softwareEnforcedAuthorizationTags: Set<Int>
        get() = softwareEnforcedAuthorizations.keys
    val teeEnforcedAuthorizationTags: Set<Int>
        get() = teeEnforcedAuthorizations.keys

    fun getSoftwareAuthorizationInteger(tag: Int): Int? {
        val entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag)
        return entry?.let { getIntegerFromAsn1(it) }
    }

    fun getSoftwareAuthorizationLong(tag: Int): Long? {
        val entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag)
        return entry?.let { getLongFromAsn1(it) }
    }

    fun getTeeAuthorizationInteger(tag: Int): Int? {
        val entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag)
        return entry?.let { getIntegerFromAsn1(it) }
    }

    fun getTeeAuthorizationLong(tag: Int): Long? {
        val entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag)
        return entry?.let { getLongFromAsn1(it) }
    }

    fun getSoftwareAuthorizationBoolean(tag: Int): Boolean {
        val entry = findAuthorizationListEntry(softwareEnforcedAuthorizations, tag)
        return entry != null
    }

    fun getTeeAuthorizationBoolean(tag: Int): Boolean {
        val entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag)
        return entry != null
    }

    fun getSoftwareAuthorizationByteString(tag: Int): ByteArray? {
        val entry =
            findAuthorizationListEntry(softwareEnforcedAuthorizations, tag) as ASN1OctetString?
        return entry?.value
    }

    fun getTeeAuthorizationByteString(tag: Int): ByteArray? {
        val entry = findAuthorizationListEntry(teeEnforcedAuthorizations, tag) as ASN1OctetString?
        return entry?.value
    }

    companion object {
        private const val KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val ATTESTATION_VERSION_INDEX = 0
        private const val ATTESTATION_SECURITY_LEVEL_INDEX = 1
        private const val KEYMASTER_VERSION_INDEX = 2
        private const val KEYMASTER_SECURITY_LEVEL_INDEX = 3
        private const val ATTESTATION_CHALLENGE_INDEX = 4
        private const val UNIQUE_ID_INDEX = 5
        private const val SW_ENFORCED_INDEX = 6
        private const val TEE_ENFORCED_INDEX = 7

        // Some security values. The complete list is in this AOSP file:
        // hardware/libhardware/include/hardware/keymaster_defs.h
        private const val KM_SECURITY_LEVEL_SOFTWARE = 0
        private const val KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1
        private const val KM_SECURITY_LEVEL_STRONG_BOX = 2

        private fun findAuthorizationListEntry(
            authorizationMap: Map<Int, ASN1Object>, tag: Int
        ): ASN1Object? {
            return authorizationMap[tag]
        }

        private fun getBooleanFromAsn1(asn1Value: ASN1Object): Boolean {
            return if (asn1Value is ASN1Boolean) {
                asn1Value.value
            } else {
                throw RuntimeException(
                    "Boolean value expected; found " + asn1Value::class.simpleName + " instead."
                )
            }
        }

        private fun getIntegerFromAsn1(asn1Value: ASN1Object): Int {
            return if (asn1Value is ASN1Integer) {
                val longValue = asn1Value.toLong()
                if (Int.MIN_VALUE <= longValue && longValue <= Int.MAX_VALUE) {
                    longValue.toInt()
                } else {
                    throw IllegalArgumentException("Int value out of range: $longValue")
                }
            } else {
                throw IllegalArgumentException(
                    "Integer value expected; found " + asn1Value::class.simpleName + " instead."
                )
            }
        }

        private fun getLongFromAsn1(asn1Value: ASN1Object): Long {
            return if (asn1Value is ASN1Integer) {
                asn1Value.toLong()
            } else {
                throw IllegalArgumentException(
                    "Integer value expected; found " + asn1Value::class.simpleName + " instead."
                )
            }
        }

        private fun getAuthorizationMap(
            authorizationList: List<ASN1Object>
        ): Map<Int, ASN1Object> {
            val authorizationMap: MutableMap<Int, ASN1Object> = HashMap()
            for (entry in authorizationList) {
                val taggedEntry = entry as ASN1TaggedObject
                authorizationMap[taggedEntry.tag] = ASN1.decode(taggedEntry.content)
                    ?: throw IllegalArgumentException("ASN.1 parsing error")
            }
            return authorizationMap
        }

        private fun securityLevelToEnum(securityLevel: Int): SecurityLevel {
            return when (securityLevel) {
                KM_SECURITY_LEVEL_SOFTWARE -> SecurityLevel.SOFTWARE
                KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TRUSTED_ENVIRONMENT
                KM_SECURITY_LEVEL_STRONG_BOX -> SecurityLevel.STRONG_BOX
                else -> throw IllegalArgumentException("Invalid security level.")
            }
        }
    }
}
