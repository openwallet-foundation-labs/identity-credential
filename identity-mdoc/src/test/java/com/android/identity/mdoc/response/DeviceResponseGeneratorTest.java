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

import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.Tagged;
import com.android.identity.cbor.Tstr;
import com.android.identity.cose.Cose;
import com.android.identity.cose.CoseLabel;
import com.android.identity.cose.CoseNumberLabel;
import com.android.identity.credential.AuthenticationKey;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialRequest;
import com.android.identity.credential.CredentialStore;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.credential.PendingAuthenticationKey;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.Certificate;
import com.android.identity.crypto.CertificateChain;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcCurve;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataParser;
import com.android.identity.mdoc.util.MdocUtil;
import com.android.identity.securearea.KeyPurpose;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.securearea.software.SoftwareCreateKeySettings;
import com.android.identity.securearea.software.SoftwareSecureArea;
import com.android.identity.storage.EphemeralStorageEngine;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Timestamp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.random.Random;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;

public class DeviceResponseGeneratorTest {
    private static final String TAG = "DeviceResponseGeneratorTest";

    StorageEngine mStorageEngine;

    SecureArea mSecureArea;

    SecureAreaRepository mSecureAreaRepository;

    static final String DOC_TYPE = "com.example.credential_xyz";
    private AuthenticationKey mAuthKey;
    private Credential mCredential;
    private Timestamp mTimeSigned;
    private Timestamp mTimeValidityBegin;
    private Timestamp mTimeValidityEnd;

    private EcPrivateKey mDocumentSignerKey;
    private Certificate mDocumentSignerCert;

    @Before
    public void setup() throws Exception {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        mStorageEngine = new EphemeralStorageEngine();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new SoftwareSecureArea(mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);

        provisionCredential();
    }

    // This isn't really used, we only use a single domain.
    private final String AUTH_KEY_DOMAIN = "domain";

    private void provisionCredential() throws Exception {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        // Create the credential...
        mCredential = credentialStore.createCredential(
                "testCredential");
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .build();
        mCredential.getApplicationData().setNameSpacedData("credentialData", nameSpacedData);

        Map<String, Map<String, byte[]>> overrides = new HashMap<>();
        Map<String, byte[]> overridesForNs1 = new HashMap<>();
        overridesForNs1.put("foo3", Cbor.encode(new Tstr("bar3_override")));
        overrides.put("ns1", overridesForNs1);

        Map<String, List<String>> exceptions = new HashMap<>();
        exceptions.put("ns1", Arrays.asList("foo3"));
        exceptions.put("ns2", Arrays.asList("bar2"));

        // Create an authentication key... make sure the authKey used supports both
        // mdoc ECDSA and MAC authentication.
        long nowMillis = (Calendar.getInstance().getTimeInMillis() / 1000) * 1000;
        mTimeSigned = Timestamp.ofEpochMilli(nowMillis);
        mTimeValidityBegin = Timestamp.ofEpochMilli(nowMillis + 3600 * 1000);
        mTimeValidityEnd = Timestamp.ofEpochMilli(nowMillis + 10 * 86400 * 1000);
        PendingAuthenticationKey pendingAuthKey =
                mCredential.createPendingAuthenticationKey(
                        AUTH_KEY_DOMAIN,
                        mSecureArea,
                        new SoftwareCreateKeySettings.Builder(new byte[0])
                                .setKeyPurposes(Set.of(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY))
                                .build(),
                        null);

        // Generate an MSO and issuer-signed data for this authentication key.
        MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator(
                "SHA-256",
                DOC_TYPE,
                pendingAuthKey.getAttestation().getCertificates().get(0).getPublicKey());
        msoGenerator.setValidityInfo(mTimeSigned, mTimeValidityBegin, mTimeValidityEnd, null);

        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                Random.Default,
                16,
                overrides);

        for (String nameSpaceName : issuerNameSpaces.keySet()) {
            Map<Long, byte[]> digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    Algorithm.SHA256);
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests);
        }

        Instant validFrom = Clock.System.INSTANCE.now();
        Instant validUntil = Instant.Companion.fromEpochMilliseconds(
                validFrom.toEpochMilliseconds() + 5L*365*24*60*60*1000);
        mDocumentSignerKey = Crypto.createEcPrivateKey(EcCurve.P256);
        mDocumentSignerCert = Crypto.createX509v3Certificate(
                mDocumentSignerKey.getPublicKey(),
                mDocumentSignerKey,
                Algorithm.ES256,
                "1",
                "CN=State Of Utopia",
                "CN=State Of Utopia",
                validFrom,
                validUntil,
                List.of());

        byte[] mso = msoGenerator.generate();
        byte[] taggedEncodedMso = Cbor.encode(new Tagged(24, new Bstr(mso)));

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        //
        // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
        //
        Map<CoseLabel, DataItem> protectedHeaders = Map.of(
                new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                DataItemExtensionsKt.getDataItem(Algorithm.ES256.getCoseAlgorithmIdentifier())
        );
        Map<CoseLabel, DataItem> unprotectedHeaders = Map.of(
                new CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                new CertificateChain(List.of(mDocumentSignerCert)).getDataItem()
        );
        byte[] encodedIssuerAuth = Cbor.encode(
                Cose.coseSign1Sign(
                        mDocumentSignerKey,
                        taggedEncodedMso,
                        true,
                        Algorithm.ES256,
                        protectedHeaders,
                        unprotectedHeaders
                ).getDataItem()
        );

        byte[] issuerProvidedAuthenticationData = new StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, exceptions),
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
                new CredentialRequest.DataElement("ns1", "foo1", false, false),
                new CredentialRequest.DataElement("ns1", "foo2", false, false),
                new CredentialRequest.DataElement("ns1", "foo3", false, false),
                new CredentialRequest.DataElement("ns2", "bar1", false, false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false, false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false, false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Cbor.encode(new Tstr("Doesn't matter"));

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getApplicationData().getNameSpacedData("credentialData"),
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
                                Algorithm.ES256)
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
        Assert.assertEquals(1, doc.getIssuerCertificateChain().getCertificates().size());
        Assert.assertEquals(mDocumentSignerCert, doc.getIssuerCertificateChain().getCertificates().get(0));

        Assert.assertEquals(DOC_TYPE, doc.getDocType());
        Assert.assertEquals(mTimeSigned, doc.getValidityInfoSigned());
        Assert.assertEquals(mTimeValidityBegin, doc.getValidityInfoValidFrom());
        Assert.assertEquals(mTimeValidityEnd, doc.getValidityInfoValidUntil());
        Assert.assertNull(doc.getValidityInfoExpectedUpdate());

        // Check DeviceSigned data
        Assert.assertEquals(0, doc.getDeviceNamespaces().size());
        // Check the key which signed DeviceSigned was the expected one.
        Assert.assertEquals(mAuthKey.getAttestation().getCertificates().get(0).getPublicKey(), doc.getDeviceKey());
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
        Assert.assertEquals("bar3_override", doc.getIssuerEntryString("ns1", "foo3"));
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
                new CredentialRequest.DataElement("ns1", "foo1", false, false),
                new CredentialRequest.DataElement("ns1", "foo2", false, false),
                new CredentialRequest.DataElement("ns1", "foo3", false, false),
                new CredentialRequest.DataElement("ns2", "bar1", false, false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false, false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false, false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Cbor.encode(new Tstr("Doesn't matter"));

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getApplicationData().getNameSpacedData("credentialData"),
                staticAuthData);

        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(EcCurve.P256);
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesMac(
                                new NameSpacedData.Builder().build(),
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                eReaderKey.getPublicKey())
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKey);
        DeviceResponseParser.DeviceResponse deviceResponse = parser.parse();
        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document doc = deviceResponse.getDocuments().get(0);
        Assert.assertTrue(doc.getDeviceSignedAuthenticated());
        Assert.assertFalse(doc.getDeviceSignedAuthenticatedViaSignature());
    }

    @Test
    public void testDeviceSigned() throws Exception {
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false, false),
                new CredentialRequest.DataElement("ns1", "foo2", false, false),
                new CredentialRequest.DataElement("ns1", "foo3", false, false),
                new CredentialRequest.DataElement("ns2", "bar1", false, false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false, false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false, false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Cbor.encode(new Tstr("Doesn't matter"));

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getApplicationData().getNameSpacedData("credentialData"),
                staticAuthData);

        // Check that DeviceSigned works.
        NameSpacedData deviceSignedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1_override")
                .putEntryString("ns3", "baz1", "bah1")
                .putEntryString("ns4", "baz2", "bah2")
                .putEntryString("ns4", "baz3", "bah3")
                .build();

        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(EcCurve.P256);
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setIssuerNamespaces(mergedIssuerNamespaces)
                        .setDeviceNamespacesSignature(
                                deviceSignedData,
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                Algorithm.ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKey);
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
        Assert.assertEquals("bar3_override", doc.getIssuerEntryString("ns1", "foo3"));
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
                new CredentialRequest.DataElement("ns1", "foo1", false, false),
                new CredentialRequest.DataElement("ns1", "foo2", false, false),
                new CredentialRequest.DataElement("ns1", "foo3", false, false),
                new CredentialRequest.DataElement("ns2", "bar1", false, false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false, false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false, false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);

        byte[] encodedSessionTranscript = Cbor.encode(new Tstr("Doesn't matter"));

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

        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(EcCurve.P256);
        DeviceResponseGenerator deviceResponseGenerator = new DeviceResponseGenerator(0);
        deviceResponseGenerator.addDocument(
                new DocumentGenerator(DOC_TYPE, staticAuthData.getIssuerAuth(), encodedSessionTranscript)
                        .setDeviceNamespacesSignature(
                                deviceSignedData,
                                mAuthKey.getSecureArea(),
                                mAuthKey.getAlias(),
                                null,
                                Algorithm.ES256)
                        .generate());
        byte[] encodedDeviceResponse = deviceResponseGenerator.generate();
        DeviceResponseParser parser = new DeviceResponseParser();
        parser.setDeviceResponse(encodedDeviceResponse);
        parser.setSessionTranscript(encodedSessionTranscript);
        parser.setEphemeralReaderKey(eReaderKey);
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

        CredentialRequest.DataElement ns1_foo2 = new CredentialRequest.DataElement("ns1", "foo2", false, false);
        ns1_foo2.setDoNotSend(true);
        List<CredentialRequest.DataElement> dataElements = Arrays.asList(
                new CredentialRequest.DataElement("ns1", "foo1", false, false),
                ns1_foo2,
                new CredentialRequest.DataElement("ns1", "foo3", false, false),
                new CredentialRequest.DataElement("ns2", "bar1", false, false),
                new CredentialRequest.DataElement("ns2", "does_not_exist", false, false),
                new CredentialRequest.DataElement("ns_does_not_exist", "boo", false, false)
        );
        CredentialRequest request = new CredentialRequest(dataElements);


        byte[] encodedSessionTranscript = Cbor.encode(new Tstr("Doesn't matter"));

        StaticAuthDataParser.StaticAuthData staticAuthData =
                new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                        .parse();

        Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                request,
                mCredential.getApplicationData().getNameSpacedData("credentialData"),
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
                                Algorithm.ES256)
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
        Assert.assertEquals(1, doc.getIssuerCertificateChain().getCertificates().size());
        Assert.assertEquals(mDocumentSignerCert, doc.getIssuerCertificateChain().getCertificates().get(0));

        Assert.assertEquals(DOC_TYPE, doc.getDocType());
        Assert.assertEquals(mTimeSigned, doc.getValidityInfoSigned());
        Assert.assertEquals(mTimeValidityBegin, doc.getValidityInfoValidFrom());
        Assert.assertEquals(mTimeValidityEnd, doc.getValidityInfoValidUntil());
        Assert.assertNull(doc.getValidityInfoExpectedUpdate());

        // Check DeviceSigned data
        Assert.assertEquals(0, doc.getDeviceNamespaces().size());
        // Check the key which signed DeviceSigned was the expected one.
        Assert.assertEquals(mAuthKey.getAttestation().getCertificates().get(0).getPublicKey(), doc.getDeviceKey());
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
        Assert.assertEquals("bar3_override", doc.getIssuerEntryString("ns1", "foo3"));
        Assert.assertEquals("ns2", doc.getIssuerNamespaces().get(1));
        Assert.assertEquals(1, doc.getIssuerEntryNames("ns2").size());
        Assert.assertEquals("foo1", doc.getIssuerEntryString("ns2", "bar1"));
    }

}