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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.security.identity.EphemeralPublicKeyNotFoundException;
import androidx.security.identity.IdentityCredential;
import androidx.security.identity.IdentityCredentialException;
import androidx.security.identity.IdentityCredentialStore;
import androidx.security.identity.NoAuthenticationKeyAvailableException;
import androidx.security.identity.RequestNamespace;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;

public class DynamicAuthTest {
    private static final String TAG = "DynamicAuthTest";

    @Test
    public void dynamicAuthTest() throws IdentityCredentialException, CborException {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);
        assertArrayEquals(new int[0], credential.getAuthenticationDataUsageCount());

        credential.setAvailableAuthenticationKeys(5, 3);
        assertArrayEquals(
                new int[]{0, 0, 0, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // Getting data without device authentication should work even in the case where we haven't
        // provisioned any authentication keys. Check that.
        LinkedList<RequestNamespace> requestEntryNamespaces = new LinkedList<>();
        requestEntryNamespaces.add(
                new RequestNamespace.Builder("org.iso.18013-5.2019")
                        .addEntryName("First name")
                        .addEntryName("Last name")
                        .build());
        IdentityCredential.GetEntryResult result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                null, // sessionTranscript null indicates Device Authentication not requested.
                null);
        byte[] resultCbor = result.getAuthenticatedData();
        try {
            String pretty = Util.cborPrettyPrint(Util.canonicalizeCbor(resultCbor));
            assertEquals("{\n"
                            + "  'org.iso.18013-5.2019' : {\n"
                            + "    'Last name' : 'Turing',\n"
                            + "    'First name' : 'Alan'\n"
                            + "  }\n"
                            + "}",
                    pretty);
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        KeyPair ephemeralKeyPair = credential.createEphemeralKeyPair();

        // However it should fail if device authentication *is* requested. Also check that.
        //
        // First, create a fake sessionTranscript... we expect things to fail with
        // with ERROR_EPHEMERAL_PUBLIC_KEY_NOT_FOUND because the correct ephemeral key is
        // not in the right place.
        ByteArrayOutputStream stBaos = new ByteArrayOutputStream();
        try {
            new CborEncoder(stBaos).encode(new CborBuilder()
                    .addArray()
                    .add(new byte[]{0x01, 0x02})  // The device engagement structure, encoded
                    .add(new byte[]{0x03, 0x04})  // Reader ephemeral public key, encoded
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        byte[] sessionTranscript = stBaos.toByteArray();
        try {
            result = credential.getEntries(
                    Util.createItemsRequest(requestEntryNamespaces, null),
                    requestEntryNamespaces,
                    sessionTranscript,
                    null);
            assertTrue(false);
        } catch (EphemeralPublicKeyNotFoundException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Now, correct the sessionTranscript
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        // Then check that getEntries() throw NoAuthenticationKeyAvailableException (_even_ when
        // allowing using exhausted keys).
        try {
            result = credential.getEntries(
                    Util.createItemsRequest(requestEntryNamespaces, null),
                    requestEntryNamespaces,
                    sessionTranscript,
                    null);
            assertTrue(false);
        } catch (NoAuthenticationKeyAvailableException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Get auth keys needing certification. This should be all of them. Note that
        // this forces the creation of the authentication keys in the HAL.
        Collection<X509Certificate> certificates = null;
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());

        // Do it one more time to check that an auth key is still pending even
        // when the corresponding key has been created.
        Collection<X509Certificate> certificates2 = null;
        certificates2 = credential.getAuthKeysNeedingCertification();
        assertArrayEquals(certificates.toArray(), certificates2.toArray());

        // Now set auth data for the *first* key (this is the act of certifying the key) and check
        // that one less key now needs certification.
        X509Certificate key0Cert = certificates.iterator().next();

        // Check key0Cert is signed by CredentialKey.
        try {
            key0Cert.verify(certChain.iterator().next().getPublicKey());
        } catch (CertificateException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        try {
            credential.storeStaticAuthenticationData(key0Cert, new byte[]{42, 43, 44});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(4, certificates.size());

        // Now certify the *last* key.
        X509Certificate key4Cert = (new LinkedList<X509Certificate>(certificates)).get(
                certificates.size() - 1);
        try {
            key4Cert.verify(certChain.iterator().next().getPublicKey());
        } catch (CertificateException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        try {
            credential.storeStaticAuthenticationData(key4Cert, new byte[]{43, 44, 45});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(3, certificates.size());

        // Now use one of the keys...
        requestEntryNamespaces = new LinkedList<>();
        requestEntryNamespaces.add(
                new RequestNamespace.Builder("org.iso.18013-5.2019")
                        .addEntryName("First name")
                        .addEntryName("Last name")
                        .addEntryName("Home address")
                        .addEntryName("Birth date")
                        .addEntryName("Cryptanalyst")
                        .addEntryName("Portrait image")
                        .addEntryName("Height")
                        .build());
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                sessionTranscript,
                null);
        resultCbor = result.getAuthenticatedData();
        try {
            String pretty = Util.cborPrettyPrint(Util.canonicalizeCbor(resultCbor));
            assertEquals("{\n"
                            + "  'org.iso.18013-5.2019' : {\n"
                            + "    'Height' : 180,\n"
                            + "    'Last name' : 'Turing',\n"
                            + "    'Birth date' : '19120623',\n"
                            + "    'First name' : 'Alan',\n"
                            + "    'Cryptanalyst' : true,\n"
                            + "    'Home address' : 'Maida Vale, London, England',\n"
                            + "    'Portrait image' : [0x01, 0x02]\n"
                            + "  }\n"
                            + "}",
                    pretty);
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // SW implementation doesn't implement MACing for dynamic authentication...
        assertNull(result.getMessageAuthenticationCode());

        // ...  but it does support signing.
        assertNotNull(result.getEcdsaSignature());

        // Check that key0's static auth data is returned and that this
        // key has an increased use-count.
        assertArrayEquals(new byte[]{42, 43, 44}, result.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, credential.getAuthenticationDataUsageCount());

        // Verify signature was made with key0.
        try {
            assertTrue(Util.coseSign1CheckSignature(
                result.getEcdsaSignature(),
                Util.buildDeviceAuthenticationCbor(
                    "org.iso.18013-5.2019.mdl",
                    sessionTranscript,
                    result.getAuthenticatedData()),
                key0Cert.getPublicKey()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Now do this one more time.... this time key4 should have been used. Check this by
        // inspecting use-counts and the static authentication data.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{43, 44, 45}, result.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 1}, credential.getAuthenticationDataUsageCount());

        // Verify signature was made with key4.
        try {
            assertTrue(Util.coseSign1CheckSignature(
                result.getEcdsaSignature(),
                Util.buildDeviceAuthenticationCbor(
                    "org.iso.18013-5.2019.mdl",
                    sessionTranscript,
                    result.getAuthenticatedData()),
                key4Cert.getPublicKey()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // And again.... this time key0 should have been used. Check it.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{42, 43, 44}, result.getStaticAuthenticationData());
        assertArrayEquals(new int[]{2, 0, 0, 0, 1}, credential.getAuthenticationDataUsageCount());

        // And again.... this time key4 should have been used. Check it.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{43, 44, 45}, result.getStaticAuthenticationData());
        assertArrayEquals(new int[]{2, 0, 0, 0, 2}, credential.getAuthenticationDataUsageCount());

        // We configured each key to have three uses only. So we have two more presentations
        // to go until we run out... first, check that only three keys need certifications
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(3, certificates.size());

        // Then exhaust the two we've already configured.
        for (int n = 0; n < 2; n++) {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            result = credential.getEntries(
                    Util.createItemsRequest(requestEntryNamespaces, null),
                    requestEntryNamespaces,
                    sessionTranscript,
                    null);
            assertNotNull(result);
        }
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Now we should have five certs needing certification.
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());

        // We still have the two keys which have been exhausted.
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Check that we fail when running out of presentations (and explicitly don't allow
        // exhausting keys).
        try {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            credential.setAllowUsingExhaustedKeys(false);
            result = credential.getEntries(
                    Util.createItemsRequest(requestEntryNamespaces, null),
                    requestEntryNamespaces,
                    sessionTranscript,
                    null);
            assertTrue(false);
        } catch (IdentityCredentialException e) {
            assertTrue(e instanceof NoAuthenticationKeyAvailableException);
        }
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Now try with allowing using auth keys already exhausted... this should work!
        try {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            result = credential.getEntries(
                    Util.createItemsRequest(requestEntryNamespaces, null),
                    requestEntryNamespaces,
                    sessionTranscript,
                    null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertArrayEquals(new int[]{4, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Check that replenishing works...
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());
        X509Certificate keyNewCert = certificates.iterator().next();
        try {
            credential.storeStaticAuthenticationData(keyNewCert, new byte[]{10, 11, 12});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(4, certificates.size());
        assertArrayEquals(new int[]{0, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        result = credential.getEntries(
                Util.createItemsRequest(requestEntryNamespaces, null),
                requestEntryNamespaces,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{10, 11, 12}, result.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());
        try {
            assertTrue(Util.coseSign1CheckSignature(
                result.getEcdsaSignature(),
                Util.buildDeviceAuthenticationCbor(
                    "org.iso.18013-5.2019.mdl",
                    sessionTranscript,
                    result.getAuthenticatedData()),
                keyNewCert.getPublicKey()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // ... and we're done. Clean up after ourselves.
        store.deleteCredentialByName(credentialName);
    }

    // TODO: test storeStaticAuthenticationData() throwing UnknownAuthenticationKeyException
    // on an unknown auth key
}
