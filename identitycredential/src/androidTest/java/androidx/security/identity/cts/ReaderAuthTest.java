/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.security.identity.cts;

import static androidx.security.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256;
import static androidx.security.identity.ResultNamespace.STATUS_OK;
import static androidx.security.identity.ResultNamespace.STATUS_NOT_IN_REQUEST_MESSAGE;
import static androidx.security.identity.ResultNamespace.STATUS_READER_AUTHENTICATION_FAILED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.security.identity.AccessControlProfile;
import androidx.security.identity.EntryNamespace;
import androidx.security.identity.IdentityCredential;
import androidx.security.identity.IdentityCredentialException;
import androidx.security.identity.IdentityCredentialStore;
import androidx.security.identity.InvalidReaderSignatureException;
import androidx.security.identity.RequestNamespace;
import androidx.security.identity.ResultNamespace;
import androidx.security.identity.WritableIdentityCredential;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import co.nstant.in.cbor.CborException;

public class ReaderAuthTest {
    private static final String TAG = "ReaderAuthTest";


    static KeyPair createReaderKey(String readerKeyAlias, boolean createCaKey)
            throws InvalidAlgorithmParameterException, NoSuchProviderException,
            NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                readerKeyAlias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512);
        kpg.initialize(builder.build());
        return kpg.generateKeyPair();
    }

    @Test
    public void readerAuth()
            throws IdentityCredentialException, CborException, InvalidAlgorithmParameterException,
            KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, InvalidKeyException, SignatureException {

        // We create two reader keys - 'A' and 'B' - and then generate certificates for each of
        // them, signed by a third key 'C'. We then provision a document with four elements where
        // each element is configured to be accessible only by 'A', 'B', ('A' or 'B'), and 'C'
        // respectively. The names of each element reflect this:
        //
        //  - "Accessible by A"
        //  - "Accessible by B"
        //  - "Accessible by A or B"
        //  - "Accessible by C"
        //
        // We then try reading from the credential in the following cases
        //
        //  - Request signed by A and presenting certChain {certA}
        //    - this should return the following data elements:
        //      - "Accessible by A"
        //      - "Accessible by A or B"
        //
        //  - Request signed by A and presenting certChain {certASignedByC, certC}
        //    - this should return the following data elements:
        //      - "Accessible by A"
        //      - "Accessible by A or B"
        //      - "Accessible by C"
        //
        //  - Request signed by B and presenting certChain {certB}
        //    - this should return the following data elements:
        //      - "Accessible by B"
        //      - "Accessible by A or B"
        //
        //  - Request signed by B and presenting certChain {certBSignedByC, certC}
        //    - this should return the following data elements:
        //      - "Accessible by B"
        //      - "Accessible by A or B"
        //      - "Accessible by C"
        //
        //  - Reader presenting an invalid certificate chain
        //
        // We test all this in the following.
        //


        // Generate keys and certificate chains.
        KeyPair readerKeyPairA = createReaderKey("readerKeyA", false);
        KeyPair readerKeyPairB = createReaderKey("readerKeyB", false);
        KeyPair readerKeyPairC = createReaderKey("readerKeyC", true);

        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        X509Certificate certA = (X509Certificate) ks.getCertificate("readerKeyA");
        X509Certificate certB = (X509Certificate) ks.getCertificate("readerKeyB");
        X509Certificate certASignedByC = Util.signPublicKeyWithPrivateKey("readerKeyA",
                "readerKeyC");
        X509Certificate certBSignedByC = Util.signPublicKeyWithPrivateKey("readerKeyB",
                "readerKeyC");
        X509Certificate certC = (X509Certificate) ks.getCertificate("readerKeyC");

        Collection<X509Certificate> certChainForA = new LinkedList<>();
        certChainForA.add(certA);
        Collection<X509Certificate> certChainForAwithC = new LinkedList<>();
        certChainForAwithC.add(certASignedByC);
        certChainForAwithC.add(certC);

        Collection<X509Certificate> certChainForB = new LinkedList<>();
        certChainForB.add(certB);
        Collection<X509Certificate> certChainForBwithC = new LinkedList<>();
        certChainForBwithC.add(certBSignedByC);
        certChainForBwithC.add(certC);

        // Provision the credential.
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
        String credentialName = "readerAuthTestCredential";
        store.deleteCredentialByName(credentialName);
        WritableIdentityCredential wc = store.createCredential(credentialName, "org.iso.18013-5.2019.mdl");
        LinkedList<AccessControlProfile> profiles = new LinkedList<>();
        // Profile 0 (reader A authentication)
        profiles.add(new AccessControlProfile.Builder(0)
                .setReaderCertificate(certA)
                .setUserAuthenticationRequired(false)
                .build());
        // Profile 1 (reader B authentication)
        profiles.add(new AccessControlProfile.Builder(1)
                .setReaderCertificate(certB)
                .setUserAuthenticationRequired(false)
                .build());
        // Profile 2 (reader C authentication)
        profiles.add(new AccessControlProfile.Builder(2)
                .setReaderCertificate(certC)
                .setUserAuthenticationRequired(false)
                .build());
        LinkedList<EntryNamespace> entryNamespaces = new LinkedList<>();
        Collection<Integer> idsReaderAuthA = new ArrayList<Integer>();
        idsReaderAuthA.add(0);
        Collection<Integer> idsReaderAuthB = new ArrayList<Integer>();
        idsReaderAuthB.add(1);
        Collection<Integer> idsReaderAuthAorB = new ArrayList<Integer>();
        idsReaderAuthAorB.add(0);
        idsReaderAuthAorB.add(1);
        Collection<Integer> idsReaderAuthC = new ArrayList<Integer>();
        idsReaderAuthC.add(2);
        entryNamespaces.add(
                new EntryNamespace.Builder("org.iso.18013-5.2019")
                        .addStringEntry("Accessible to A", idsReaderAuthA, "foo")
                        .addStringEntry("Accessible to B", idsReaderAuthB, "bar")
                        .addStringEntry("Accessible to A or B", idsReaderAuthAorB, "baz")
                        .addStringEntry("Accessible to C", idsReaderAuthC, "bat")
                        .build());
        byte[] proofOfProvisioningSignature = wc.personalize(profiles, entryNamespaces);
        byte[] proofOfProvisioning = Util.coseSign1GetData(proofOfProvisioningSignature);

        String pretty = Util.cborPrettyPrint(proofOfProvisioning);
        pretty = Util.replaceLine(pretty, 6, "      'readerCertificate' : [] // Removed");
        pretty = Util.replaceLine(pretty, 10, "      'readerCertificate' : [] // Removed");
        pretty = Util.replaceLine(pretty, 14, "      'readerCertificate' : [] // Removed");
        assertEquals("[\n"
                + "  'ProofOfProvisioning',\n"
                + "  'org.iso.18013-5.2019.mdl',\n"
                + "  [\n"
                + "    {\n"
                + "      'id' : 0,\n"
                + "      'readerCertificate' : [] // Removed\n"
                + "    },\n"
                + "    {\n"
                + "      'id' : 1,\n"
                + "      'readerCertificate' : [] // Removed\n"
                + "    },\n"
                + "    {\n"
                + "      'id' : 2,\n"
                + "      'readerCertificate' : [] // Removed\n"
                + "    }\n"
                + "  ],\n"
                + "  {\n"
                + "    'org.iso.18013-5.2019' : [\n"
                + "      {\n"
                + "        'name' : 'Accessible to A',\n"
                + "        'value' : 'foo',\n"
                + "        'accessControlProfiles' : [0]\n"
                + "      },\n"
                + "      {\n"
                + "        'name' : 'Accessible to B',\n"
                + "        'value' : 'bar',\n"
                + "        'accessControlProfiles' : [1]\n"
                + "      },\n"
                + "      {\n"
                + "        'name' : 'Accessible to A or B',\n"
                + "        'value' : 'baz',\n"
                + "        'accessControlProfiles' : [0, 1]\n"
                + "      },\n"
                + "      {\n"
                + "        'name' : 'Accessible to C',\n"
                + "        'value' : 'bat',\n"
                + "        'accessControlProfiles' : [2]\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  false\n"
                + "]", pretty);


        // Get the credential we'll be reading from and provision it with a sufficient number
        // of dynamic auth keys
        IdentityCredential credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);
        credential.setAvailableAuthenticationKeys(1, 10);
        Collection<X509Certificate> dynAuthKeyCerts = credential.getAuthKeysNeedingCertification();
        credential.storeStaticAuthenticationData(dynAuthKeyCerts.iterator().next(), new byte[0]);
        KeyPair eKeyPair = credential.createEphemeralKeyPair();

        IdentityCredential.GetEntryResult result;
        Collection<String> entryNames;
        Collection<ResultNamespace> resultNamespaces;
        ResultNamespace ns;

        // Create the request message which will be signed by the reader.
        LinkedList<RequestNamespace> requestEntryNamespaces = new LinkedList<>();
        requestEntryNamespaces.add(
                new RequestNamespace.Builder("org.iso.18013-5.2019")
                        .addEntryName("Accessible to A")
                        .addEntryName("Accessible to B")
                        .addEntryName("Accessible to A or B")
                        .addEntryName("Accessible to C")
                        .build());
        byte[] requestMessage = Util.createItemsRequest(requestEntryNamespaces,
                "org.iso.18013-5.2019.mdl");

        // Signing with A and presenting cert chain {certA}.
        //
        result = retrieveForReader(credential, eKeyPair, readerKeyPairA, certChainForA, requestMessage,
                requestEntryNamespaces);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("Accessible to A"));
        assertTrue(entryNames.contains("Accessible to A or B"));
        assertEquals(STATUS_OK, ns.getStatus("Accessible to A"));
        assertEquals(STATUS_OK, ns.getStatus("Accessible to A or B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to C"));

        // Signing with A and presenting cert chain {certA} and providing a requestMessage
        // that doesn't request "Accessible to A or B" in the signed |requestMessage| but
        // includes it in |entriesToRequest| (which is not signed)... should result
        // in requesting "Accessible to A or B" failing with NOT_IN_REQUEST_MESSAGE
        // and "Accessible to B" and "Accessible to C" failing with
        // READER_AUTHENTICATION_FAILED.
        //
        LinkedList<RequestNamespace> requestEntryNamespaces2 = new LinkedList<>();
        requestEntryNamespaces2.add(
                new RequestNamespace.Builder("org.iso.18013-5.2019")
                        .addEntryName("Accessible to A")
                        .addEntryName("Accessible to B")
                        .addEntryName("Accessible to C")
                        .build());
        byte[] requestMessage2 = Util.createItemsRequest(requestEntryNamespaces2,
                "org.iso.18013-5.2019.mdl");
        credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        eKeyPair = credential.createEphemeralKeyPair();
        result = retrieveForReader(credential, eKeyPair, readerKeyPairA, certChainForA, requestMessage2,
                requestEntryNamespaces);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(1, entryNames.size());
        assertTrue(entryNames.contains("Accessible to A"));
        assertEquals(STATUS_OK, ns.getStatus("Accessible to A"));
        assertEquals(STATUS_NOT_IN_REQUEST_MESSAGE, ns.getStatus("Accessible to A or B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to C"));

        // Signing with A and presenting cert chain {certAsignedByC, certC}.
        //
        credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        eKeyPair = credential.createEphemeralKeyPair();
        result = retrieveForReader(credential, eKeyPair, readerKeyPairA, certChainForAwithC, requestMessage,
                requestEntryNamespaces);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(3, entryNames.size());
        assertTrue(entryNames.contains("Accessible to A"));
        assertTrue(entryNames.contains("Accessible to A or B"));
        assertTrue(entryNames.contains("Accessible to C"));

        // Signing with B and presenting cert chain {certB}.
        //
        credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        eKeyPair = credential.createEphemeralKeyPair();
        result = retrieveForReader(credential, eKeyPair, readerKeyPairB, certChainForB, requestMessage,
                requestEntryNamespaces);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("Accessible to B"));
        assertTrue(entryNames.contains("Accessible to A or B"));

        // Signing with B and presenting cert chain {certBsignedByC, certC}.
        //
        credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        eKeyPair = credential.createEphemeralKeyPair();
        result = retrieveForReader(credential, eKeyPair, readerKeyPairB, certChainForBwithC, requestMessage,
                requestEntryNamespaces);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(3, entryNames.size());
        assertTrue(entryNames.contains("Accessible to B"));
        assertTrue(entryNames.contains("Accessible to A or B"));
        assertTrue(entryNames.contains("Accessible to C"));

        // Signing with B and presenting invalid cert chain {certB, certC} should fail
        // because certB is not signed by certC.
        try {
            credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            eKeyPair = credential.createEphemeralKeyPair();
            Collection<X509Certificate> certChain = new LinkedList<>();
            certChain.add(certB);
            certChain.add(certC);
            retrieveForReader(credential, eKeyPair, readerKeyPairB, certChain, requestMessage,
                    requestEntryNamespaces);
            assertTrue(false);
        } catch (InvalidReaderSignatureException e) {
            // Do nothing, this is the expected exception...
        }

        // No request message should result in returning zero data elements - they're
        // all protected by reader authentication.
        //
        credential = store.getCredentialByName(credentialName,
                CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        eKeyPair = credential.createEphemeralKeyPair();
        result = credential.getEntries(
            null,
            requestEntryNamespaces,
            null,
            null);
        resultNamespaces = result.getEntryNamespaces();
        assertEquals(1, resultNamespaces.size());
        ns = resultNamespaces.iterator().next();
        assertEquals("org.iso.18013-5.2019", ns.getNamespaceName());
        entryNames = ns.getRetrievedEntryNames();
        assertEquals(0, entryNames.size());
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to A"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to A or B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to B"));
        assertEquals(STATUS_READER_AUTHENTICATION_FAILED, ns.getStatus("Accessible to C"));
    }

    private IdentityCredential.GetEntryResult retrieveForReader(
            IdentityCredential credential,
            KeyPair ephemeralKeyPair,
            KeyPair readerKeyToSignWith,
            Collection<X509Certificate> readerCertificateChainToPresent,
            byte[] requestMessage,
            Collection<RequestNamespace> entriesToRequest)
            throws IdentityCredentialException, CborException, InvalidAlgorithmParameterException,
            KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, InvalidKeyException, SignatureException {

        byte[] sessionTranscriptBytes = Util.buildSessionTranscript(ephemeralKeyPair);

        // Finally, create the structure that the reader signs, and sign it.
        byte[] dataToBeSignedByReader = Util.buildReaderAuthenticationCbor(sessionTranscriptBytes, requestMessage);
        byte[] readerSignature = Util.coseSign1Sign(readerKeyToSignWith.getPrivate(),
                null, // payload
                dataToBeSignedByReader, // detached content
                readerCertificateChainToPresent); // certificate-chain

        // Now issue the request.
        IdentityCredential.GetEntryResult result = credential.getEntries(
                requestMessage,
                entriesToRequest,
                sessionTranscriptBytes,
                readerSignature);
        return result;
    }

}
