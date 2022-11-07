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

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.ConditionVariable;
import android.security.keystore.KeyProperties;
import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class PresentationHelperTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";  // TODO: verify

    private KeyPair generateIssuingAuthorityKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime256v1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }


    private X509Certificate getSelfSignedIssuerAuthorityCertificate(
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
        byte[] encodedCert = builder.build(signer).getEncoded();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedCert);
        X509Certificate result = (X509Certificate) cf.generateCertificate(bais);
        return result;
    }

    @Test
    @SmallTest
    public void testPresentation() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Util.getIdentityCredentialStore(context);
        assumeTrue(store.getFeatureVersion() >= IdentityCredentialStore.FEATURE_VERSION_202201);

        // Profile 0 (no authentication)
        AccessControlProfile noAuthProfile =
                new AccessControlProfile.Builder(new AccessControlProfileId(0))
                        .setUserAuthenticationRequired(false)
                        .build();
        Collection<AccessControlProfileId> idsNoAuth = new ArrayList<AccessControlProfileId>();
        idsNoAuth.add(new AccessControlProfileId(0));

        PersonalizationData personalizationData =
                new PersonalizationData.Builder()
                        .addAccessControlProfile(noAuthProfile)
                        .putEntryString(MDL_NAMESPACE, "given_name", idsNoAuth, "Erika")
                        .putEntryString(MDL_NAMESPACE, "family_name", idsNoAuth, "Mustermann")
                        .putEntryBoolean(AAMVA_NAMESPACE, "real_id", idsNoAuth, true)
                        .build();

        // Generate Issuing Authority keypair and X509 certificate.
        KeyPair issuerAuthorityKeyPair = generateIssuingAuthorityKeyPair();
        X509Certificate issuerAuthorityCertificate =
                getSelfSignedIssuerAuthorityCertificate(issuerAuthorityKeyPair);

        Utility.provisionSelfSignedCredential(store,
                "test",
                issuerAuthorityKeyPair.getPrivate(),
                issuerAuthorityCertificate,
                MDL_DOCTYPE,
                personalizationData,
                1,
                2);

        PresentationSession session = store.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

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
        QrEngagementHelper qrHelper = new QrEngagementHelper(context,
                session,
                new ArrayList<>(),
                new DataTransportOptions.Builder().build(),
                null,
                qrHelperListener,
                executor);
        qrHelper.addDataTransport(proverTransport);  // internal helper
        qrHelper.startListening();                   // internal helper
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        DataItem handover = SimpleValue.NULL;
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair();
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(handover)
                .end()
                .build().get(0));
        SessionEncryptionReader seReader = new SessionEncryptionReader(
                eReaderKeyPair.getPrivate(),
                eReaderKeyPair.getPublic(),
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(MDL_NAMESPACE, mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(AAMVA_NAMESPACE, aamvaNsItems);

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(MDL_DOCTYPE, mdlItemsToRequest, null, null, null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessageToDevice(encodedDeviceRequest,
                OptionalInt.empty());
        verifierTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
            }

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
                Pair<byte[], OptionalInt> decryptedMessage =
                        seReader.decryptMessageFromDevice(data);
                Assert.assertFalse(decryptedMessage.second.isPresent());

                DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                        .setDeviceResponse(decryptedMessage.first)
                        .setSessionTranscript(encodedSessionTranscript)
                        .setEphemeralReaderKey(eReaderKeyPair.getPrivate())
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
                        seReader.encryptMessageToDevice(null, OptionalInt.of(20)));
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        final PresentationHelper[] presentation = {null};
        PresentationHelper.Listener listener =
                new PresentationHelper.Listener() {

                    @Override
                    public void onDeviceRequest(@NonNull byte[] deviceRequestBytes) {
                        DeviceRequestParser parser = new DeviceRequestParser();
                        parser.setDeviceRequest(deviceRequestBytes);
                        parser.setSessionTranscript(presentation[0].getSessionTranscript());
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
                        Assert.assertFalse(request.getIntentToRetain(MDL_NAMESPACE, "portrait"));

                        Map<String, Collection<String>> issuerSignedEntriesToRequest =
                                new HashMap<>();
                        issuerSignedEntriesToRequest.put(MDL_NAMESPACE,
                                Arrays.asList("given_name", "family_name"));
                        issuerSignedEntriesToRequest.put(AAMVA_NAMESPACE, Arrays.asList("real_id"));
                        Map<String, Collection<String>> deviceSignedEntriesToRequest =
                                new HashMap<>();
                        try {
                            CredentialDataResult result = session.getCredentialData("test",
                                    new CredentialDataRequest.Builder()
                                            .setIssuerSignedEntriesToRequest(
                                                    issuerSignedEntriesToRequest)
                                            .build());

                            byte[] staticAuthData = result.getStaticAuthenticationData();
                            Pair<Map<String, List<byte[]>>, byte[]>
                                    decodedStaticAuthData = Utility.decodeStaticAuthData(
                                    staticAuthData);

                            Map<String, List<byte[]>> issuerSignedDataItems =
                                    decodedStaticAuthData.first;
                            byte[] encodedIssuerAuth = decodedStaticAuthData.second;

                            Map<String, List<byte[]>> issuerSignedDataItemsWithValues =
                                    Utility.mergeIssuerSigned(issuerSignedDataItems,
                                            result.getIssuerSignedEntries());

                            DeviceResponseGenerator generator =
                                    new DeviceResponseGenerator(
                                            Constants.DEVICE_RESPONSE_STATUS_OK);
                            generator.addDocument(request.getDocType(),
                                    result,
                                    issuerSignedDataItemsWithValues,
                                    encodedIssuerAuth);
                            presentation[0].sendDeviceResponse(generator.generate());

                        } catch (NoAuthenticationKeyAvailableException |
                                InvalidReaderSignatureException |
                                EphemeralPublicKeyNotFoundException |
                                InvalidRequestMessageException e) {
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
        presentation[0] = new PresentationHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                session)
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        Assert.assertTrue(condVarDeviceDisconnected.block(5000));

    }

    @Test
    @SmallTest
    public void testPresentationVerifierDisconnects() throws Exception {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Util.getIdentityCredentialStore(context);
        assumeTrue(store.getFeatureVersion() >= IdentityCredentialStore.FEATURE_VERSION_202201);

        PresentationSession session = store.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

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
        QrEngagementHelper qrHelper = new QrEngagementHelper(context,
                session,
                new ArrayList<>(),
                new DataTransportOptions.Builder().build(),
                null,
                qrHelperListener,
                executor);
        qrHelper.addDataTransport(proverTransport);  // internal helper
        qrHelper.startListening();                   // internal helper
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        byte[] encodedHandover = Util.cborEncode(SimpleValue.NULL);
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair();
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(Util.cborDecode(encodedHandover))
                .end()
                .build().get(0));
        SessionEncryptionReader seReader = new SessionEncryptionReader(
                eReaderKeyPair.getPrivate(),
                eReaderKeyPair.getPublic(),
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        // Just make an empty request since the verifier will disconnect immediately anyway.
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(MDL_DOCTYPE, mdlItemsToRequest, null, null, null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessageToDevice(encodedDeviceRequest,
                OptionalInt.empty());
        verifierTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
            }

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

        PresentationHelper.Listener listener =
                new PresentationHelper.Listener() {
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
        PresentationHelper presentation = new PresentationHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                session)
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