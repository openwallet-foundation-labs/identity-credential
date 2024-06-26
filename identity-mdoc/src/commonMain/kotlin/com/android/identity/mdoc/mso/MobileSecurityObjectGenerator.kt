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
package com.android.identity.mdoc.mso

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.crypto.EcPublicKey
import kotlinx.datetime.Instant

/**
 * Helper class for building `MobileSecurityObject` [CBOR](http://cbor.io/)
 * as specified ISO/IEC 18013-5:2021 section 9.1.2 Issuer data authentication
 *
 * @param digestAlgorithm The digest algorithm identifier. Must be one of {"SHA-256", "SHA-384", "SHA-512"}.
 * @param docType The document type.
 * @param deviceKey The public part of the key pair used for mdoc authentication.
 * @throws IllegalArgumentException if the `digestAlgorithm` is not one of
 * {"SHA-256", "SHA-384", "SHA-512"}.
 */
class MobileSecurityObjectGenerator(
    digestAlgorithm: String,
    docType: String,
    deviceKey: EcPublicKey
) {
    private val mDigestAlgorithm: String
    private val mDocType: String
    private val mDeviceKey: EcPublicKey
    private var mDigestSize = 0
    private val mValueDigestsOuter = CborMap.builder()
    private var digestEmpty = true
    private val mAuthorizedNameSpaces: MutableList<String> = ArrayList()
    private val mAuthorizedDataElements: MutableMap<String, List<String>> = HashMap()
    private val mKeyInfo: MutableMap<Long, ByteArray> = HashMap()
    private var mSigned: Instant? = null
    private var mValidFrom: Instant? = null
    private var mValidUntil: Instant? = null
    private var mExpectedUpdate: Instant? = null

    init {
        val allowableDigestAlgorithms = listOf("SHA-256", "SHA-384", "SHA-512")
        require(allowableDigestAlgorithms.contains(digestAlgorithm)) {
            "digestAlgorithm must be one of $allowableDigestAlgorithms"
        }
        mDigestAlgorithm = digestAlgorithm
        mDocType = docType
        mDeviceKey = deviceKey
        mDigestSize = when (digestAlgorithm) {
            "SHA-256" -> 32
            "SHA-384" -> 48
            "SHA-512" -> 64
            else -> -1
        }
    }

    /**
     * Populates the `ValueDigests` mapping. This must be called at least once before
     * generating since `ValueDigests` must be non-empty.
     *
     * @param nameSpace The namespace.
     * @param digestIDs A non-empty mapping between a `DigestID` and a `Digest`.
     * @return The `MobileSecurityObjectGenerator`.
     * @exception IllegalArgumentException if the `digestIDs` is empty.
     */
    fun addDigestIdsForNamespace(
        nameSpace: String,
        digestIDs: Map<Long, ByteArray>
    ) = apply {
        require(!digestIDs.isEmpty()) { "digestIDs must not be empty" }
        digestEmpty = false
        val valueDigestsInner = mValueDigestsOuter.putMap(nameSpace)
        for ((digestID,digest) in digestIDs) {
            require(digest.size == mDigestSize) {
                "digest is unexpected length: expected $mDigestSize, got ${digest.size}"
            }
            valueDigestsInner.put(digestID, digest)
        }
        valueDigestsInner.end()
    }

    /**
     * Populates the `AuthorizedNameSpaces` portion of the `keyAuthorizations`
     * within `DeviceKeyInfo`. This gives authorizations to full namespaces
     * included in the `authorizedNameSpaces` array. If authorization is given for a full
     * namespace, that namespace shall not be included in
     * [.setDeviceKeyAuthorizedDataElements].
     *
     * @param authorizedNameSpaces A list of namespaces which should be given authorization.
     * @return The `MobileSecurityObjectGenerator`.
     * @throws IllegalArgumentException if the authorizedNameSpaces does not meet the constraints.
     */
    fun setDeviceKeyAuthorizedNameSpaces(
        authorizedNameSpaces: List<String>
    ) = apply {

        val namespaceSet = mutableSetOf<String>().apply {
            addAll(mAuthorizedDataElements.keys)
            retainAll(authorizedNameSpaces)
        }

        // 18013-5 Section 9.1.2.4 says "If authorization is given for a full namespace (by including
        // the namespace in the AuthorizedNameSpaces array), that namespace shall not be included in
        // the AuthorizedDataElements map.
        require(namespaceSet.isEmpty()) {
            "authorizedNameSpaces includes a namespace already " +
                    "present in the mapping of authorized data elements provided."
        }
        mAuthorizedNameSpaces.clear()
        mAuthorizedNameSpaces.addAll(authorizedNameSpaces)
    }

    /**
     * Populates the `AuthorizedDataElements` portion of the `keyAuthorizations`
     * within `DeviceKeyInfo`. This gives authorizations to data elements
     * included in the `authorizedDataElements` mapping. If a namespace is included here,
     * then it should not be included in [.setDeviceKeyAuthorizedNameSpaces]
     *
     * @param authorizedDataElements A mapping from namespaces to a list of
     * `DataElementIdentifier`
     * @return The `MobileSecurityObjectGenerator`.
     * @throws IllegalArgumentException if authorizedDataElements does not meet the constraints.
     */
    fun setDeviceKeyAuthorizedDataElements(
        authorizedDataElements: Map<String, List<String>>
    ) = apply {
        val namespaceSet = mutableSetOf<String>().apply {
            addAll(authorizedDataElements.keys)
            retainAll(mAuthorizedNameSpaces)
        }


        // 18013-5 Section 9.1.2.4 says "If authorization is given for a full namespace (by including
        // the namespace in the AuthorizedNameSpaces array), that namespace shall not be included in
        // the AuthorizedDataElements map.
        require(namespaceSet.isEmpty()) {
            "authorizedDataElements includes a namespace already " +
                    "present in the list of authorized name spaces provided."
        }
        mAuthorizedDataElements.clear()
        mAuthorizedDataElements.putAll(authorizedDataElements)
    }

    /**
     * Provides extra info for the mdoc authentication public key as part of the
     * `KeyInfo` portion of the `DeviceKeyInfo`.
     *
     * @param keyInfo A mapping to represent additional key information.
     * @return The `MobileSecurityObjectGenerator`.
     */
    fun setDeviceKeyInfo(keyInfo: Map<Long, ByteArray>) = apply {
        mKeyInfo.clear()
        mKeyInfo.putAll(keyInfo)
    }

    /**
     * Sets the `ValidityInfo` structure which contains information related to the
     * validity of the MSO and its signature. This must be called before generating since this a
     * required component of the `MobileSecurityObject`.
     *
     * @param signed         The timestamp at which the MSO signature was created.
     * @param validFrom      The timestamp before which the MSO is not yet valid. This shall be
     * equal or later than the signed element.
     * @param validUntil     The timestamp after which the MSO is no longer valid. This shall be
     * later than the validFrom element.
     * @param expectedUpdate Optional: if provided, represents the timestamp at which the issuing
     * authority infrastructure expects to re-sign the MSO, else, null
     * @return The `MobileSecurityObjectGenerator`.
     * @throws IllegalArgumentException if the times are do not meet the constraints.
     */
    fun setValidityInfo(
        signed: Instant,
        validFrom: Instant, validUntil: Instant,
        expectedUpdate: Instant?
    ) = apply {
        // 18013-5 Section 9.1.2.4 says "The timestamp of validFrom shall be equal or later than
        // the signed element."
        require(validFrom >= signed) {
            "The validFrom timestamp should be equal or later than the signed timestamp"
        }

        // 18013-5 Section 9.1.2.4 says "The validUntil element contains the timestamp after which the
        // MSO is no longer valid. The value of the timestamp shall be later than the validFrom element."
        require(validUntil > validFrom) {
            "The validUntil timestamp should be later than the validFrom timestamp"
        }
        mSigned = signed
        mValidFrom = validFrom
        mValidUntil = validUntil
        mExpectedUpdate = expectedUpdate
    }

    private fun generateDeviceKeyBuilder(): CborBuilder {
        val deviceKeyMapBuilder = CborMap.builder()
        deviceKeyMapBuilder.put("deviceKey", mDeviceKey.toCoseKey(mapOf()).toDataItem())
        if (mAuthorizedNameSpaces.isNotEmpty() or !mAuthorizedDataElements.isEmpty()) {
            val keyAuthMapBuilder = deviceKeyMapBuilder.putMap("keyAuthorizations")
            if (mAuthorizedNameSpaces.isNotEmpty()) {
                val authNameSpacesArrayBuilder = keyAuthMapBuilder.putArray("nameSpaces")
                for (namespace in mAuthorizedNameSpaces) {
                    authNameSpacesArrayBuilder.add(namespace)
                }
                authNameSpacesArrayBuilder.end()
            }
            if (mAuthorizedDataElements.isNotEmpty()) {
                val authDataElemOuter = keyAuthMapBuilder.putMap("dataElements")
                for (namespace in mAuthorizedDataElements.keys) {
                    val authDataElemInner = authDataElemOuter.putArray(namespace)
                    for (dataElemIdentifier in mAuthorizedDataElements[namespace]!!) {
                        authDataElemInner.add(dataElemIdentifier)
                    }
                    authDataElemInner.end()
                }
                authDataElemOuter.end()
            }
            keyAuthMapBuilder.end()
        }
        if (mKeyInfo.isNotEmpty()) {
            val keyInfoMapBuilder = deviceKeyMapBuilder.putMap("keyInfo")
            for ((label, bytes) in mKeyInfo) {
                keyInfoMapBuilder.put(label, bytes)
            }
            keyInfoMapBuilder.end()
        }
        return deviceKeyMapBuilder.end()
    }

    private fun generateValidityInfoBuilder(): CborBuilder =
        CborMap.builder().run {
            put("signed", mSigned!!.toEpochMilliseconds().toDataItemDateTimeString())
            put("validFrom", mValidFrom!!.toEpochMilliseconds().toDataItemDateTimeString())
            put("validUntil", mValidUntil!!.toEpochMilliseconds().toDataItemDateTimeString())
            if (mExpectedUpdate != null)
                put("expectedUpdate", mExpectedUpdate!!.toEpochMilliseconds().toDataItemDateTimeString())
            end()
        }

    /**
     * Builds the `MobileSecurityObject` CBOR.
     *
     *
     * It's mandatory to call [.addDigestIdsForNamespace] and
     * [.setValidityInfo] before this call.
     *
     * @return the bytes of `MobileSecurityObject` CBOR.
     * @throws IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    fun generate(): ByteArray =
        CborMap.builder().run {
            check(!digestEmpty) { "Must call addDigestIdsForNamespace before generating" }
            checkNotNull(mSigned) { "Must call setValidityInfo before generating" }

            put("version", "1.0")
            put("digestAlgorithm", mDigestAlgorithm)
            put("docType", mDocType)
            put("valueDigests", mValueDigestsOuter.end().build())
            put("deviceKeyInfo", generateDeviceKeyBuilder().build())
            put("validityInfo", generateValidityInfoBuilder().build())
            end()
            Cbor.encode(end().build())
        }
}
