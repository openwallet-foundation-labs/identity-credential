/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collection;

public class DeviceRequestParserTest {

    @Test
    @SmallTest
    public void testDeviceRequestParserWithVectors() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        DeviceRequestParser parser = new DeviceRequestParser();
        parser.setDeviceRequest(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST));
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceRequestParser.DeviceRequest request = parser.parse();

        Assert.assertEquals("1.0", request.getVersion());

        Collection<DeviceRequestParser.DocumentRequest> docRequests = request.getDocRequests();
        Assert.assertEquals(1, docRequests.size());
        DeviceRequestParser.DocumentRequest dr = docRequests.iterator().next();

        Assert.assertEquals("org.iso.18013.5.1.mDL", dr.getDocType());
        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST),
                dr.getItemsRequest());

        Collection<X509Certificate> readerCertChain = dr.getReaderCertificateChain();
        Assert.assertEquals(1, readerCertChain.size());
        X509Certificate readerCert = readerCertChain.iterator().next();
        try {
            Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_CERT),
                    readerCert.getEncoded());
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            Assert.fail();
        }

        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_AUTH),
                dr.getReaderAuth());

        Assert.assertTrue(dr.getReaderAuthenticated());

        Assert.assertArrayEquals(new String[]{"org.iso.18013.5.1"}, dr.getNamespaces().toArray());
        Assert.assertEquals(6, dr.getEntryNames("org.iso.18013.5.1").size());
        Assert.assertTrue(dr.getIntentToRetain("org.iso.18013.5.1", "family_name"));
        Assert.assertTrue(dr.getIntentToRetain("org.iso.18013.5.1", "document_number"));
        Assert.assertTrue(dr.getIntentToRetain("org.iso.18013.5.1", "driving_privileges"));
        Assert.assertTrue(dr.getIntentToRetain("org.iso.18013.5.1", "issue_date"));
        Assert.assertTrue(dr.getIntentToRetain("org.iso.18013.5.1", "expiry_date"));
        Assert.assertFalse(dr.getIntentToRetain("org.iso.18013.5.1", "portrait"));
        try {
            dr.getEntryNames("ns-was-not-requested");
            Assert.fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            dr.getIntentToRetain("ns-was-not-requested", "elem-was-not-requested");
            Assert.fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            dr.getIntentToRetain("org.iso.18013.5.1", "elem-was-not-requested");
            Assert.fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserWithVectorsMalformedReaderSignature() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
            Util.cborExtractTaggedAndEncodedCbor(
                Util.cborDecode(encodedSessionTranscriptBytes)));

        // We know the COSE_Sign1 signature for reader authentication is at index 655 and
        // starts with 1f340006... Poison that so we can check whether signature verification
        // detects it...
        byte[] encodedDeviceRequest = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST);
        Assert.assertEquals((byte) 0x1f, encodedDeviceRequest[655]);
        encodedDeviceRequest[655] = 0x1e;

        DeviceRequestParser parser = new DeviceRequestParser();
        parser.setDeviceRequest(encodedDeviceRequest);
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceRequestParser.DeviceRequest request = parser.parse();

        Assert.assertEquals("1.0", request.getVersion());

        Collection<DeviceRequestParser.DocumentRequest> docRequests = request.getDocRequests();
        Assert.assertEquals(1, docRequests.size());
        DeviceRequestParser.DocumentRequest dr = docRequests.iterator().next();

        Assert.assertEquals("org.iso.18013.5.1.mDL", dr.getDocType());
        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST),
            dr.getItemsRequest());

        Collection<X509Certificate> readerCertChain = dr.getReaderCertificateChain();
        Assert.assertEquals(1, readerCertChain.size());
        X509Certificate readerCert = readerCertChain.iterator().next();
        try {
            Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_CERT),
                readerCert.getEncoded());
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            Assert.fail();
        }

        Assert.assertFalse(dr.getReaderAuthenticated());
    }

    // TODO: add tests where the reader uses non-P256 curves (e.g. Brainpool, 25519) to sign
    //  the request and make sure this works with the API (or at least doesn't throw an
    //  exception)

}
