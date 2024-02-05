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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;

// TODO: Add test which generates the exact bytes of {@link
//  TestVectors#ISO_18013_5_ANNEX_D_DEVICE_REQUEST}.
//
@RunWith(JUnit4.class)
public class DeviceRequestGeneratorTest {
    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";

    private static final String MVR_DOCTYPE = "org.iso.18013.7.1.mVR";
    private static final String MVR_NAMESPACE = "org.iso.18013.7.1";

    @Before
    public void setUp() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @Test
    public void testDeviceRequestBuilder() throws Exception {
        byte[] encodedSessionTranscript = Cbor.encode(new Bstr(new byte[] {0x01, 0x02}));

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(MDL_NAMESPACE, mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(AAMVA_NAMESPACE, aamvaNsItems);

        Map<String, Map<String, Boolean>> mvrItemsToRequest = new HashMap<>();
        Map<String, Boolean> mvrNsItems = new HashMap<>();
        mvrNsItems.put("vehicle_number", true);
        mvrItemsToRequest.put(MVR_NAMESPACE, mvrNsItems);

        EcPrivateKey readerKey = Crypto.createEcPrivateKey(EcCurve.P256);
        Instant validFrom = Clock.System.INSTANCE.now();
        Instant validUntil = Instant.Companion.fromEpochMilliseconds(
                validFrom.toEpochMilliseconds() + 30L*24*60*60*1000);
        Certificate readerCert = Crypto.createX509v3Certificate(
                readerKey.getPublicKey(),
                readerKey,
                Algorithm.ES256,
                "1",
                "CN=Test Key",
                "CN=Test Key",
                validFrom,
                validUntil,
                List.of()
        );
        CertificateChain readerCertChain = new CertificateChain(List.of(readerCert));

        Map<String, byte[]> mdlRequestInfo = new HashMap<>();
        mdlRequestInfo.put("foo", Cbor.encode(new Tstr("bar")));
        mdlRequestInfo.put("bar", Cbor.encode(DataItemExtensionsKt.getDataItem(42)));

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .setSessionTranscript(encodedSessionTranscript)
                .addDocumentRequest(MDL_DOCTYPE,
                        mdlItemsToRequest,
                        mdlRequestInfo,
                        readerKey,
                        readerKey.getCurve().getDefaultSigningAlgorithm(),
                        readerCertChain)
                .addDocumentRequest(MVR_DOCTYPE,
                        mvrItemsToRequest,
                        null,
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
        Assert.assertEquals(2, documentRequests.size());

        Iterator<DeviceRequestParser.DocumentRequest> it =
                deviceRequest.getDocumentRequests().iterator();

        DeviceRequestParser.DocumentRequest docRequest = it.next();
        Assert.assertTrue(docRequest.getReaderAuthenticated());
        Assert.assertEquals(MDL_DOCTYPE, docRequest.getDocType());
        Assert.assertEquals(2, docRequest.getNamespaces().size());
        Assert.assertEquals(2, docRequest.getEntryNames(MDL_NAMESPACE).size());
        Assert.assertTrue(docRequest.getIntentToRetain(MDL_NAMESPACE, "family_name"));
        Assert.assertFalse(docRequest.getIntentToRetain(MDL_NAMESPACE, "portrait"));
        try {
            docRequest.getIntentToRetain(MDL_NAMESPACE, "non-existent");
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
        Assert.assertEquals(1, docRequest.getEntryNames(AAMVA_NAMESPACE).size());
        Assert.assertFalse(docRequest.getIntentToRetain(AAMVA_NAMESPACE, "real_id"));
        try {
            docRequest.getIntentToRetain("non-existent", "non-existent");
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
        try {
            docRequest.getEntryNames("non-existent");
            Assert.fail();
        } catch (IllegalArgumentException expected) { }
        Assert.assertEquals(1, docRequest.getReaderCertificateChain().getCertificates().size());
        Assert.assertEquals(readerCertChain, docRequest.getReaderCertificateChain());
        Map<String, byte[]> requestInfo = docRequest.getRequestInfo();
        Assert.assertNotNull(requestInfo);
        Assert.assertEquals(2, requestInfo.keySet().size());
        Assert.assertArrayEquals(Cbor.encode(new Tstr("bar")), requestInfo.get("foo"));
        Assert.assertArrayEquals(Cbor.encode(DataItemExtensionsKt.getDataItem(42)), requestInfo.get("bar"));

        docRequest = it.next();
        Assert.assertTrue(docRequest.getReaderAuthenticated());
        Assert.assertEquals(MVR_DOCTYPE, docRequest.getDocType());
        Assert.assertEquals(1, docRequest.getNamespaces().size());
        Assert.assertEquals(1, docRequest.getEntryNames(MVR_NAMESPACE).size());
        Assert.assertTrue(docRequest.getIntentToRetain(MVR_NAMESPACE, "vehicle_number"));
        Assert.assertEquals(1, docRequest.getReaderCertificateChain().getCertificates().size());
        Assert.assertEquals(readerCertChain, docRequest.getReaderCertificateChain());
        Assert.assertEquals(0, docRequest.getRequestInfo().size());
    }

}
