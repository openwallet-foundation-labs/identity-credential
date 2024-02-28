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

package com.android.identity.android.mdoc.deviceretrieval;

import android.content.Context;
import android.os.ConditionVariable;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.identity.android.mdoc.engagement.QrEngagementHelper;
import com.android.identity.android.mdoc.transport.DataTransport;
import com.android.identity.android.mdoc.transport.DataTransportOptions;
import com.android.identity.android.mdoc.transport.DataTransportTcp;
import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborArray;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.Simple;
import com.android.identity.cbor.Tagged;
import com.android.identity.cose.Cose;
import com.android.identity.cose.CoseLabel;
import com.android.identity.cose.CoseNumberLabel;
import com.android.identity.credential.AuthenticationKey;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialStore;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.credential.PendingAuthenticationKey;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.Certificate;
import com.android.identity.crypto.CertificateChain;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcCurve;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataParser;
import com.android.identity.mdoc.request.DeviceRequestGenerator;
import com.android.identity.mdoc.request.DeviceRequestParser;
import com.android.identity.mdoc.response.DeviceResponseGenerator;
import com.android.identity.mdoc.response.DeviceResponseParser;
import com.android.identity.mdoc.response.DocumentGenerator;
import com.android.identity.mdoc.sessionencryption.SessionEncryption;
import com.android.identity.mdoc.util.MdocUtil;
import com.android.identity.securearea.KeyLockedException;
import com.android.identity.securearea.KeyPurpose;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.securearea.software.SoftwareCreateKeySettings;
import com.android.identity.securearea.software.SoftwareSecureArea;
import com.android.identity.storage.EphemeralStorageEngine;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Constants;
import com.android.identity.util.Timestamp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.Security;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import kotlin.Pair;
import kotlin.random.Random;
import kotlinx.datetime.Clock;
import kotlinx.datetime.Instant;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class DeviceRetrievalHelperTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";

    private StorageEngine mStorageEngine;
    private SecureArea mSecureArea;
    private SecureAreaRepository mSecureAreaRepository;
    private Credential mCredential;
    private AuthenticationKey mAuthKey;
    private Timestamp mTimeSigned;
    private Timestamp mTimeValidityBegin;
    private Timestamp mTimeValidityEnd;

    private EcPrivateKey mDocumentSignerKey;
    private Certificate mDocumentSignerCert;

    private final String AUTH_KEY_DOMAIN = "domain";

    private void provisionCredential() {
        mStorageEngine = new EphemeralStorageEngine();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new SoftwareSecureArea(mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);

        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        // Create the credential...
        mCredential = credentialStore.createCredential(
                "testCredential");
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString(MDL_NAMESPACE, "given_name", "Erika")
                .putEntryString(MDL_NAMESPACE, "family_name", "Mustermann")
                .putEntryBoolean(AAMVA_NAMESPACE, "real_id", true)
                .build();
        mCredential.getApplicationData().setNameSpacedData("credentialData", nameSpacedData);

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
                MDL_DOCTYPE,
                pendingAuthKey.getAttestation().getCertificates().get(0).getPublicKey());
        msoGenerator.setValidityInfo(mTimeSigned, mTimeValidityBegin, mTimeValidityEnd, null);

        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                Random.Default,
                16,
                null);

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
                null,
                Algorithm.ES256,
                "1",
                "CN=State Of Utopia",
                "CN=State Of Utopia",
                validFrom,
                validUntil,
                Set.of(),
                List.of());

        byte[] mso = msoGenerator.generate();
        byte[] taggedEncodedMso = Cbor.encode(new Tagged(24, new Bstr(mso)));

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        //
        // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
        //
        Map<CoseLabel, DataItem> protectedHeaders = Map.of(
                new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                DataItemExtensionsKt.getToDataItem(Algorithm.ES256.getCoseAlgorithmIdentifier())
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
                ).getToDataItem()
        );

        byte[] issuerProvidedAuthenticationData = new StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, null),
                encodedIssuerAuth).generate();

        // Now that we have issuer-provided authentication data we certify the authentication key.
        mAuthKey = pendingAuthKey.certify(
                issuerProvidedAuthenticationData,
                mTimeValidityBegin,
                mTimeValidityEnd);
    }

    @Before
    public void setUp() {
        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in Android.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());

        provisionCredential();
    }


    @Test
    public void testPresentation() {
        Context context = InstrumentationRegistry.getTargetContext();

        ConditionVariable condVarDeviceConnected = new ConditionVariable();
        ConditionVariable condVarDeviceDisconnected = new ConditionVariable();
        ConditionVariable condVarDeviceEngagementReady = new ConditionVariable();

        // TODO: use loopback instead of TCP transport
        DataTransportTcp proverTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC,
                new DataTransportOptions.Builder().build());
        DataTransportTcp verifierTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC_READER,
                new DataTransportOptions.Builder().build());

        Executor executor = Executors.newSingleThreadExecutor();

        QrEngagementHelper.Listener qrHelperListener =
                new QrEngagementHelper.Listener() {
                    @Override
                    public void onDeviceEngagementReady() {
                        condVarDeviceEngagementReady.open();
                    }

                    @Override
                    public void onDeviceConnecting() {
                    }

                    @Override
                    public void onDeviceConnected(DataTransport transport) {
                        condVarDeviceConnected.open();
                        Assert.assertEquals(proverTransport, transport);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        throw new AssertionError(error);
                    }
                };

        EcPrivateKey eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256);
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                eDeviceKey.getPublicKey(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(List.of(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(EcCurve.P256);
        byte[] encodedEReaderKeyPub = Cbor.encode(eReaderKey.getPublicKey().toCoseKey(Map.of()).getToDataItem());
        byte[] encodedSessionTranscript = Cbor.encode(
                CborArray.Companion.builder()
                        .addTaggedEncodedCbor(encodedDeviceEngagement)
                        .addTaggedEncodedCbor(encodedEReaderKeyPub)
                        .add(Simple.Companion.getNULL())
                        .end()
                        .build());
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.Role.MDOC_READER,
                eReaderKey,
                eDeviceKey.getPublicKey(),
                encodedSessionTranscript);

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("given_name", false);
        mdlItemsToRequest.put(MDL_NAMESPACE, mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(AAMVA_NAMESPACE, aamvaNsItems);

        byte[] encodedDeviceRequest = new DeviceRequestGenerator(encodedSessionTranscript)
                .addDocumentRequest(
                        MDL_DOCTYPE,
                        mdlItemsToRequest,
                        null,
                        null,
                        Algorithm.UNSET,
                        null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessage(encodedDeviceRequest, null);
        verifierTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnecting() {
            }

            @Override
            public void onConnected() {
            }

            @Override
            public void onDisconnected() {
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onMessageReceived() {
                byte[] data = verifierTransport.getMessage();
                Pair<byte[], Long> decryptedMessage = seReader.decryptMessage(data);
                Assert.assertNull(decryptedMessage.getSecond());

                DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser(
                        decryptedMessage.getFirst(),
                        encodedSessionTranscript)
                        .setEphemeralReaderKey(eReaderKey)
                        .parse();
                Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
                Assert.assertEquals("1.0", dr.getVersion());
                Collection<DeviceResponseParser.Document> documents = dr.getDocuments();
                Assert.assertEquals(1, documents.size());
                DeviceResponseParser.Document d = documents.iterator().next();
                Assert.assertEquals(MDL_DOCTYPE, d.getDocType());
                Assert.assertEquals(0, d.getDeviceNamespaces().size());
                Assert.assertEquals(2, d.getIssuerNamespaces().size());
                Assert.assertEquals(2, d.getIssuerEntryNames(MDL_NAMESPACE).size());
                Assert.assertEquals("Erika",
                        d.getIssuerEntryString(MDL_NAMESPACE, "given_name"));
                Assert.assertEquals("Mustermann",
                        d.getIssuerEntryString(MDL_NAMESPACE, "family_name"));
                Assert.assertEquals(1, d.getIssuerEntryNames(AAMVA_NAMESPACE).size());
                Assert.assertTrue(d.getIssuerEntryBoolean(AAMVA_NAMESPACE, "real_id"));

                // Send a close message (status 20 is "session termination")
                verifierTransport.sendMessage(
                        seReader.encryptMessage(null,
                                Constants.SESSION_DATA_STATUS_SESSION_TERMINATION));
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        final DeviceRetrievalHelper[] presentation = {null};
        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {

                    @Override
                    public void onEReaderKeyReceived(@NonNull EcPublicKey eReaderKey) {
                    }

                    @Override
                    public void onDeviceRequest(@NonNull byte[] deviceRequestBytes) {
                        DeviceRequestParser parser = new DeviceRequestParser(
                                deviceRequestBytes,
                                presentation[0].getSessionTranscript());
                        DeviceRequestParser.DeviceRequest deviceRequest = parser.parse();

                        Collection<DeviceRequestParser.DocumentRequest> docRequests =
                                deviceRequest.getDocumentRequests();
                        Assert.assertEquals(1, docRequests.size());
                        DeviceRequestParser.DocumentRequest request = docRequests.iterator().next();
                        Assert.assertEquals(MDL_DOCTYPE, request.getDocType());
                        Assert.assertEquals(2, request.getNamespaces().size());
                        Assert.assertTrue(request.getNamespaces().contains(MDL_NAMESPACE));
                        Assert.assertTrue(request.getNamespaces().contains(AAMVA_NAMESPACE));
                        Assert.assertEquals(1, request.getEntryNames(AAMVA_NAMESPACE).size());
                        Assert.assertFalse(request.getIntentToRetain(AAMVA_NAMESPACE, "real_id"));
                        Assert.assertEquals(2, request.getEntryNames(MDL_NAMESPACE).size());
                        Assert.assertTrue(request.getIntentToRetain(MDL_NAMESPACE, "family_name"));
                        Assert.assertFalse(request.getIntentToRetain(MDL_NAMESPACE, "given_name"));

                        try {
                            DeviceResponseGenerator generator =
                                    new DeviceResponseGenerator(
                                            Constants.DEVICE_RESPONSE_STATUS_OK);

                            StaticAuthDataParser.StaticAuthData staticAuthData =
                                    new StaticAuthDataParser(mAuthKey.getIssuerProvidedData())
                                            .parse();

                            NameSpacedData deviceSignedData = new NameSpacedData.Builder().build();

                            Map<String, List<byte[]>> mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
                                    MdocUtil.generateCredentialRequest(request),
                                    mCredential.getApplicationData().getNameSpacedData("credentialData"),
                                    staticAuthData);

                            generator.addDocument(
                                    new DocumentGenerator(MDL_DOCTYPE,
                                            staticAuthData.getIssuerAuth(),
                                            encodedSessionTranscript)
                                            .setIssuerNamespaces(mergedIssuerNamespaces)
                                            .setDeviceNamespacesSignature(
                                                    deviceSignedData,
                                                    mAuthKey.getSecureArea(),
                                                    mAuthKey.getAlias(),
                                                    null,
                                                    Algorithm.ES256)
                                            .generate());

                            presentation[0].sendDeviceResponse(generator.generate(), OptionalLong.empty());

                        } catch (KeyLockedException e) {
                            throw new AssertionError(e);
                        }

                    }

                    @Override
                    public void onDeviceDisconnected(boolean transportSpecificTermination) {
                        Assert.assertFalse(transportSpecificTermination);
                        condVarDeviceDisconnected.open();
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        throw new AssertionError(error);
                    }

                };
        presentation[0] = new DeviceRetrievalHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                eDeviceKey)
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        Assert.assertTrue(condVarDeviceDisconnected.block(5000));
    }

    @Test
    public void testPresentationVerifierDisconnects() throws Exception {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();

        Executor executor = Executors.newSingleThreadExecutor();

        ConditionVariable condVarDeviceConnected = new ConditionVariable();
        ConditionVariable condVarDeviceRequestReceived = new ConditionVariable();
        ConditionVariable condVarOnError = new ConditionVariable();
        ConditionVariable condVarDeviceEngagementReady = new ConditionVariable();

        // TODO: use loopback transport
        DataTransportTcp proverTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC,
                new DataTransportOptions.Builder().build());
        DataTransportTcp verifierTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC_READER,
                new DataTransportOptions.Builder().build());
        QrEngagementHelper.Listener qrHelperListener = new QrEngagementHelper.Listener() {
                    @Override
                    public void onDeviceEngagementReady() {
                        condVarDeviceEngagementReady.open();
                    }

                    @Override
                    public void onDeviceConnecting() {
                    }

                    @Override
                    public void onDeviceConnected(DataTransport transport) {
                        condVarDeviceConnected.open();
                        Assert.assertEquals(proverTransport, transport);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        throw new AssertionError(error);
                    }
                };

        EcPrivateKey eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256);
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                eDeviceKey.getPublicKey(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(List.of(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        EcPrivateKey eReaderKey = Crypto.createEcPrivateKey(EcCurve.P256);
        byte[] encodedEReaderKeyPub = Cbor.encode(eReaderKey.getPublicKey().toCoseKey(Map.of()).getToDataItem());
        byte[] encodedSessionTranscript = Cbor.encode(
                CborArray.Companion.builder()
                        .addTaggedEncodedCbor(encodedDeviceEngagement)
                        .addTaggedEncodedCbor(encodedEReaderKeyPub)
                        .add(Simple.Companion.getNULL())
                        .end()
                        .build());
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.Role.MDOC_READER,
                eReaderKey,
                eDeviceKey.getPublicKey(),
                encodedSessionTranscript);

        // Just make an empty request since the verifier will disconnect immediately anyway.
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        byte[] encodedDeviceRequest = new DeviceRequestGenerator(encodedSessionTranscript)
                .addDocumentRequest(
                        MDL_DOCTYPE,
                        mdlItemsToRequest,
                        null,
                        null,
                        Algorithm.UNSET,
                        null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessage(encodedDeviceRequest, null);
        verifierTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnecting() {
            }

            @Override
            public void onConnected() {
            }

            @Override
            public void onDisconnected() {
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onMessageReceived() {
                Assert.fail();
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {
                    @Override
                    public void onEReaderKeyReceived(@NonNull EcPublicKey eReaderKey) {
                    }

                    @Override
                    public void onDeviceRequest(@NonNull byte[] deviceRequestBytes) {
                        // Don't respond yet.. simulate the holder taking infinity to respond.
                        // instead, we'll simply wait for the verifier to disconnect instead.
                        condVarDeviceRequestReceived.open();
                    }

                    @Override
                    public void onDeviceDisconnected(boolean transportSpecificTermination) {
                        Assert.fail();
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        condVarOnError.open();
                    }

                };
        DeviceRetrievalHelper presentation = new DeviceRetrievalHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                eDeviceKey)
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        Assert.assertTrue(condVarDeviceRequestReceived.block(5000));
        verifierTransport.close();
        Assert.assertTrue(condVarOnError.block(5000));
    }
}