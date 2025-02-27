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

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.mdoc.TestVectors
import com.android.identity.util.fromHex
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MobileSecurityObjectGeneratorTest {
    companion object {
        private const val HALF_SEC_IN_NANOSECONDS = 1000000000/2
    }

    private fun getDigestAlg(digestAlgorithm: String): Algorithm {
        return when (digestAlgorithm) {
            "SHA-256" -> Algorithm.SHA256
            "SHA-384" -> Algorithm.SHA384
            "SHA-512" -> Algorithm.SHA512
            else -> throw AssertionError()
        }
    }
    
    private fun generateISODigest(digestAlgorithm: String): Map<Long, ByteString> {
        val alg = getDigestAlg(digestAlgorithm)
        val isoDigestIDs = mutableMapOf<Long, ByteString>()
        isoDigestIDs[0L] = ByteString(Crypto.digest(alg, "aardvark".encodeToByteArray()))
        isoDigestIDs[1L] = ByteString(Crypto.digest(alg, "alligator".encodeToByteArray()))
        isoDigestIDs[2L] = ByteString(Crypto.digest(alg, "baboon".encodeToByteArray()))
        isoDigestIDs[3L] = ByteString(Crypto.digest(alg, "butterfly".encodeToByteArray()))
        isoDigestIDs[4L] = ByteString(Crypto.digest(alg, "cat".encodeToByteArray()))
        isoDigestIDs[5L] = ByteString(Crypto.digest(alg, "cricket".encodeToByteArray()))
        isoDigestIDs[6L] = ByteString(Crypto.digest(alg, "dog".encodeToByteArray()))
        isoDigestIDs[7L] = ByteString(Crypto.digest(alg, "elephant".encodeToByteArray()))
        isoDigestIDs[8L] = ByteString(Crypto.digest(alg, "firefly".encodeToByteArray()))
        isoDigestIDs[9L] = ByteString(Crypto.digest(alg, "frog".encodeToByteArray()))
        isoDigestIDs[10L] = ByteString(Crypto.digest(alg, "gecko".encodeToByteArray()))
        isoDigestIDs[11L] = ByteString(Crypto.digest(alg, "hippo".encodeToByteArray()))
        isoDigestIDs[12L] = ByteString(Crypto.digest(alg, "iguana".encodeToByteArray()))
        return isoDigestIDs
    }

    private fun generateISOUSDigest(digestAlgorithm: String): Map<Long, ByteString> {
        val alg = getDigestAlg(digestAlgorithm)
        val isoUSDigestIDs: MutableMap<Long, ByteString> = HashMap()
        isoUSDigestIDs[0L] = ByteString(Crypto.digest(alg, "jaguar".encodeToByteArray()))
        isoUSDigestIDs[1L] = ByteString(Crypto.digest(alg, "jellyfish".encodeToByteArray()))
        isoUSDigestIDs[2L] = ByteString(Crypto.digest(alg, "koala".encodeToByteArray()))
        isoUSDigestIDs[3L] = ByteString(Crypto.digest(alg, "lemur".encodeToByteArray()))
        return isoUSDigestIDs
    }

    private fun checkISODigest(isoDigestIDs: Map<Long, ByteString>?, digestAlgorithm: String) {
        val alg = getDigestAlg(digestAlgorithm)
        assertEquals(
            setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
            isoDigestIDs!!.keys
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "aardvark".encodeToByteArray())),
            isoDigestIDs[0L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "alligator".encodeToByteArray())),
            isoDigestIDs[1L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "baboon".encodeToByteArray())),
            isoDigestIDs[2L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "butterfly".encodeToByteArray())),
            isoDigestIDs[3L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "cat".encodeToByteArray())),
            isoDigestIDs[4L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "cricket".encodeToByteArray())),
            isoDigestIDs[5L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "dog".encodeToByteArray())),
            isoDigestIDs[6L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "elephant".encodeToByteArray())),
            isoDigestIDs[7L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "firefly".encodeToByteArray())),
            isoDigestIDs[8L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "frog".encodeToByteArray())),
            isoDigestIDs[9L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "gecko".encodeToByteArray())),
            isoDigestIDs[10L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "hippo".encodeToByteArray())),
            isoDigestIDs[11L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "iguana".encodeToByteArray())),
            isoDigestIDs[12L]
        )
    }

    private fun checkISOUSDigest(isoUSDigestIDs: Map<Long, ByteString>?, digestAlgorithm: String) {
        val alg = getDigestAlg(digestAlgorithm)
        assertEquals(setOf(0L, 1L, 2L, 3L), isoUSDigestIDs!!.keys)
        assertEquals(
            ByteString(Crypto.digest(alg, "jaguar".encodeToByteArray())),
            isoUSDigestIDs[0L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "jellyfish".encodeToByteArray())),
            isoUSDigestIDs[1L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "koala".encodeToByteArray())),
            isoUSDigestIDs[2L]
        )
        assertEquals(
            ByteString(Crypto.digest(alg, "lemur".encodeToByteArray())),
            isoUSDigestIDs[3L]
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun testFullMSO(digestAlgorithm: String) {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex()),
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex())
        )
        val signedTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validFromTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validUntilTimestamp = Instant.fromEpochMilliseconds(1633095002000L)
        val expectedTimestamp = Instant.fromEpochMilliseconds(1611093002000L)
        val deviceKeyAuthorizedDataElements: MutableMap<String, List<String>> = HashMap()
        deviceKeyAuthorizedDataElements["a"] = listOf("1", "2", "f")
        deviceKeyAuthorizedDataElements["b"] = listOf("4", "5", "k")
        val keyInfo: MutableMap<Long, ByteString> = HashMap()
        keyInfo[10L] = ByteString("C985".fromHex())
        val encodedMSO = MobileSecurityObjectGenerator(
            digestAlgorithm,
            "org.iso.18013.5.1.mDL", deviceKeyFromVector
        )
            .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
            .addDigestIdsForNamespace("org.iso.18013.5.1.US", generateISOUSDigest(digestAlgorithm))
            .setDeviceKeyAuthorizedNameSpaces(listOf("abc", "bcd"))
            .setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
            .setDeviceKeyInfo(keyInfo)
            .setValidityInfo(
                signedTimestamp,
                validFromTimestamp,
                validUntilTimestamp,
                expectedTimestamp
            )
            .generate()
        val mso = MobileSecurityObjectParser(
            encodedMSO
        ).parse()
        assertEquals("1.0", mso.version)
        assertEquals(digestAlgorithm, mso.digestAlgorithm)
        assertEquals("org.iso.18013.5.1.mDL", mso.docType)
        assertEquals(
            setOf("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
            mso.valueDigestNamespaces
        )
        assertNull(mso.getDigestIDs("abc"))
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm)
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm)
        assertEquals(deviceKeyFromVector, mso.deviceKey)
        assertEquals(listOf("abc", "bcd"), mso.deviceKeyAuthorizedNameSpaces)
        assertEquals(deviceKeyAuthorizedDataElements, mso.deviceKeyAuthorizedDataElements)
        assertEquals(keyInfo.keys, mso.deviceKeyInfo!!.keys)
        assertEquals(
            keyInfo[10L]!!.toHexString(),
            mso.deviceKeyInfo!![10L]!!.toHexString()
        )
        assertEquals(signedTimestamp, mso.signed)
        assertEquals(validFromTimestamp, mso.validFrom)
        assertEquals(validUntilTimestamp, mso.validUntil)
        assertEquals(expectedTimestamp, mso.expectedUpdate)
    }

    @Test
    fun testBasicMSO() {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex()),
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex())
        )
        val signedTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validFromTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validUntilTimestamp = Instant.fromEpochMilliseconds(1633095002000L)
        val digestAlgorithm = "SHA-256"
        val encodedMSO = MobileSecurityObjectGenerator(
            digestAlgorithm,
            "org.iso.18013.5.1.mDL", deviceKeyFromVector
        )
            .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
            .addDigestIdsForNamespace("org.iso.18013.5.1.US", generateISOUSDigest(digestAlgorithm))
            .setValidityInfo(signedTimestamp, validFromTimestamp, validUntilTimestamp, null)
            .generate()
        val mso = MobileSecurityObjectParser(encodedMSO).parse()
        assertEquals("1.0", mso.version)
        assertEquals(digestAlgorithm, mso.digestAlgorithm)
        assertEquals("org.iso.18013.5.1.mDL", mso.docType)
        assertEquals(
            setOf("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
            mso.valueDigestNamespaces
        )
        assertNull(mso.getDigestIDs("abc"))
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm)
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm)
        assertEquals(deviceKeyFromVector, mso.deviceKey)
        assertNull(mso.deviceKeyAuthorizedNameSpaces)
        assertNull(mso.deviceKeyAuthorizedDataElements)
        assertNull(mso.deviceKeyInfo)
        assertEquals(signedTimestamp, mso.signed)
        assertEquals(validFromTimestamp, mso.validFrom)
        assertEquals(validUntilTimestamp, mso.validUntil)
        assertNull(mso.expectedUpdate)
    }

    @Test
    fun testFullMSO_Sha256() {
        testFullMSO("SHA-256")
    }

    @Test
    fun testFullMSO_Sha384() {
        testFullMSO("SHA-384")
    }

    @Test
    fun testFullMSO_Sha512() {
        testFullMSO("SHA-512")
    }

    @Test
    fun testMSOExceptions() {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex()),
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex())
        )
        assertFailsWith<IllegalArgumentException>(
            "expect exception for illegal digestAlgorithm") {
            MobileSecurityObjectGenerator(
                "SHA-257",
                "org.iso.18013.5.1.mDL", deviceKeyFromVector
            )
        }
        val digestAlgorithm = "SHA-256"
        val msoGenerator = MobileSecurityObjectGenerator(
            digestAlgorithm,
            "org.iso.18013.5.1.mDL", deviceKeyFromVector
        )
        assertFailsWith<IllegalArgumentException>(
            "expect exception for empty digestIDs") {
            msoGenerator.addDigestIdsForNamespace("org.iso.18013.5.1", HashMap())
        }
        assertFailsWith<IllegalArgumentException>(
            "expect exception for validFrom < signed") {
            msoGenerator.setValidityInfo(
                Instant.fromEpochMilliseconds(1601559002000L),
                Instant.fromEpochMilliseconds(1601559001000L),
                Instant.fromEpochMilliseconds(1633095002000L),
                Instant.fromEpochMilliseconds(1611093002000L)
            )
        }
        assertFailsWith<IllegalArgumentException>(
            "expect exception for validUntil <= validFrom") {
            msoGenerator.setValidityInfo(
                Instant.fromEpochMilliseconds(1601559002000L),
                Instant.fromEpochMilliseconds(1601559002000L),
                Instant.fromEpochMilliseconds(1601559002000L),
                Instant.fromEpochMilliseconds(1611093002000L)
            )
        }
        val deviceKeyAuthorizedDataElements: MutableMap<String, List<String>> = HashMap()
        deviceKeyAuthorizedDataElements["a"] = listOf("1", "2", "f")
        deviceKeyAuthorizedDataElements["b"] = listOf("4", "5", "k")
        assertFailsWith<IllegalArgumentException>(
            "expect exception for deviceKeyAuthorizedDataElements including " +
                    "namespace in deviceKeyAuthorizedNameSpaces") {
            msoGenerator.setDeviceKeyAuthorizedNameSpaces(listOf("a", "bcd"))
                .setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
        }
        assertFailsWith<IllegalArgumentException>(
            "expect exception for deviceKeyAuthorizedNameSpaces including " +
                    "namespace in deviceKeyAuthorizedDataElements") {
            msoGenerator.setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
                .setDeviceKeyAuthorizedNameSpaces(listOf("a", "bcd"))
        }
        assertFailsWith<IllegalStateException>(
            "expect exception for msoGenerator which has not had " +
                    "addDigestIdsForNamespace and setValidityInfo called before generating") {
            msoGenerator.generate()
        }
        assertFailsWith<IllegalStateException>(
            "expect exception for msoGenerator which has not had " +
                    "addDigestIdsForNamespace called before generating") {
            MobileSecurityObjectGenerator(
                digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector
            )
                .setValidityInfo(
                    Instant.fromEpochMilliseconds(1601559002000L),
                    Instant.fromEpochMilliseconds(1601559002000L),
                    Instant.fromEpochMilliseconds(1633095002000L),
                    Instant.fromEpochMilliseconds(1611093002000L)
                )
                .generate()
        }
        assertFailsWith<IllegalStateException>(
            "expect exception for msoGenerator which has not had " +
                    "setValidityInfo called before generating") {
            MobileSecurityObjectGenerator(
                digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector
            )
                .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
                .addDigestIdsForNamespace(
                    "org.iso.18013.5.1.US",
                    generateISOUSDigest(digestAlgorithm)
                )
                .generate()
        }
    }

    // Checks that fractional parts of timestamps are dropped by MobileSecurityObjectGenerator, as
    // required by 18013-5 clause 9.1.2.4
    @Test
    fun testNoFractionalSeconds() {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex()),
            ByteString(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex())
        )
        val signedTimestamp = Instant.fromEpochSeconds(1800, HALF_SEC_IN_NANOSECONDS)
        val validFromTimestamp = Instant.fromEpochSeconds(3600, HALF_SEC_IN_NANOSECONDS)
        val validUntilTimestamp = Instant.fromEpochSeconds(7200, HALF_SEC_IN_NANOSECONDS)
        val expectedUpdateTimestamp = Instant.fromEpochSeconds(7100, HALF_SEC_IN_NANOSECONDS)

        val signedTimestampWholeSeconds = Instant.fromEpochSeconds(1800, 0)
        val validFromTimestampWholeSeconds = Instant.fromEpochSeconds(3600, 0)
        val validUntilTimestampWholeSeconds = Instant.fromEpochSeconds(7200, 0)
        val expectedUpdateTimestampWholeSeconds = Instant.fromEpochSeconds(7100, 0)

        val encodedMSO = MobileSecurityObjectGenerator(
            "SHA-256",
            "org.iso.18013.5.1.mDL", deviceKeyFromVector
        )
            .setValidityInfo(signedTimestamp, validFromTimestamp, validUntilTimestamp, expectedUpdateTimestamp)
            .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest("SHA-256"))
            .generate()
        val mso = MobileSecurityObjectParser(encodedMSO).parse()
        assertEquals(signedTimestampWholeSeconds, mso.signed)
        assertEquals(validUntilTimestampWholeSeconds, mso.validUntil)
        assertEquals(validFromTimestampWholeSeconds, mso.validFrom)
        assertEquals(expectedUpdateTimestampWholeSeconds, mso.expectedUpdate)
    }
}
