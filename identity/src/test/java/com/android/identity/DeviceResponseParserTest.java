/*
 * Copyright 2022gn The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

public class DeviceResponseParserTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";

    @Test
    @SmallTest
    public void testDeviceResponseParserWithVectors() throws CertificateEncodingException {

        // NOTE: This tests tests the MAC verification path of DeviceResponseParser, the
        // ECDSA verification path is tested in DeviceResponseGeneratorTest by virtue of
        // SUtil.getIdentityCredentialStore() defaulting to the Jetpack.

        byte[] encodedDeviceResponse = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        PrivateKey eReaderKey = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                .setDeviceResponse(encodedDeviceResponse)
                .setSessionTranscript(encodedSessionTranscript)
                .setEphemeralReaderKey(eReaderKey)
                .parse();
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceResponseParser.Document> documents = dr.getDocuments();
        Assert.assertEquals(1, documents.size());

        DeviceResponseParser.Document d = documents.get(0);

        // Check ValidityInfo is correctly parsed, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        // 2020-10-01T13:30:02Z == 1601559002000
        Assert.assertEquals(1601559002000L, d.getValidityInfoSigned().toEpochMilli());
        Assert.assertEquals(1601559002000L, d.getValidityInfoValidFrom().toEpochMilli());
        // 2021-10-01T13:30:02Z == 1601559002000
        Assert.assertEquals(1633095002000L, d.getValidityInfoValidUntil().toEpochMilli());
        Assert.assertNull(d.getValidityInfoExpectedUpdate());

        // Check DeviceKey is correctly parsed
        PublicKey deviceKeyFromVector = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y, 16));
        Assert.assertEquals(deviceKeyFromVector, d.getDeviceKey());

        // Test example is using a MAC.
        Assert.assertFalse(d.getDeviceSignedAuthenticatedViaSignature());

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        Assert.assertTrue(d.getIssuerSignedAuthenticated());
        Assert.assertTrue(d.getDeviceSignedAuthenticated());
        Assert.assertEquals(MDL_DOCTYPE, d.getDocType());
        Assert.assertEquals(0, d.getDeviceNamespaces().size());
        Assert.assertEquals(1, d.getIssuerNamespaces().size());
        Assert.assertEquals(MDL_NAMESPACE, d.getIssuerNamespaces().iterator().next());
        Assert.assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size());
        Assert.assertEquals("Doe",
                d.getIssuerEntryString(MDL_NAMESPACE, "family_name"));
        Assert.assertEquals("123456789",
                d.getIssuerEntryString(MDL_NAMESPACE, "document_number"));
        Assert.assertEquals("tag 1004 '2019-10-20'",
                Util.cborPrettyPrint(d.getIssuerEntryData(MDL_NAMESPACE,
                        "issue_date")));
        Assert.assertEquals("tag 1004 '2024-10-20'",
                Util.cborPrettyPrint(d.getIssuerEntryData(MDL_NAMESPACE,
                        "expiry_date")));
        Assert.assertEquals("[\n"
                        + "  {\n"
                        + "    'vehicle_category_code' : 'A',\n"
                        + "    'issue_date' : tag 1004 '2018-08-09',\n"
                        + "    'expiry_date' : tag 1004 '2024-10-20'\n"
                        + "  },\n"
                        + "  {\n"
                        + "    'vehicle_category_code' : 'B',\n"
                        + "    'issue_date' : tag 1004 '2017-02-23',\n"
                        + "    'expiry_date' : tag 1004 '2024-10-20'\n"
                        + "  }\n"
                        + "]",
                Util.cborPrettyPrint(d.getIssuerEntryData(MDL_NAMESPACE,
                        "driving_privileges")));
        Assert.assertArrayEquals(Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE_PORTRAIT_DATA),
                d.getIssuerEntryByteString(MDL_NAMESPACE, "portrait"));

        // Check the issuer-signed items all validated (digest matches what's in MSO)
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"));
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "document_number"));
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "issue_date"));
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "expiry_date"));
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "driving_privileges"));
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "portrait"));
        Assert.assertEquals(0, d.getNumIssuerEntryDigestMatchFailures());

        // Check the returned issuer certificate matches.
        //
        List<X509Certificate> issuerCertChain = d.getIssuerCertificateChain();
        Assert.assertEquals(1, issuerCertChain.size());
        X509Certificate issuerCert = issuerCertChain.get(0);
        Assert.assertEquals("C=US, CN=utopia ds", issuerCert.getSubjectX500Principal().toString());
        Assert.assertEquals("C=US, CN=utopia iaca", issuerCert.getIssuerX500Principal().toString());
        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DS_CERT),
                issuerCert.getEncoded());
    }

    @Test
    @SmallTest
    public void testDeviceResponseParserWithVectorsMalformedIssuerItem() {
        byte[] encodedDeviceResponse = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);

        // We know that the value for family_name in IssuerSignedItem is at offset 200.
        // Change this from "Doe" to "Foe" to force validation of that item to fail.
        //
        Assert.assertEquals(0x44, encodedDeviceResponse[200]);
        encodedDeviceResponse[200] = (byte) 0x46;

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        PrivateKey eReaderKey = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                .setDeviceResponse(encodedDeviceResponse)
                .setSessionTranscript(encodedSessionTranscript)
                .setEphemeralReaderKey(eReaderKey)
                .parse();
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceResponseParser.Document> documents = dr.getDocuments();
        Assert.assertEquals(1, documents.size());

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        DeviceResponseParser.Document d = documents.get(0);
        Assert.assertTrue(d.getIssuerSignedAuthenticated());
        Assert.assertTrue(d.getDeviceSignedAuthenticated());

        // We changed "Doe" to "Foe". Check that it doesn't validate.
        //
        Assert.assertEquals("Foe",
                d.getIssuerEntryString(MDL_NAMESPACE, "family_name"));
        Assert.assertFalse(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"));
        Assert.assertEquals(1, d.getNumIssuerEntryDigestMatchFailures());
    }

    @Test
    @SmallTest
    public void testDeviceResponseParserWithVectorsMalformedDeviceSigned() {
        byte[] encodedDeviceResponse = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);

        // We know that the 32 bytes for the MAC in DeviceMac CBOR starts at offset 3522 and
        // starts with E99521A8. Poison that to cause DeviceSigned to not authenticate.
        Assert.assertEquals((byte) 0xe9, encodedDeviceResponse[3522]);
        encodedDeviceResponse[3522] = (byte) 0xe8;

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        PrivateKey eReaderKey = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                .setDeviceResponse(encodedDeviceResponse)
                .setSessionTranscript(encodedSessionTranscript)
                .setEphemeralReaderKey(eReaderKey)
                .parse();
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceResponseParser.Document> documents = dr.getDocuments();
        Assert.assertEquals(1, documents.size());

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        DeviceResponseParser.Document d = documents.get(0);
        Assert.assertTrue(d.getIssuerSignedAuthenticated());

        // Validation of DeviceSigned should fail.
        Assert.assertFalse(d.getDeviceSignedAuthenticated());
    }

    @Test
    @SmallTest
    public void testDeviceResponseParserWithVectorsMalformedIssuerSigned() {
        byte[] encodedDeviceResponse = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE);

        // We know that issuer signature starts at offset 3398 and starts with 59E64205DF1E.
        // Poison that to cause IssuerSigned to not authenticate.
        Assert.assertEquals((byte) 0x59, encodedDeviceResponse[3398]);
        encodedDeviceResponse[3398] = (byte) 0x5a;

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        PrivateKey eReaderKey = Util.getPrivateKeyFromInteger(new BigInteger(
                TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D, 16));
        DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                .setDeviceResponse(encodedDeviceResponse)
                .setSessionTranscript(encodedSessionTranscript)
                .setEphemeralReaderKey(eReaderKey)
                .parse();
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
        Assert.assertEquals("1.0", dr.getVersion());
        List<DeviceResponseParser.Document> documents = dr.getDocuments();
        Assert.assertEquals(1, documents.size());

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        DeviceResponseParser.Document d = documents.get(0);

        // Validation of IssuerSigned should fail.
        Assert.assertFalse(d.getIssuerSignedAuthenticated());

        // Check we're still able to read the data, though...
        Assert.assertEquals(MDL_DOCTYPE, d.getDocType());
        Assert.assertEquals(0, d.getDeviceNamespaces().size());
        Assert.assertEquals(1, d.getIssuerNamespaces().size());
        Assert.assertEquals(MDL_NAMESPACE, d.getIssuerNamespaces().iterator().next());
        Assert.assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size());
        Assert.assertEquals("Doe",
                d.getIssuerEntryString(MDL_NAMESPACE, "family_name"));

        // DeviceSigned is fine.
        Assert.assertTrue(d.getDeviceSignedAuthenticated());
    }

}
