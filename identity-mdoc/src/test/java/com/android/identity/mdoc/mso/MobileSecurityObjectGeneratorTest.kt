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
import com.android.identity.util.toHex
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security
import kotlinx.datetime.Instant

class MobileSecurityObjectGeneratorTest {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private fun getDigestAlg(digestAlgorithm: String): Algorithm {
        return when (digestAlgorithm) {
            "SHA-256" -> Algorithm.SHA256
            "SHA-384" -> Algorithm.SHA384
            "SHA-512" -> Algorithm.SHA512
            else -> throw AssertionError()
        }
    }
    
    private fun generateISODigest(digestAlgorithm: String): Map<Long, ByteArray> {
        val alg = getDigestAlg(digestAlgorithm)
        val isoDigestIDs = mutableMapOf<Long, ByteArray>()
        isoDigestIDs[0L] = Crypto.digest(alg, "aardvark".toByteArray())
        isoDigestIDs[1L] = Crypto.digest(alg, "alligator".toByteArray())
        isoDigestIDs[2L] = Crypto.digest(alg, "baboon".toByteArray())
        isoDigestIDs[3L] = Crypto.digest(alg, "butterfly".toByteArray())
        isoDigestIDs[4L] = Crypto.digest(alg, "cat".toByteArray())
        isoDigestIDs[5L] = Crypto.digest(alg, "cricket".toByteArray())
        isoDigestIDs[6L] = Crypto.digest(alg, "dog".toByteArray())
        isoDigestIDs[7L] = Crypto.digest(alg, "elephant".toByteArray())
        isoDigestIDs[8L] = Crypto.digest(alg, "firefly".toByteArray())
        isoDigestIDs[9L] = Crypto.digest(alg, "frog".toByteArray())
        isoDigestIDs[10L] = Crypto.digest(alg, "gecko".toByteArray())
        isoDigestIDs[11L] = Crypto.digest(alg, "hippo".toByteArray())
        isoDigestIDs[12L] = Crypto.digest(alg, "iguana".toByteArray())
        return isoDigestIDs
    }

    private fun generateISOUSDigest(digestAlgorithm: String): Map<Long, ByteArray> {
        val alg = getDigestAlg(digestAlgorithm)
        val isoUSDigestIDs: MutableMap<Long, ByteArray> = HashMap()
        isoUSDigestIDs[0L] = Crypto.digest(alg, "jaguar".toByteArray())
        isoUSDigestIDs[1L] = Crypto.digest(alg, "jellyfish".toByteArray())
        isoUSDigestIDs[2L] = Crypto.digest(alg, "koala".toByteArray())
        isoUSDigestIDs[3L] = Crypto.digest(alg, "lemur".toByteArray())
        return isoUSDigestIDs
    }

    private fun checkISODigest(isoDigestIDs: Map<Long, ByteArray>?, digestAlgorithm: String) {
        val alg = getDigestAlg(digestAlgorithm)
        Assert.assertEquals(
            setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
            isoDigestIDs!!.keys
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "aardvark".toByteArray()),
            isoDigestIDs[0L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "alligator".toByteArray()),
            isoDigestIDs[1L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "baboon".toByteArray()),
            isoDigestIDs[2L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "butterfly".toByteArray()),
            isoDigestIDs[3L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "cat".toByteArray()),
            isoDigestIDs[4L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "cricket".toByteArray()),
            isoDigestIDs[5L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "dog".toByteArray()),
            isoDigestIDs[6L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "elephant".toByteArray()),
            isoDigestIDs[7L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "firefly".toByteArray()),
            isoDigestIDs[8L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "frog".toByteArray()),
            isoDigestIDs[9L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "gecko".toByteArray()),
            isoDigestIDs[10L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "hippo".toByteArray()),
            isoDigestIDs[11L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "iguana".toByteArray()),
            isoDigestIDs[12L]
        )
    }

    private fun checkISOUSDigest(isoUSDigestIDs: Map<Long, ByteArray>?, digestAlgorithm: String) {
        val alg = getDigestAlg(digestAlgorithm)
        Assert.assertEquals(setOf(0L, 1L, 2L, 3L), isoUSDigestIDs!!.keys)
        Assert.assertArrayEquals(
            Crypto.digest(alg, "jaguar".toByteArray()),
            isoUSDigestIDs[0L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "jellyfish".toByteArray()),
            isoUSDigestIDs[1L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "koala".toByteArray()),
            isoUSDigestIDs[2L]
        )
        Assert.assertArrayEquals(
            Crypto.digest(alg, "lemur".toByteArray()),
            isoUSDigestIDs[3L]
        )
    }

    fun testFullMSO(digestAlgorithm: String) {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex
        )
        val signedTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validFromTimestamp = Instant.fromEpochMilliseconds(1601559002000L)
        val validUntilTimestamp = Instant.fromEpochMilliseconds(1633095002000L)
        val expectedTimestamp = Instant.fromEpochMilliseconds(1611093002000L)
        val deviceKeyAuthorizedDataElements: MutableMap<String, List<String>> = HashMap()
        deviceKeyAuthorizedDataElements["a"] = listOf("1", "2", "f")
        deviceKeyAuthorizedDataElements["b"] = listOf("4", "5", "k")
        val keyInfo: MutableMap<Long, ByteArray> = HashMap()
        keyInfo[10L] = "C985".fromHex
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
        Assert.assertEquals("1.0", mso.version)
        Assert.assertEquals(digestAlgorithm, mso.digestAlgorithm)
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.docType)
        Assert.assertEquals(
            setOf("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
            mso.valueDigestNamespaces
        )
        Assert.assertNull(mso.getDigestIDs("abc"))
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm)
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm)
        Assert.assertEquals(deviceKeyFromVector, mso.deviceKey)
        Assert.assertEquals(listOf("abc", "bcd"), mso.deviceKeyAuthorizedNameSpaces)
        Assert.assertEquals(deviceKeyAuthorizedDataElements, mso.deviceKeyAuthorizedDataElements)
        Assert.assertEquals(keyInfo.keys, mso.deviceKeyInfo!!.keys)
        Assert.assertEquals(
            keyInfo[10L]!!.toHex,
            mso.deviceKeyInfo!![10L]!!.toHex
        )
        Assert.assertEquals(signedTimestamp, mso.signed)
        Assert.assertEquals(validFromTimestamp, mso.validFrom)
        Assert.assertEquals(validUntilTimestamp, mso.validUntil)
        Assert.assertEquals(expectedTimestamp, mso.expectedUpdate)
    }

    @Test
    fun testBasicMSO() {
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex
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
        Assert.assertEquals("1.0", mso.version)
        Assert.assertEquals(digestAlgorithm, mso.digestAlgorithm)
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.docType)
        Assert.assertEquals(
            setOf("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
            mso.valueDigestNamespaces
        )
        Assert.assertNull(mso.getDigestIDs("abc"))
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm)
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm)
        Assert.assertEquals(deviceKeyFromVector, mso.deviceKey)
        Assert.assertNull(mso.deviceKeyAuthorizedNameSpaces)
        Assert.assertNull(mso.deviceKeyAuthorizedDataElements)
        Assert.assertNull(mso.deviceKeyInfo)
        Assert.assertEquals(signedTimestamp, mso.signed)
        Assert.assertEquals(validFromTimestamp, mso.validFrom)
        Assert.assertEquals(validUntilTimestamp, mso.validUntil)
        Assert.assertNull(mso.expectedUpdate)
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
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex
        )
        Assert.assertThrows(
            "expect exception for illegal digestAlgorithm",
            IllegalArgumentException::class.java
        ) {
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
        Assert.assertThrows(
            "expect exception for empty digestIDs",
            IllegalArgumentException::class.java
        ) { msoGenerator.addDigestIdsForNamespace("org.iso.18013.5.1", HashMap()) }
        Assert.assertThrows(
            "expect exception for validFrom < signed",
            IllegalArgumentException::class.java
        ) {
            msoGenerator.setValidityInfo(
                Instant.fromEpochMilliseconds(1601559002000L),
                Instant.fromEpochMilliseconds(1601559001000L),
                Instant.fromEpochMilliseconds(1633095002000L),
                Instant.fromEpochMilliseconds(1611093002000L)
            )
        }
        Assert.assertThrows(
            "expect exception for validUntil <= validFrom",
            IllegalArgumentException::class.java
        ) {
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
        Assert.assertThrows(
            "expect exception for deviceKeyAuthorizedDataElements including " +
                    "namespace in deviceKeyAuthorizedNameSpaces",
            IllegalArgumentException::class.java
        ) {
            msoGenerator.setDeviceKeyAuthorizedNameSpaces(listOf("a", "bcd"))
                .setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
        }
        Assert.assertThrows(
            "expect exception for deviceKeyAuthorizedNameSpaces including " +
                    "namespace in deviceKeyAuthorizedDataElements",
            IllegalArgumentException::class.java
        ) {
            msoGenerator.setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
                .setDeviceKeyAuthorizedNameSpaces(listOf("a", "bcd"))
        }
        Assert.assertThrows(
            "expect exception for msoGenerator which has not had " +
                    "addDigestIdsForNamespace and setValidityInfo called before generating",
            IllegalStateException::class.java
        ) { msoGenerator.generate() }
        Assert.assertThrows(
            "expect exception for msoGenerator which has not had " +
                    "addDigestIdsForNamespace called before generating",
            IllegalStateException::class.java
        ) {
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
        Assert.assertThrows(
            "expect exception for msoGenerator which has not had " +
                    "setValidityInfo called before generating",
            IllegalStateException::class.java
        ) {
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
}
