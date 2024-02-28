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

package com.android.identity.mdoc.sessionencryption;

import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DataItem;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPrivateKeyDoubleCoordinate;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.TestVectors;
import com.android.identity.internal.Util;
import com.android.identity.crypto.EcCurve;
import com.android.identity.mdoc.engagement.EngagementParser;
import com.android.identity.util.Constants;
import com.android.identity.util.Logger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.OptionalLong;

public class SessionEncryptionTest {

    @Before
    public void setUp() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @Test
    public void testReaderAgainstVectors() {
        EcPrivateKey eReaderKey = new EcPrivateKeyDoubleCoordinate(
                EcCurve.P256,
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y));

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);

        DataItem sessionTranscript = Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor();
        DataItem deviceEngagementBytes = sessionTranscript.get(0);
        byte[] encodedDeviceEngagement = deviceEngagementBytes.getAsTagged().getAsBstr();

        EngagementParser engagementParser = new EngagementParser(encodedDeviceEngagement);
        EngagementParser.Engagement engagement = engagementParser.parse();
        EcPublicKey eDeviceKey = engagement.getESenderKey();

        SessionEncryption sessionEncryption = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                eReaderKey,
                eDeviceKey,
                Cbor.encode(sessionTranscript));

        // Check that encryption works.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT),
                sessionEncryption.encryptMessage(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                        OptionalLong.empty()));

        // Check that decryption works.
        SessionEncryption.DecryptedMessage result = sessionEncryption.decryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA));
        Assert.assertFalse(result.getStatus().isPresent());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                result.getData());

        // Check that we parse status correctly.
        result = sessionEncryption.decryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION));
        Assert.assertEquals(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION, result.getStatus().getAsLong());
        Assert.assertNull(result.getData());

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
                sessionEncryption.encryptMessage(null,
                        OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)));
    }

    @Test
    public void testDeviceAgainstVectors() {
        EcPrivateKey eDeviceKey = new EcPrivateKeyDoubleCoordinate(
                EcCurve.P256,
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D),
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_X),
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_Y));

        // Strip the #6.24 tag since our API expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);

        DataItem sessionTranscript = Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor();
        DataItem deviceEngagementBytes = sessionTranscript.get(0);
        byte[] encodedDeviceEngagement = deviceEngagementBytes.getAsTagged().getAsBstr();

        EngagementParser engagementParser = new EngagementParser(encodedDeviceEngagement);
        EngagementParser.Engagement engagement = engagementParser.parse();
        EcPublicKey eDeviceKeyPublic = engagement.getESenderKey();

        byte[] sessionEstablishment = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT);
        EcPublicKey eReaderKey = Cbor.decode(sessionEstablishment)
                .get("eReaderKey")
                .getAsTaggedEncodedCbor()
                .getAsCoseKey()
                .getEcPublicKey();

        SessionEncryption sessionEncryptionDevice = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                eDeviceKey,
                eReaderKey,
                Cbor.encode(sessionTranscript));

        // Check that decryption works.
        SessionEncryption.DecryptedMessage result = sessionEncryptionDevice.decryptMessage(
                sessionEstablishment);
        Assert.assertFalse(result.getStatus().isPresent());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                result.getData());

        // Check that encryption works.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA),
                sessionEncryptionDevice.encryptMessage(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                        OptionalLong.empty()));

        // Check that we parse status correctly.
        result = sessionEncryptionDevice.decryptMessage(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION));
        Assert.assertEquals(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION,
                result.getStatus().getAsLong());
        Assert.assertNull(result.getData());

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
                sessionEncryptionDevice.encryptMessage(null, OptionalLong.of(20)));
    }

    private void testCurve(EcCurve curve) {
        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(curve);
        EcPrivateKey eDeviceKey = Crypto.createEcPrivateKey(curve);

        byte[] encodedSessionTranscript = new byte[] {1, 2, 3};

        SessionEncryption sessionEncryptionReader = new SessionEncryption(
                SessionEncryption.ROLE_MDOC_READER,
                eReaderKey,
                eDeviceKey.getPublicKey(),
                encodedSessionTranscript);
        sessionEncryptionReader.setSendSessionEstablishment(false);
        SessionEncryption sessionEncryptionHolder = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                eDeviceKey,
                eReaderKey.getPublicKey(),
                encodedSessionTranscript);

        for (int i = 1; i <= 3; i++) {
            // have reader generate sessionEstablishment and check holder decryption of sessionEstablishment
            byte[] mdocRequest = sessionEncryptionReader.encryptMessage(
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                    OptionalLong.empty());
            SessionEncryption.DecryptedMessage result = sessionEncryptionHolder.decryptMessage(
                    mdocRequest);
            Assert.assertFalse(result.getStatus().isPresent());
            Assert.assertArrayEquals(
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                    result.getData());
            Assert.assertEquals(i, sessionEncryptionReader.getNumMessagesEncrypted());
            Assert.assertEquals(i, sessionEncryptionHolder.getNumMessagesDecrypted());

            // have holder generate deviceResponse and check reader decryption of deviceResponse
            byte[] deviceResponse = sessionEncryptionHolder.encryptMessage(
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                    OptionalLong.empty());
            result = sessionEncryptionReader.decryptMessage(deviceResponse);
            Assert.assertFalse(result.getStatus().isPresent());
            Assert.assertArrayEquals(
                    Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                    result.getData());
            Assert.assertEquals(i, sessionEncryptionHolder.getNumMessagesEncrypted());
            Assert.assertEquals(i, sessionEncryptionReader.getNumMessagesDecrypted());
        }
    }

    @Test
    public void testP256() {
        testCurve(EcCurve.P256);
    }

    @Test
    public void testP384() {
        testCurve(EcCurve.P384);
    }

    @Test
    public void testP521() {
        testCurve(EcCurve.P521);
    }

    @Test
    public void testBrainpool256() {
        testCurve(EcCurve.BRAINPOOLP256R1);
    }

    @Test
    public void testBrainpool320() {
        testCurve(EcCurve.BRAINPOOLP320R1);
    }

    @Test
    public void testBrainpool384() {
        testCurve(EcCurve.BRAINPOOLP384R1);
    }

    @Test
    public void testBrainpool521() {
        testCurve(EcCurve.BRAINPOOLP512R1);
    }

    @Test
    public void testX25519() {
        testCurve(EcCurve.X25519);
    }

    @Test
    public void testX448() {
        testCurve(EcCurve.X448);
    }
}
