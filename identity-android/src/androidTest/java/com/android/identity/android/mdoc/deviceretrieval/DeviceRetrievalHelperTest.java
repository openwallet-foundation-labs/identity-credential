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

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.ConditionVariable;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.identity.android.legacy.AccessControlProfile;
import com.android.identity.android.legacy.AccessControlProfileId;
import com.android.identity.android.legacy.CredentialDataRequest;
import com.android.identity.android.legacy.CredentialDataResult;
import com.android.identity.android.legacy.EphemeralPublicKeyNotFoundException;
import com.android.identity.android.legacy.IdentityCredentialStore;
import com.android.identity.android.legacy.InvalidReaderSignatureException;
import com.android.identity.android.legacy.InvalidRequestMessageException;
import com.android.identity.android.legacy.NoAuthenticationKeyAvailableException;
import com.android.identity.android.legacy.PersonalizationData;
import com.android.identity.android.legacy.PresentationSession;
import com.android.identity.android.legacy.Utility;
import com.android.identity.android.mdoc.document.Namespace;
import com.android.identity.android.mdoc.document.DocumentType;
import com.android.identity.android.mdoc.engagement.QrEngagementHelper;
import com.android.identity.android.mdoc.transport.DataTransport;
import com.android.identity.android.mdoc.transport.DataTransportOptions;
import com.android.identity.android.mdoc.transport.DataTransportTcp;
import com.android.identity.mdoc.mso.StaticAuthDataParser;
import com.android.identity.mdoc.request.DeviceRequestGenerator;
import com.android.identity.mdoc.request.DeviceRequestParser;
import com.android.identity.mdoc.response.DeviceResponseGenerator;
import com.android.identity.mdoc.response.DeviceResponseParser;
import com.android.identity.mdoc.sessionencryption.SessionEncryption;
import com.android.identity.securearea.SecureArea;
import com.android.identity.util.Constants;
import com.android.identity.internal.Util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class DeviceRetrievalHelperTest {
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
        IdentityCredentialStore store = Utility.getIdentityCredentialStore(context);
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
                        .putEntryString(Namespace.MDL.getValue(), "given_name", idsNoAuth, "Erika")
                        .putEntryString(Namespace.MDL.getValue(), "family_name", idsNoAuth, "Mustermann")
                        .putEntryBoolean(Namespace.MDL_AAMVA.getValue(), "real_id", idsNoAuth, true)
                        .build();

        // Generate Issuing Authority keypair and X509 certificate.
        KeyPair issuerAuthorityKeyPair = generateIssuingAuthorityKeyPair();
        X509Certificate issuerAuthorityCertificate =
                getSelfSignedIssuerAuthorityCertificate(issuerAuthorityKeyPair);

        Utility.provisionSelfSignedCredential(store,
                "test",
                issuerAuthorityKeyPair.getPrivate(),
                issuerAuthorityCertificate,
                DocumentType.MDL.getValue(),
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
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                session.getEphemeralKeyPair().getPublic(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(Collections.singletonList(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        DataItem handover = SimpleValue.NULL;
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair(SecureArea.EC_CURVE_P256);
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(handover)
                .end()
                .build().get(0));
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                eReaderKeyPair,
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(Namespace.MDL.getValue(), mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(Namespace.MDL_AAMVA.getValue(), aamvaNsItems);

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(DocumentType.MDL.getValue(), mdlItemsToRequest, null, null, null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessage(encodedDeviceRequest,
                OptionalLong.empty());
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
                SessionEncryption.DecryptedMessage decryptedMessage = seReader.decryptMessage(data);
                Assert.assertFalse(decryptedMessage.getStatus().isPresent());

                DeviceResponseParser.DeviceResponse dr = new DeviceResponseParser()
                        .setDeviceResponse(decryptedMessage.getData())
                        .setSessionTranscript(encodedSessionTranscript)
                        .setEphemeralReaderKey(eReaderKeyPair.getPrivate())
                        .parse();
                Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.getStatus());
                Assert.assertEquals("1.0", dr.getVersion());
                Collection<DeviceResponseParser.Document> documents = dr.getDocuments();
                Assert.assertEquals(1, documents.size());
                DeviceResponseParser.Document d = documents.iterator().next();
                Assert.assertEquals(DocumentType.MDL.getValue(), d.getDocType());
                Assert.assertEquals(0, d.getDeviceNamespaces().size());
                Assert.assertEquals(2, d.getIssuerNamespaces().size());
                Assert.assertEquals(2, d.getIssuerEntryNames(Namespace.MDL.getValue()).size());
                Assert.assertEquals("Erika",
                        d.getIssuerEntryString(Namespace.MDL.getValue(), "given_name"));
                Assert.assertEquals("Mustermann",
                        d.getIssuerEntryString(Namespace.MDL.getValue(), "family_name"));
                Assert.assertEquals(1, d.getIssuerEntryNames(Namespace.MDL_AAMVA.getValue()).size());
                Assert.assertTrue(d.getIssuerEntryBoolean(Namespace.MDL_AAMVA.getValue(), "real_id"));

                // Send a close message (status 20 is "session termination")
                verifierTransport.sendMessage(
                        seReader.encryptMessage(null, OptionalLong.of(
                                Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)));
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        final DeviceRetrievalHelper[] presentation = {null};
        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {

                    @Override
                    public void onEReaderKeyReceived(@NonNull PublicKey eReaderKey) {
                        try {
                            session.setSessionTranscript(presentation[0].getSessionTranscript());
                            session.setReaderEphemeralPublicKey(eReaderKey);
                        } catch (InvalidKeyException e) {
                            throw new AssertionError(e);
                        }
                    }

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
                        Assert.assertEquals(DocumentType.MDL.getValue(), request.getDocType());
                        Assert.assertEquals(2, request.getNamespaces().size());
                        Assert.assertTrue(request.getNamespaces().contains(Namespace.MDL.getValue()));
                        Assert.assertTrue(request.getNamespaces().contains(Namespace.MDL_AAMVA.getValue()));
                        Assert.assertEquals(1, request.getEntryNames(Namespace.MDL_AAMVA.getValue()).size());
                        Assert.assertFalse(request.getIntentToRetain(Namespace.MDL_AAMVA.getValue(), "real_id"));
                        Assert.assertEquals(2, request.getEntryNames(Namespace.MDL.getValue()).size());
                        Assert.assertTrue(request.getIntentToRetain(Namespace.MDL.getValue(), "family_name"));
                        Assert.assertFalse(request.getIntentToRetain(Namespace.MDL.getValue(), "portrait"));

                        Map<String, Collection<String>> issuerSignedEntriesToRequest =
                                new HashMap<>();
                        issuerSignedEntriesToRequest.put(Namespace.MDL.getValue(),
                                Arrays.asList("given_name", "family_name"));
                        issuerSignedEntriesToRequest.put(Namespace.MDL_AAMVA.getValue(), Collections.singletonList("real_id"));
                        Map<String, Collection<String>> deviceSignedEntriesToRequest =
                                new HashMap<>();
                        try {
                            CredentialDataResult result = session.getCredentialData("test",
                                    new CredentialDataRequest.Builder()
                                            .setIssuerSignedEntriesToRequest(
                                                    issuerSignedEntriesToRequest)
                                            .build());

                            byte[] staticAuthData = result.getStaticAuthenticationData();
                            StaticAuthDataParser.StaticAuthData decodedStaticAuthData =
                                    new StaticAuthDataParser(staticAuthData).parse();

                            Map<String, List<byte[]>> issuerSignedDataItems =
                                    decodedStaticAuthData.getDigestIdMapping();
                            byte[] encodedIssuerAuth = decodedStaticAuthData.getIssuerAuth();

                            Map<String, List<byte[]>> issuerSignedDataItemsWithValues =
                                    Utility.mergeIssuerSigned(issuerSignedDataItems,
                                            result.getIssuerSignedEntries());

                            DeviceResponseGenerator generator =
                                    new DeviceResponseGenerator(
                                            Constants.DEVICE_RESPONSE_STATUS_OK);
                            Utility.addDocument(
                                    generator,
                                    request.getDocType(),
                                    result,
                                    issuerSignedDataItemsWithValues,
                                    null,
                                    encodedIssuerAuth);
                            presentation[0].sendDeviceResponse(generator.generate(), OptionalLong.empty());

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
        presentation[0] = new DeviceRetrievalHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                session.getEphemeralKeyPair())
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        Assert.assertTrue(condVarDeviceDisconnected.block(5000));
    }


    @Test
    @SmallTest
    public void testPresentationErrorDecryptingFromReader() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Utility.getIdentityCredentialStore(context);
        assumeTrue(store.getFeatureVersion() >= IdentityCredentialStore.FEATURE_VERSION_202201);

        PresentationSession session = store.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        ConditionVariable condVarDeviceConnected = new ConditionVariable();
        ConditionVariable condVarDecryptionErrorReceived = new ConditionVariable();
        ConditionVariable condVarReaderReceivedStatus10 = new ConditionVariable();
        ConditionVariable condVarDeviceEngagementReady = new ConditionVariable();

        // TODO: use loopback instead of TCP transport
        DataTransportTcp proverTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC,
                new DataTransportOptions.Builder().build());
        DataTransportTcp verifierTransport = new DataTransportTcp(context,
                DataTransport.ROLE_MDOC_READER,
                new DataTransportOptions.Builder().build());

        // Cause a decryption error on the holder side...
        proverTransport.setMessageRewriter(new DataTransportTcp.MessageRewriter() {
            @NonNull
            @Override
            public byte[] rewrite(@NonNull byte[] message) {
                // We know the first message is a map with a single "data" value which is a bstr.
                DataItem item = Util.cborDecode(message);
                byte[] data = Util.cborMapExtractByteString(item, "data");

                // Since mdoc session encryption is using AES-256-GCM it's sufficient to just
                // flip a single bit to cause decryption to fail. We'll flip all the bits in the
                // first byte, for good measure.
                data[0] ^= 0xff;

                ((co.nstant.in.cbor.model.Map) item).put(
                        new UnicodeString("data"), new ByteString(data));
                return Util.cborEncode(item);
            }
        });

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
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                session.getEphemeralKeyPair().getPublic(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(Collections.singletonList(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        DataItem handover = SimpleValue.NULL;
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair(SecureArea.EC_CURVE_P256);
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(handover)
                .end()
                .build().get(0));
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                eReaderKeyPair,
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(Namespace.MDL.getValue(), mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(Namespace.MDL_AAMVA.getValue(), aamvaNsItems);

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(DocumentType.MDL.getValue(), mdlItemsToRequest, null, null, null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessage(encodedDeviceRequest,
                OptionalLong.empty());
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
                SessionEncryption.DecryptedMessage decryptedMessage = seReader.decryptMessage(data);
                Assert.assertNull(decryptedMessage.getData());
                Assert.assertTrue(decryptedMessage.getStatus().isPresent());
                long status = decryptedMessage.getStatus().getAsLong();
                Assert.assertEquals(Constants.SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION, status);
                condVarReaderReceivedStatus10.open();
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        final DeviceRetrievalHelper[] presentation = {null};
        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {
                    @Override
                    public void onEReaderKeyReceived(@NonNull PublicKey eReaderKey) {
                        try {
                            session.setReaderEphemeralPublicKey(eReaderKey);
                        } catch (InvalidKeyException e) {
                            throw new AssertionError(e);
                        }
                    }

                    @Override
                    public void onDeviceRequest(@NonNull byte[] deviceRequestBytes) {
                        Assert.fail("Should not be reached");
                    }

                    @Override
                    public void onDeviceDisconnected(boolean transportSpecificTermination) {
                        Assert.assertFalse(transportSpecificTermination);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        Assert.assertEquals("Error decrypting message from reader",
                                error.getMessage());
                        condVarDecryptionErrorReceived.open();
                    }

                };
        presentation[0] = new DeviceRetrievalHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                session.getEphemeralKeyPair())
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        // Check the application was informed that we failed because of decryption error
        Assert.assertTrue(condVarDecryptionErrorReceived.block(5000));
        // Check the remote reader received a session termination message with status 10
        Assert.assertTrue(condVarReaderReceivedStatus10.block(5000));
    }

    @Test
    @SmallTest
    public void testPresentationBadKeyFromReader() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Utility.getIdentityCredentialStore(context);
        assumeTrue(store.getFeatureVersion() >= IdentityCredentialStore.FEATURE_VERSION_202201);

        PresentationSession session = store.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        ConditionVariable condVarDeviceConnected = new ConditionVariable();
        ConditionVariable condVarDecryptionErrorReceived = new ConditionVariable();
        ConditionVariable condVarReaderReceivedStatus10 = new ConditionVariable();
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
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                session.getEphemeralKeyPair().getPublic(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(Collections.singletonList(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        DataItem handover = SimpleValue.NULL;
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair(SecureArea.EC_CURVE_P256);
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(handover)
                .end()
                .build().get(0));
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                eReaderKeyPair,
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        Map<String, Boolean> mdlNsItems = new HashMap<>();
        mdlNsItems.put("family_name", true);
        mdlNsItems.put("portrait", false);
        mdlItemsToRequest.put(Namespace.MDL.getValue(), mdlNsItems);
        Map<String, Boolean> aamvaNsItems = new HashMap<>();
        aamvaNsItems.put("real_id", false);
        mdlItemsToRequest.put(Namespace.MDL_AAMVA.getValue(), aamvaNsItems);

        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(DocumentType.MDL.getValue(), mdlItemsToRequest, null, null, null)
                .generate();
        // NOTE! This creates a SessionEstablishment where EReaderKey's Y coordinate is negated
        // and thus the key is not valid. This is to check that DeviceRetrievalHelper correctly
        // detects this condition.
        byte[] sessionEstablishment = seReader.encryptMessageWithInvalidEReaderKey(
                encodedDeviceRequest,
                OptionalLong.empty());

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
                SessionEncryption.DecryptedMessage decryptedMessage = seReader.decryptMessage(data);
                Assert.assertNull(decryptedMessage.getData());
                Assert.assertTrue(decryptedMessage.getStatus().isPresent());
                long status = decryptedMessage.getStatus().getAsLong();
                Assert.assertEquals(Constants.SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION, status);
                condVarReaderReceivedStatus10.open();
            }
        }, executor);

        verifierTransport.setHostAndPort(proverTransport.getHost(), proverTransport.getPort());
        verifierTransport.connect();
        Assert.assertTrue(condVarDeviceConnected.block(5000));

        final DeviceRetrievalHelper[] presentation = {null};
        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {
                    @Override
                    public void onEReaderKeyReceived(@NonNull PublicKey eReaderKey) {
                        try {
                            session.setReaderEphemeralPublicKey(eReaderKey);
                        } catch (InvalidKeyException e) {
                            throw new AssertionError(e);
                        }
                    }

                    @Override
                    public void onDeviceRequest(@NonNull byte[] deviceRequestBytes) {
                        Assert.fail("Should not be reached");
                    }

                    @Override
                    public void onDeviceDisconnected(boolean transportSpecificTermination) {
                        Assert.assertFalse(transportSpecificTermination);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        Assert.assertEquals(
                                "Error decoding EReaderKey in SessionEstablishment, returning status 10",
                                error.getMessage());
                        condVarDecryptionErrorReceived.open();
                    }

                };
        presentation[0] = new DeviceRetrievalHelper.Builder(
                context,
                listener,
                context.getMainExecutor(),
                session.getEphemeralKeyPair())
                .useForwardEngagement(proverTransport,
                        qrHelper.getDeviceEngagement(),
                        qrHelper.getHandover())
                .build();

        verifierTransport.sendMessage(sessionEstablishment);
        // Check the application was informed that we failed because of decryption error
        Assert.assertTrue(condVarDecryptionErrorReceived.block(5000));
        // Check the remote reader received a session termination message with status 10
        Assert.assertTrue(condVarReaderReceivedStatus10.block(5000));
    }

    @Test
    @SmallTest
    public void testPresentationVerifierDisconnects() throws Exception {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Utility.getIdentityCredentialStore(context);
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
        QrEngagementHelper qrHelper = new QrEngagementHelper.Builder(
                context,
                session.getEphemeralKeyPair().getPublic(),
                new DataTransportOptions.Builder().build(),
                qrHelperListener,
                executor)
                .setTransports(Collections.singletonList(proverTransport))
                .build();
        Assert.assertTrue(condVarDeviceEngagementReady.block(5000));
        byte[] encodedDeviceEngagement = qrHelper.getDeviceEngagement();

        byte[] encodedHandover = Util.cborEncode(SimpleValue.NULL);
        KeyPair eReaderKeyPair = Util.createEphemeralKeyPair(SecureArea.EC_CURVE_P256);
        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(eReaderKeyPair.getPublic()));
        byte[] encodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(encodedDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(Util.cborDecode(encodedHandover))
                .end()
                .build().get(0));
        SessionEncryption seReader = new SessionEncryption(SessionEncryption.ROLE_MDOC_READER,
                eReaderKeyPair,
                session.getEphemeralKeyPair().getPublic(),
                encodedSessionTranscript);

        // Just make an empty request since the verifier will disconnect immediately anyway.
        Map<String, Map<String, Boolean>> mdlItemsToRequest = new HashMap<>();
        byte[] encodedDeviceRequest = new DeviceRequestGenerator()
                .addDocumentRequest(DocumentType.MDL.getValue(), mdlItemsToRequest, null, null, null)
                .generate();
        byte[] sessionEstablishment = seReader.encryptMessage(encodedDeviceRequest,
                OptionalLong.empty());
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

        DeviceRetrievalHelper.Listener listener =
                new DeviceRetrievalHelper.Listener() {
                    @Override
                    public void onEReaderKeyReceived(@NonNull PublicKey eReaderKey) {
                        try {
                            session.setReaderEphemeralPublicKey(eReaderKey);
                        } catch (InvalidKeyException e) {
                            throw new AssertionError(e);
                        }
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
                session.getEphemeralKeyPair())
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