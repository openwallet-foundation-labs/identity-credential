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

import android.security.keystore.KeyProperties;

import androidx.test.filters.SmallTest;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

public class DeviceRequestGeneratorTest {
    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";

    private static final String MVR_DOCTYPE = "org.iso.18013.7.1.mVR";
    private static final String MVR_NAMESPACE = "org.iso.18013.7.1";

    private KeyPair generateReaderKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime256v1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }

    private X509Certificate getSelfSignedReaderCertificate(
            KeyPair readerKeyPair) throws Exception {
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
                .build(readerKeyPair.getPrivate());
        byte[] encodedCert = builder.build(signer).getEncoded();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedCert);
        X509Certificate result = (X509Certificate) cf.generateCertificate(bais);
        return result;
    }

    @Test
    @SmallTest
    public void testDeviceRequestBuilder() throws Exception {
        byte[] encodedSessionTranscript = Util.cborEncodeBytestring(new byte[] {0x01, 0x02});

        Map<String, Map<String, Boolean>> mdlItemsToRequest = Map.of(
                MDL_NAMESPACE, Map.of("family_name", true, "portrait", false),
                AAMVA_NAMESPACE, Map.of("real_id", false));
        Map<String, Map<String, Boolean>> mvrItemsToRequest = Map.of(
                MVR_NAMESPACE, Map.of("vehicle_number", true));

        KeyPair readerKeyPair = generateReaderKeyPair();
        ArrayList<X509Certificate> readerCertChain = new ArrayList<>();
        readerCertChain.add(getSelfSignedReaderCertificate(readerKeyPair));

        Map<String, byte[]> mdlRequestInfo = Map.of(
                "foo", Util.cborEncodeString("bar"),
                "bar", Util.cborEncodeNumber(42));

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .setSessionTranscript(encodedSessionTranscript)
                .addDocumentRequest(MDL_DOCTYPE,
                        mdlItemsToRequest,
                        mdlRequestInfo,
                        readerKeyPair.getPrivate(),
                        readerCertChain)
                .addDocumentRequest(MVR_DOCTYPE,
                        mvrItemsToRequest,
                        null,
                        readerKeyPair.getPrivate(),
                        readerCertChain)
                .generate();

        DeviceRequestParser.DeviceRequest deviceRequest = new DeviceRequestParser()
                .setSessionTranscript(encodedSessionTranscript)
                .setDeviceRequest(encodedDeviceRequest)
                .parse();
        Assert.assertEquals("1.0", deviceRequest.getVersion());
        Collection<DeviceRequestParser.DocumentRequest> documentRequests =
                deviceRequest.getDocRequests();
        Assert.assertEquals(2, documentRequests.size());

        Iterator<DeviceRequestParser.DocumentRequest> it =
                deviceRequest.getDocRequests().iterator();

        DeviceRequestParser.DocumentRequest docRequest = it.next();
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
        Assert.assertEquals(1, docRequest.getReaderCertificateChain().size());
        Assert.assertArrayEquals(readerCertChain.iterator().next().getEncoded(),
                docRequest.getReaderCertificateChain().iterator().next().getEncoded());
        Map<String, byte[]> requestInfo = docRequest.getRequestInfo();
        Assert.assertNotNull(requestInfo);
        Assert.assertEquals(2, requestInfo.keySet().size());
        Assert.assertArrayEquals(Util.cborEncodeString("bar"), requestInfo.get("foo"));
        Assert.assertArrayEquals(Util.cborEncodeNumber(42), requestInfo.get("bar"));

        docRequest = it.next();
        Assert.assertEquals(MVR_DOCTYPE, docRequest.getDocType());
        Assert.assertEquals(1, docRequest.getNamespaces().size());
        Assert.assertEquals(1, docRequest.getEntryNames(MVR_NAMESPACE).size());
        Assert.assertTrue(docRequest.getIntentToRetain(MVR_NAMESPACE, "vehicle_number"));
        Assert.assertEquals(1, docRequest.getReaderCertificateChain().size());
        Assert.assertArrayEquals(readerCertChain.iterator().next().getEncoded(),
                docRequest.getReaderCertificateChain().iterator().next().getEncoded());
        requestInfo = docRequest.getRequestInfo();
        Assert.assertNull(requestInfo);
    }

}
