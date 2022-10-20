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

import android.security.keystore.KeyProperties;

import androidx.test.filters.SmallTest;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceRequestParserTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";

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

        List<DeviceRequestParser.DocumentRequest> docRequests = request.getDocumentRequests();
        Assert.assertEquals(1, docRequests.size());
        DeviceRequestParser.DocumentRequest dr = docRequests.iterator().next();

        Assert.assertEquals(MDL_DOCTYPE, dr.getDocType());
        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST),
                dr.getItemsRequest());

        List<X509Certificate> readerCertChain = dr.getReaderCertificateChain();
        Assert.assertEquals(1, readerCertChain.size());
        X509Certificate readerCert = readerCertChain.iterator().next();
        try {
            Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_CERT),
                    readerCert.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new AssertionError(e);
        }

        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_AUTH),
                dr.getReaderAuth());

        Assert.assertTrue(dr.getReaderAuthenticated());

        Assert.assertArrayEquals(new String[]{MDL_NAMESPACE}, dr.getNamespaces().toArray());
        Assert.assertEquals(6, dr.getEntryNames(MDL_NAMESPACE).size());
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "family_name"));
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "document_number"));
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "driving_privileges"));
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "issue_date"));
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "expiry_date"));
        Assert.assertFalse(dr.getIntentToRetain(MDL_NAMESPACE, "portrait"));
        try {
            dr.getEntryNames("ns-was-not-requested");
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            dr.getIntentToRetain("ns-was-not-requested", "elem-was-not-requested");
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            dr.getIntentToRetain(MDL_NAMESPACE, "elem-was-not-requested");
            throw new AssertionError("Expected IllegalArgumentException");
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

        List<DeviceRequestParser.DocumentRequest> docRequests = request.getDocumentRequests();
        Assert.assertEquals(1, docRequests.size());
        DeviceRequestParser.DocumentRequest dr = docRequests.iterator().next();

        Assert.assertEquals(MDL_DOCTYPE, dr.getDocType());
        Assert.assertArrayEquals(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST),
                dr.getItemsRequest());

        List<X509Certificate> readerCertChain = dr.getReaderCertificateChain();
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

    void testDeviceRequestParserReaderAuthHelper(
            String curveName, String algorithm) throws Exception {
        byte[] encodedSessionTranscript = Util.cborEncodeBytestring(new byte[]{0x01, 0x02});

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(MDL_NAMESPACE, mdlNsItems);

        BouncyCastleProvider bcProvider = new BouncyCastleProvider();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, bcProvider);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(curveName);
        kpg.initialize(ecSpec);
        KeyPair readerKeyPair = kpg.generateKeyPair();

        kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair trustPointKeyPair = kpg.generateKeyPair();

        X500Name issuer = new X500Name("CN=Some Reader Authority");
        X500Name subject = new X500Name("CN=Some Reader Key");

        // Valid from now to five years from now.
        Date now = new Date();
        final long kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000;
        Date expirationDate = new Date(now.getTime() + 5 * kMilliSecsInOneYear);
        BigInteger serial = new BigInteger("42");
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(issuer,
                        serial,
                        now,
                        expirationDate,
                        subject,
                        readerKeyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .build(trustPointKeyPair.getPrivate());
        byte[] encodedCert = builder.build(signer).getEncoded();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedCert);
        X509Certificate readerCert = (X509Certificate) cf.generateCertificate(bais);

        ArrayList<X509Certificate> readerCertChain = new ArrayList<>();
        readerCertChain.add(readerCert);

        Map<String, byte[]> mdlRequestInfo = new HashMap<>();
        mdlRequestInfo.put("foo", Util.cborEncodeString("bar"));
        mdlRequestInfo.put("bar", Util.cborEncodeNumber(42));

        Signature signature = Signature.getInstance(algorithm, bcProvider);
        signature.initSign(readerKeyPair.getPrivate());

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .setSessionTranscript(encodedSessionTranscript)
                .addDocumentRequest(MDL_DOCTYPE,
                        mdlItemsToRequest,
                        mdlRequestInfo,
                        signature,
                        readerCertChain)
                .generate();

        DeviceRequestParser.DeviceRequest deviceRequest = new DeviceRequestParser()
                .setSessionTranscript(encodedSessionTranscript)
                .setDeviceRequest(encodedDeviceRequest)
                .parse();
        Assert.assertEquals("1.0", deviceRequest.getVersion());
        List<DeviceRequestParser.DocumentRequest> documentRequests =
                deviceRequest.getDocumentRequests();
        Assert.assertTrue(documentRequests.get(0).getReaderAuthenticated());
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_P256() throws Exception {
        testDeviceRequestParserReaderAuthHelper("secp256r1", "SHA256withECDSA");
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_P384() throws Exception {
        testDeviceRequestParserReaderAuthHelper("secp384r1", "SHA384withECDSA");
    }

    /* TODO: investigate why this test is flaky */
    @Ignore
    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_P521() throws Exception {
        testDeviceRequestParserReaderAuthHelper("secp521r1", "SHA512withECDSA");
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_brainpoolP256r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper("brainpoolP256r1", "SHA256withECDSA");
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_brainpoolP384r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper("brainpoolP384r1", "SHA384withECDSA");
    }

    @Test
    @SmallTest
    public void testDeviceRequestParserReaderAuth_brainpoolP512r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper("brainpoolP512r1", "SHA512withECDSA");
    }

    // TODO: add tests for Curve25519 and Curve448 curves.

    // TODO: Have a request signed by an unsupported curve and make sure DeviceRequestParser
    //   fails gracefully.. that is, should successfully parse the request message but the
    //   getReaderAuthenticated() method should return false.
    //

}
