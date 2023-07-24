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

import com.android.identity.TestUtilities;
import com.android.identity.TestVectors;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.engagement.EngagementParser;
import com.android.identity.util.Constants;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.OptionalLong;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

public class SessionEncryptionTest {

    @Before
    public void setUp() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @After
    public void tearDown() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    }

    @Test
    public void testReaderAgainstVectors() {
        PublicKey eReaderKeyPublic = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y, 16));
        PrivateKey eReaderKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);

        DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes));
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] encodedDeviceEngagement = ((ByteString) deviceEngagementBytes).getBytes();
        DataItem handover = ((Array) sessionTranscript).getDataItems().get(2);
        byte[] encodedHandover = Util.cborEncode(handover);

        EngagementParser engagementParser = new EngagementParser(encodedDeviceEngagement);
        EngagementParser.Engagement engagement = engagementParser.parse();
        PublicKey eDeviceKey = engagement.getESenderKey();

        SessionEncryption sessionEncryption = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                new KeyPair(eReaderKeyPublic, eReaderKeyPrivate),
                eDeviceKey,
                Util.cborEncode(sessionTranscript));

        // Check that encryption works.
        Assert.assertArrayEquals(
                // Canonicalize b/c cbor-java automatically canonicalizes and the provided test
                // vector isn't in canonical form. When we update to cbor-java 0.9 we can use
                // nonCanonical() on CborDecoder and this can be removed.
                //
                Util.cborEncode(Util.cborDecode(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT))),
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
        PrivateKey eDeviceKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D, 16));

        // Strip the #6.24 tag since our API expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);

        DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes));
        byte[] encodedSessionTranscript = Util.cborEncode(sessionTranscript);
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] encodedDeviceEngagement = ((ByteString) deviceEngagementBytes).getBytes();
        DataItem handover = ((Array) sessionTranscript).getDataItems().get(2);
        byte[] encodedHandover = Util.cborEncode(handover);

        EngagementParser engagementParser = new EngagementParser(encodedDeviceEngagement);
        EngagementParser.Engagement engagement = engagementParser.parse();
        PublicKey eDeviceKeyPublic = engagement.getESenderKey();

        byte[] sessionEstablishment = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT);
        byte[] eReaderKeyBytes = Util.cborMapExtractByteString(Util.cborDecode(sessionEstablishment), "eReaderKey");
        PublicKey eReaderKey = Util.coseKeyDecode(Util.cborDecode(eReaderKeyBytes));

        SessionEncryption sessionEncryptionDevice = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                new KeyPair(eDeviceKeyPublic, eDeviceKeyPrivate),
                eReaderKey,
                encodedSessionTranscript);

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

    private void testCurve(@Constants.EcCurve int curve) {
        KeyPair eReaderKeyPair = TestUtilities.createEphemeralKeyPair(curve);
        PublicKey eReaderKeyPublic = eReaderKeyPair.getPublic();
        KeyPair eDeviceKeyPair = TestUtilities.createEphemeralKeyPair(curve);
        PublicKey eDeviceKeyPublic = eDeviceKeyPair.getPublic();
        PrivateKey eDeviceKeyPrivate = eDeviceKeyPair.getPrivate();

        byte[] encodedSessionTranscript = new byte[] {1, 2, 3};

        SessionEncryption sessionEncryptionReader = new SessionEncryption(
                SessionEncryption.ROLE_MDOC_READER,
                eReaderKeyPair,
                eDeviceKeyPublic,
                encodedSessionTranscript);
        sessionEncryptionReader.setSendSessionEstablishment(false);
        SessionEncryption sessionEncryptionHolder = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                new KeyPair(eDeviceKeyPublic, eDeviceKeyPrivate),
                eReaderKeyPublic,
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
        testCurve(Constants.EC_CURVE_P256);
    }

    @Test
    public void testP384() {
        testCurve(Constants.EC_CURVE_P384);
    }

    @Test
    public void testP521() {
        testCurve(Constants.EC_CURVE_P521);
    }

    @Test
    public void testBrainpool256() {
        testCurve(Constants.EC_CURVE_BRAINPOOLP256R1);
    }

    @Test
    public void testBrainpool320() {
        testCurve(Constants.EC_CURVE_BRAINPOOLP320R1);
    }

    @Test
    public void testBrainpool384() {
        testCurve(Constants.EC_CURVE_BRAINPOOLP384R1);
    }

    @Test
    public void testBrainpool521() {
        testCurve(Constants.EC_CURVE_BRAINPOOLP512R1);
    }

    // TODO add these back in after Keystore abstraction is merged
    @Ignore
    @Test
    public void testEd25519() {
        testCurve(Constants.EC_CURVE_ED25519);
    }

    @Ignore
    @Test
    public void testEd448() {
        testCurve(Constants.EC_CURVE_ED448);
    }
}
