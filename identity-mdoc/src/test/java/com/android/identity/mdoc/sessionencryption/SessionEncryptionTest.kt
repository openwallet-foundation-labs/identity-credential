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
package com.android.identity.mdoc.sessionencryption

import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPrivateKeyDoubleCoordinate
import com.android.identity.internal.Util
import com.android.identity.mdoc.TestVectors
import com.android.identity.mdoc.engagement.EngagementParser
import com.android.identity.util.Constants
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

class SessionEncryptionTest {
    @Before
    fun setUp() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testReaderAgainstVectors() {
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
        )

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val sessionTranscript = Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        val deviceEngagementBytes = sessionTranscript[0]
        val encodedDeviceEngagement = deviceEngagementBytes.asTagged.asBstr
        val engagementParser = EngagementParser(encodedDeviceEngagement)
        val engagement = engagementParser.parse()
        val eDeviceKey = engagement.eSenderKey
        val sessionEncryption = SessionEncryption(
            SessionEncryption.Role.MDOC_READER,
            eReaderKey,
            eDeviceKey,
            Cbor.encode(sessionTranscript)
        )

        // Check that encryption works.
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT),
            sessionEncryption.encryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                null
            )
        )

        // Check that decryption works.
        var result = sessionEncryption.decryptMessage(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA)
        )
        Assert.assertNull(result.second)
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
            result.first
        )

        // Check that we parse status correctly.
        result = sessionEncryption.decryptMessage(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION)
        )
        Assert.assertEquals(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION, result.second)
        Assert.assertNull(result.first)

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
            sessionEncryption.encryptMessage(
                null,
                Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
            )
        )
    }

    @Test
    fun testDeviceAgainstVectors() {
        val eDeviceKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y)
        )

        // Strip the #6.24 tag since our API expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val sessionTranscript = Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        val sessionEstablishment = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT)
        val eReaderKey = Cbor.decode(sessionEstablishment)["eReaderKey"]
            .asTaggedEncodedCbor.asCoseKey.ecPublicKey
        val sessionEncryptionDevice = SessionEncryption(
            SessionEncryption.Role.MDOC,
            eDeviceKey,
            eReaderKey,
            Cbor.encode(sessionTranscript)
        )

        // Check that decryption works.
        var result = sessionEncryptionDevice.decryptMessage(
            sessionEstablishment
        )
        Assert.assertNull(result.second)
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
            result.first
        )

        // Check that encryption works.
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA),
            sessionEncryptionDevice.encryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                null
            )
        )

        // Check that we parse status correctly.
        result = sessionEncryptionDevice.decryptMessage(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION)
        )
        Assert.assertEquals(
            Constants.SESSION_DATA_STATUS_SESSION_TERMINATION,
            result.second
        )
        Assert.assertNull(result.first)

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
            sessionEncryptionDevice.encryptMessage(null, 20L)
        )
    }

    private fun testCurve(curve: EcCurve) {
        val eReaderKey = Crypto.createEcPrivateKey(curve)
        val eDeviceKey = Crypto.createEcPrivateKey(curve)
        val encodedSessionTranscript = byteArrayOf(1, 2, 3)
        val sessionEncryptionReader = SessionEncryption(
            SessionEncryption.Role.MDOC_READER,
            eReaderKey,
            eDeviceKey.publicKey,
            encodedSessionTranscript
        )
        sessionEncryptionReader.setSendSessionEstablishment(false)
        val sessionEncryptionHolder = SessionEncryption(
            SessionEncryption.Role.MDOC,
            eDeviceKey,
            eReaderKey.publicKey,
            encodedSessionTranscript
        )
        for (i in 1..3) {
            // have reader generate sessionEstablishment and check holder decryption of sessionEstablishment
            val mdocRequest = sessionEncryptionReader.encryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                null
            )
            var result = sessionEncryptionHolder.decryptMessage(
                mdocRequest
            )
            Assert.assertNull(result.second)
            Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                result.first
            )
            Assert.assertEquals(i.toLong(), sessionEncryptionReader.numMessagesEncrypted.toLong())
            Assert.assertEquals(i.toLong(), sessionEncryptionHolder.numMessagesDecrypted.toLong())

            // have holder generate deviceResponse and check reader decryption of deviceResponse
            val deviceResponse = sessionEncryptionHolder.encryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                null
            )
            result = sessionEncryptionReader.decryptMessage(deviceResponse)
            Assert.assertNull(result.second)
            Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                result.first
            )
            Assert.assertEquals(i.toLong(), sessionEncryptionHolder.numMessagesEncrypted.toLong())
            Assert.assertEquals(i.toLong(), sessionEncryptionReader.numMessagesDecrypted.toLong())
        }
    }

    @Test
    fun testP256() {
        testCurve(EcCurve.P256)
    }

    @Test
    fun testP384() {
        testCurve(EcCurve.P384)
    }

    @Test
    fun testP521() {
        testCurve(EcCurve.P521)
    }

    @Test
    fun testBrainpool256() {
        testCurve(EcCurve.BRAINPOOLP256R1)
    }

    @Test
    fun testBrainpool320() {
        testCurve(EcCurve.BRAINPOOLP320R1)
    }

    @Test
    fun testBrainpool384() {
        testCurve(EcCurve.BRAINPOOLP384R1)
    }

    @Test
    fun testBrainpool521() {
        testCurve(EcCurve.BRAINPOOLP512R1)
    }

    @Test
    fun testX25519() {
        testCurve(EcCurve.X25519)
    }

    @Test
    fun testX448() {
        testCurve(EcCurve.X448)
    }
}
