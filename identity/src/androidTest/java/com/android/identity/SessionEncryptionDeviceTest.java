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


import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.util.OptionalLong;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

@RunWith(AndroidJUnit4.class)
public class SessionEncryptionDeviceTest {

    @Test
    @SmallTest
    public void testAgainstVectors() {
        PrivateKey eDeviceKeyPrivate = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_DEVICE_KEY_D, 16));

        // Strip the #6.24 tag since our API expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);

        DataItem sessionTranscript = Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes));
        DataItem deviceEngagementBytes = ((Array) sessionTranscript).getDataItems().get(0);
        byte[] encodedDeviceEngagement = ((ByteString) deviceEngagementBytes).getBytes();
        DataItem handover = ((Array) sessionTranscript).getDataItems().get(2);
        byte[] encodedHandover = Util.cborEncode(handover);

        SessionEncryptionDevice sessionEncryption = new SessionEncryptionDevice(
                eDeviceKeyPrivate, encodedDeviceEngagement, encodedHandover);

        // Check that decryption works.
        Pair<byte[], OptionalLong> result = sessionEncryption.decryptMessageFromReader(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_ESTABLISHMENT));
        Assert.assertFalse(result.second.isPresent());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST),
                result.first);

        // Check the correct session transcript was calculated, e.g. that it
        // matches what's in ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        //
        // This is the earliest we can get the session transcsript (we don't have
        // eReaderKeyPub until a message has been received) and we may indeed need
        // it this early for the initial response message.
        //
        Assert.assertArrayEquals(Util.cborEncode(sessionTranscript),
                sessionEncryption.getSessionTranscript());

        // Check that encryption works.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_DATA),
                sessionEncryption.encryptMessageToReader(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE),
                        OptionalLong.empty()));

        // Check that we parse status correctly.
        result = sessionEncryption.decryptMessageFromReader(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION));
        Assert.assertEquals(20, result.second.getAsLong());
        Assert.assertNull(result.first);

        // Check we can generate messages with status.
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_SESSION_TERMINATION),
                sessionEncryption.encryptMessageToReader(null, OptionalLong.of(20)));
    }

}
