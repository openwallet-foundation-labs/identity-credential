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

package com.android.identity.mdoc.request;

import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.Tstr;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.Certificate;
import com.android.identity.crypto.CertificateChain;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcCurve;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.TestVectors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;

@RunWith(JUnit4.class)
public class DeviceRequestParserTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";

    @Before
    public void setUp() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @Test
    public void testDeviceRequestParserWithVectors() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript =
                Cbor.encode(Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor());

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

        CertificateChain readerCertChain = dr.getReaderCertificateChain();
        Assert.assertEquals(1, readerCertChain.getCertificates().size());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_CERT),
                readerCertChain.getCertificates().get(0).getEncodedCertificate()
        );

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
    public void testDeviceRequestParserWithVectorsMalformedReaderSignature() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript =
                Cbor.encode(Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor());

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

        CertificateChain readerCertChain = dr.getReaderCertificateChain();
        Assert.assertEquals(1, readerCertChain.getCertificates().size());
        Assert.assertArrayEquals(
                Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_READER_CERT),
                readerCertChain.getCertificates().get(0).getEncodedCertificate()
        );

        Assert.assertFalse(dr.getReaderAuthenticated());
    }

    void testDeviceRequestParserReaderAuthHelper(EcCurve curve) {
        byte[] encodedSessionTranscript = Cbor.encode(new Bstr(new byte[]{0x01, 0x02}));

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(MDL_NAMESPACE, mdlNsItems);

        EcPrivateKey readerKey = Crypto.createEcPrivateKey(curve);
        EcPrivateKey trustPoint = Crypto.createEcPrivateKey(EcCurve.P256);
        Instant validFrom = Clock.System.INSTANCE.now();
        Instant validUntil = Instant.Companion.fromEpochMilliseconds(
                validFrom.toEpochMilliseconds() + 5L*365*24*60*60*1000);
        Certificate certificate = Crypto.createX509v3Certificate(
                readerKey.getPublicKey(),
                trustPoint,
                null,
                Algorithm.ES256,
                "42",
                "CN=Some Reader Key",
                "CN=Some Reader Authority",
                validFrom,
                validUntil,
                Set.of(),
                List.of()
        );
        CertificateChain readerCertChain = new CertificateChain(List.of(certificate));

        Map<String, byte[]> mdlRequestInfo = new HashMap<>();
        mdlRequestInfo.put("foo", Cbor.encode(new Tstr("bar")));
        mdlRequestInfo.put("bar", Cbor.encode(DataItemExtensionsKt.getToDataItem(42)));

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .setSessionTranscript(encodedSessionTranscript)
                .addDocumentRequest(MDL_DOCTYPE,
                        mdlItemsToRequest,
                        mdlRequestInfo,
                        readerKey,
                        readerKey.getCurve().getDefaultSigningAlgorithm(),
                        readerCertChain)
                .generate();

        DeviceRequestParser.DeviceRequest deviceRequest = new DeviceRequestParser()
                .setSessionTranscript(encodedSessionTranscript)
                .setDeviceRequest(encodedDeviceRequest)
                .parse();
        Assert.assertEquals("1.0", deviceRequest.getVersion());
        List<DeviceRequestParser.DocumentRequest> documentRequests =
                deviceRequest.getDocumentRequests();
        //Assert.assertTrue(documentRequests.get(0).getReaderAuthenticated());
    }

    @Test
    public void testDeviceRequestParserReaderAuth_P256() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P256);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_P384() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P384);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_P521() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P521);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_brainpoolP256r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP256R1);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_brainpoolP320r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP320R1);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_brainpoolP384r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP384R1);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_brainpoolP512r1() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP512R1);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_Ed25519() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.ED25519);
    }

    @Test
    public void testDeviceRequestParserReaderAuth_Ed448() throws Exception {
        testDeviceRequestParserReaderAuthHelper(EcCurve.ED448);
    }

    // TODO: Have a request signed by an unsupported curve and make sure DeviceRequestParser
    //   fails gracefully.. that is, should successfully parse the request message but the
    //   getReaderAuthenticated() method should return false.
    //

}
