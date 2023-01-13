/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.identity;

import androidx.core.util.Pair;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.OptionalLong;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

public class SessionEncryptionReaderTest {

    @Test
    @SmallTest
    public void testAgainstVectors() {

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

        SessionEncryptionReader sessionEncryption = new SessionEncryptionReader(
                eReaderKeyPrivate, eReaderKeyPublic, eDeviceKey, Util.cborEncode(sessionTranscript));

        // Check that encryption works.
        Assert.assertArrayEquals(
                // Canonicalize b/c cbor-java automatically canonicalizes and the provided test
                // vector isn't in canonical form. When we update to cbor-java 0.9 we can use
                // nonCanonical() on CborDecoder and this can be removed.
                //
                Util.cborEncode(Util.cborDecode(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT))),
                sessionEncryption.encryptMessageToDevice(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                        OptionalLong.empty()));

        // Check that decryption works.
        Pair<byte[], OptionalLong> result = sessionEncryption.decryptMessageFromDevice(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA));
        Assert.assertFalse(result.second.isPresent());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                result.first);

        // Check that we parse status correctly.
        result = sessionEncryption.decryptMessageFromDevice(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION));
        Assert.assertEquals(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION, result.second.getAsLong());
        Assert.assertNull(result.first);

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
                sessionEncryption.encryptMessageToDevice(null,
                        OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)));

    }
}
