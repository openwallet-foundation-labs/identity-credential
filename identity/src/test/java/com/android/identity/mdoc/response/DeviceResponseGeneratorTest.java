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

package com.android.identity.mdoc.response;

import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialRequest;
import com.android.identity.credential.CredentialStore;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.internal.Util;
import com.android.identity.securearea.BouncyCastleSecureArea;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataParser;
import com.android.identity.mdoc.util.MdocUtil;
import com.android.identity.storage.EphemeralStorageEngine;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Timestamp;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DeviceResponseGeneratorTest {
    private static final String TAG = "DeviceResponseGeneratorTest";

    StorageEngine mStorageEngine;

    SecureArea mSecureArea;

    SecureAreaRepository mSecureAreaRepository;

    static final String DOC_TYPE = "com.example.credential_xyz";
    private Credential.AuthenticationKey mAuthKey;
    private Credential mCredential;
    private X509Certificate mIssuerCert;
    private Timestamp mTimeSigned;
    private Timestamp mTimeValidityBegin;
    private Timestamp mTimeValidityEnd;

    private static KeyPair generateIssuingAuthorityKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }

    private static X509Certificate getSelfSignedIssuerAuthorityCertificate(
            KeyPair issuerAuthorityKeyPair) throws Exception {
        X500Name issuer = new X500Name("CN=State Of Utopia");
        X500Name subject = new X500Name("CN=State Of Utopia Issuing Authority Signing Key");

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
                        issuerAuthorityKeyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .build(issuerAuthorityKeyPair.getPrivate());

        X509CertificateHolder certHolder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    @Before
    public void setup() throws Exception {
        mStorageEngine = new EphemeralStorageEngine();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new BouncyCastleSecureArea(mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);

        provisionCredential();
    }

    private void provisionCredential() throws Exception {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        // Create the credential...
        mCredential = credentialStore.createCredential(
                "testCredential",
                new BouncyCastleSecureArea.CreateKeySettings.Builder().build());
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .build();
        mCredential.setNameSpacedData(nameSpacedData);

        // Create an authentication key... make sure the authKey used supports both
        // mdoc ECDSA and MAC authentication.
        long nowMillis = (Calendar.getInstance().getTimeInMillis() / 1000) * 1000;
        mTimeSigned = Timestamp.ofEpochMilli(nowMillis);
        mTimeValidityBegin = Timestamp.ofEpochMilli(nowMillis + 3600 * 1000);
        mTimeValidityEnd = Timestamp.ofEpochMilli(nowMillis + 10 * 86400 * 1000);
        Credential.PendingAuthenticationKey pendingAuthKey =
                mCredential.createPendingAuthenticationKey(
                        new BouncyCastleSecureArea.CreateKeySettings.Builder()
                                .setKeyPurposes(SecureArea.KEY_PURPOSE_SIGN
                                        | SecureArea.KEY_PURPOSE_AGREE_KEY)
                                .build(),
                        null);

        // Generate an MSO and issuer-signed data for this authentication key.
        MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator(
                "SHA-256",
                DOC_TYPE,
                pendingAuthKey.getAttestation().get(0).getPublicKey());
        msoGenerator.setValidityInfo(mTimeSigned, mTimeValidityBegin, mTimeValidityEnd, null);

        Random deterministicRandomProvider = new Random(42);
        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                deterministicRandomProvider,
                16);

        for (String nameSpaceName : issuerNameSpaces.keySet()) {
            Map<Long, byte[]> digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256");
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests);
        }

        KeyPair issuerKeyPair = generateIssuingAuthorityKeyPair();
        mIssuerCert = getSelfSignedIssuerAuthorityCertificate(issuerKeyPair);

        byte[] mso = msoGenerator.generate();
        byte[] taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso));

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        //
        // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
        //
        ArrayList<X509Certificate> issuerCertChain = new ArrayList<>();
        issuerCertChain.add(mIssuerCert);
        byte[] encodedIssuerAuth = Util.cborEncode(Util.coseSign1Sign(issuerKeyPair.getPrivate(),
                "SHA256withECDSA", taggedEncodedMso,
                null,
                issuerCertChain));

        byte[] issuerProvidedAuthenticationData = new StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces),
                encodedIssuerAuth).generate();

        // Now that we have issuer-provided authentication data we certify the authentication key.
        mAuthKey = pendingAuthKey.certify(
                issuerProvidedAuthenticationData,
                mTimeValidityBegin,
                mTimeValidityEnd);
    }

    @Test
    public void testDocumentGeneratorEcdsa() throws Exception {

        // OK, now do the request... request a strict subset of the data in the credential
        // and also request data not in the credential.
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false),
                new CredentialRequest.DataElement("ns1", "foo2", false),
                new CredentialRequest.DataElement("ns1", "foo3", false),
                new CredentialRequest.DataElement("ns2", "bar1", false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Util.cborEncodeString("Doesn't matter");

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getNameSpacedData(),
                staticAuthData);

        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesSignature(
                                new NameSpacedData.Builder().build(),
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                SecureArea.ALGORITHM_ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();

        // To verify, parse the response...
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();

        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);

        // Check the MSO was properly signed.
        Assert.assertEquals(1, doc.getIssuerCertificateChain().size());
        Assert.assertEquals(mIssuerCert, doc.getIssuerCertificateChain().get(0));

        Assert.assertEquals(DOC_TYPE, doc.getDocType());
        Assert.assertEquals(mTimeSigned, doc.getValidityInfoSigned());
        Assert.assertEquals(mTimeValidityBegin, doc.getValidityInfoValidFrom());
        Assert.assertEquals(mTimeValidityEnd, doc.getValidityInfoValidUntil());
        Assert.assertNull(doc.getValidityInfoExpectedUpdate());

        // Check DeviceSigned data
        Assert.assertEquals(0, doc.getDeviceNamespaces().size());
        // Check the key which signed DeviceSigned was the expected one.
        Assert.assertEquals(mAuthKey.getAttestation().get(0).getPublicKey(), doc.getDeviceKey());
        // Check DeviceSigned was correctly authenticated.
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertTrue(doc.getDeviceSignedAuthenticatedViaSignature());

        // Check IssuerSigned data didn't have any digest failures (meaning all the hashes were correct).
        Assert.assertEquals(0, doc.getNumIssuerEntryDigestMatchFailures());
        // Check IssuerSigned data was correctly authenticated.
        Assert.assertTrue(doc.getIssuerSignedAuthenticated());

        // Check Issuer Signed data
        Assert.assertEquals(2, doc.getIssuerNamespaces().size());
        Assert.assertEquals("ns1", doc.getIssuerNamespaces().get(0));
        Assert.assertEquals(3, doc.getIssuerEntryNames("ns1").size());
        Assert.assertEquals("bar1", doc.getIssuerEntryString("ns1", "foo1"));
        Assert.assertEquals("bar2", doc.getIssuerEntryString("ns1", "foo2"));
        Assert.assertEquals("bar3", doc.getIssuerEntryString("ns1", "foo3"));
        Assert.assertEquals("ns2", doc.getIssuerNamespaces().get(1));
        Assert.assertEquals(1, doc.getIssuerEntryNames("ns2").size());
        Assert.assertEquals("foo1", doc.getIssuerEntryString("ns2", "bar1"));
    }

    @Test
    public void testDocumentGeneratorMac() throws Exception {
        // Also check that Mac authentication works. This requires creating an ephemeral
        // reader key... we generate a new response, parse it, and check that the
        // DeviceSigned part is as expected.

        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false),
                new CredentialRequest.DataElement("ns1", "foo2", false),
                new CredentialRequest.DataElement("ns1", "foo3", false),
                new CredentialRequest.DataElement("ns2", "bar1", false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Util.cborEncodeString("Doesn't matter");

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getNameSpacedData(),
                staticAuthData);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eReaderKeyPair = kpg.generateKeyPair();
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesMac(
                                new NameSpacedData.Builder().build(),
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                eReaderKeyPair.getPublic())
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKeyPair.getPrivate());
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();
        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertFalse(doc.getDeviceSignedAuthenticatedViaSignature());
    }

    @Test
    public void testDeviceSigned() throws Exception {
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false),
                new CredentialRequest.DataElement("ns1", "foo2", false),
                new CredentialRequest.DataElement("ns1", "foo3", false),
                new CredentialRequest.DataElement("ns2", "bar1", false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Util.cborEncodeString("Doesn't matter");

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getNameSpacedData(),
                staticAuthData);

        // Check that DeviceSigned works.
        NameSpacedData deviceSignedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1_override")
                .putEntryString("ns3", "baz1", "bah1")
                .putEntryString("ns4", "baz2", "bah2")
                .putEntryString("ns4", "baz3", "bah3")
                .build();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eReaderKeyPair = kpg.generateKeyPair();
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesSignature(
                                deviceSignedData,
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                SecureArea.ALGORITHM_ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKeyPair.getPrivate());
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();
        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertTrue(doc.getDeviceSignedAuthenticatedViaSignature());
        // Check all Issuer Signed data is still there
        Assert.assertEquals(2, doc.getIssuerNamespaces().size());
        Assert.assertEquals("ns1", doc.getIssuerNamespaces().get(0));
        Assert.assertEquals(3, doc.getIssuerEntryNames("ns1").size());
        Assert.assertEquals("bar1", doc.getIssuerEntryString("ns1", "foo1"));
        Assert.assertEquals("bar2", doc.getIssuerEntryString("ns1", "foo2"));
        Assert.assertEquals("bar3", doc.getIssuerEntryString("ns1", "foo3"));
        Assert.assertEquals("ns2", doc.getIssuerNamespaces().get(1));
        Assert.assertEquals(1, doc.getIssuerEntryNames("ns2").size());
        Assert.assertEquals("foo1", doc.getIssuerEntryString("ns2", "bar1"));
        // Check all Device Signed data is there
        Assert.assertEquals(3, doc.getDeviceNamespaces().size());
        Assert.assertEquals("ns1", doc.getDeviceNamespaces().get(0));
        Assert.assertEquals(1, doc.getDeviceEntryNames("ns1").size());
        Assert.assertEquals("bar1_override", doc.getDeviceEntryString("ns1", "foo1"));
        Assert.assertEquals("ns3", doc.getDeviceNamespaces().get(1));
        Assert.assertEquals(1, doc.getDeviceEntryNames("ns3").size());
        Assert.assertEquals("bah1", doc.getDeviceEntryString("ns3", "baz1"));
        Assert.assertEquals("ns4", doc.getDeviceNamespaces().get(2));
        Assert.assertEquals(2, doc.getDeviceEntryNames("ns4").size());
        Assert.assertEquals("bah2", doc.getDeviceEntryString("ns4", "baz2"));
        Assert.assertEquals("bah3", doc.getDeviceEntryString("ns4", "baz3"));
    }

    @Test
    public void testDeviceSignedOnly() throws Exception {
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false),
                new CredentialRequest.DataElement("ns1", "foo2", false),
                new CredentialRequest.DataElement("ns1", "foo3", false),
                new CredentialRequest.DataElement("ns2", "bar1", false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Util.cborEncodeString("Doesn't matter");

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        // Check that DeviceSigned works.
        NameSpacedData deviceSignedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1_override")
                .putEntryString("ns3", "baz1", "bah1")
                .putEntryString("ns4", "baz2", "bah2")
                .putEntryString("ns4", "baz3", "bah3")
                .build();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eReaderKeyPair = kpg.generateKeyPair();
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setDeviceNamespacesSignature(
                                deviceSignedData,
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                SecureArea.ALGORITHM_ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKeyPair.getPrivate());
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();
        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertTrue(doc.getDeviceSignedAuthenticatedViaSignature());
        // Check there is no Issuer Signed data
        Assert.assertEquals(0, doc.getIssuerNamespaces().size());
        // Check all Device Signed data is there
        Assert.assertEquals(3, doc.getDeviceNamespaces().size());
        Assert.assertEquals("ns1", doc.getDeviceNamespaces().get(0));
        Assert.assertEquals(1, doc.getDeviceEntryNames("ns1").size());
        Assert.assertEquals("bar1_override", doc.getDeviceEntryString("ns1", "foo1"));
        Assert.assertEquals("ns3", doc.getDeviceNamespaces().get(1));
        Assert.assertEquals(1, doc.getDeviceEntryNames("ns3").size());
        Assert.assertEquals("bah1", doc.getDeviceEntryString("ns3", "baz1"));
        Assert.assertEquals("ns4", doc.getDeviceNamespaces().get(2));
        Assert.assertEquals(2, doc.getDeviceEntryNames("ns4").size());
        Assert.assertEquals("bah2", doc.getDeviceEntryString("ns4", "baz2"));
        Assert.assertEquals("bah3", doc.getDeviceEntryString("ns4", "baz3"));
    }

    @Test
    public void testDocumentGeneratorDoNotSend() throws Exception {

        CredentialRequest.DataElement ns1_foo2 = new CredentialRequest.DataElement("ns1", "foo2", false);
        ns1_foo2.setDoNotSend(true);
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false),
                ns1_foo2,
                new CredentialRequest.DataElement("ns1", "foo3", false),
                new CredentialRequest.DataElement("ns2", "bar1", false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);


        byte[] encodedSessionTranscript = Util.cborEncodeString("Doesn't matter");

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getNameSpacedData(),
                staticAuthData);

        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesSignature(
                                new NameSpacedData.Builder().build(),
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                SecureArea.ALGORITHM_ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();

        // To verify, parse the response...
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();

        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);

        // Check the MSO was properly signed.
        Assert.assertEquals(1, doc.getIssuerCertificateChain().size());
        Assert.assertEquals(mIssuerCert, doc.getIssuerCertificateChain().get(0));

        Assert.assertEquals(DOC_TYPE, doc.getDocType());
        Assert.assertEquals(mTimeSigned, doc.getValidityInfoSigned());
        Assert.assertEquals(mTimeValidityBegin, doc.getValidityInfoValidFrom());
        Assert.assertEquals(mTimeValidityEnd, doc.getValidityInfoValidUntil());
        Assert.assertNull(doc.getValidityInfoExpectedUpdate());

        // Check DeviceSigned data
        Assert.assertEquals(0, doc.getDeviceNamespaces().size());
        // Check the key which signed DeviceSigned was the expected one.
        Assert.assertEquals(mAuthKey.getAttestation().get(0).getPublicKey(), doc.getDeviceKey());
        // Check DeviceSigned was correctly authenticated.
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertTrue(doc.getDeviceSignedAuthenticatedViaSignature());

        // Check IssuerSigned data didn't have any digest failures (meaning all the hashes were correct).
        Assert.assertEquals(0, doc.getNumIssuerEntryDigestMatchFailures());
        // Check IssuerSigned data was correctly authenticated.
        Assert.assertTrue(doc.getIssuerSignedAuthenticated());

        // Check Issuer Signed data
        Assert.assertEquals(2, doc.getIssuerNamespaces().size());
        Assert.assertEquals("ns1", doc.getIssuerNamespaces().get(0));
        Assert.assertEquals(2, doc.getIssuerEntryNames("ns1").size());
        Assert.assertEquals("bar1", doc.getIssuerEntryString("ns1", "foo1"));
        // Note: "ns1", "foo2" is not returned b/c it was marked as DoNotSend() above
        Assert.assertEquals("bar3", doc.getIssuerEntryString("ns1", "foo3"));
        Assert.assertEquals("ns2", doc.getIssuerNamespaces().get(1));
        Assert.assertEquals(1, doc.getIssuerEntryNames("ns2").size());
        Assert.assertEquals("foo1", doc.getIssuerEntryString("ns2", "bar1"));
    }

}