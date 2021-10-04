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

package androidx.security.identity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import co.nstant.in.cbor.CborBuilder;

public class MultiDocumentPresentationTest {
    private static final String TAG = "MultiDocumentPresentationTest";

    int[] getAuthKeyUsageCount(IdentityCredentialStore store, String credentialName)
            throws Exception {
        IdentityCredential credential = store.getCredentialByName(
                credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        return credential.getAuthenticationDataUsageCount();
    }

    static Collection<X509Certificate> createAuthKeys(IdentityCredentialStore store, String credentialName)
            throws Exception {
        IdentityCredential credential = store.getCredentialByName(
            credentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        credential.setAvailableAuthenticationKeys(5, 3);
        Collection<X509Certificate> certificates = credential.getAuthKeysNeedingCertification();
        for (X509Certificate certificate : certificates) {
            credential.storeStaticAuthenticationData(certificate, new byte[]{42, 43, 44});
        }
        return certificates;
    }

    @Test
    public void multipleDocuments() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Utility.getIdentityCredentialStore(appContext);
        assumeTrue(store.getCapabilities().isCreatePresentationSessionSupported());

        store.deleteCredentialByName("credential1");
        assertNull(store.deleteCredentialByName("credential1"));
        ProvisioningTest.createCredential(store, "credential1");
        Collection<X509Certificate> credential1AuthKeys = createAuthKeys(store, "credential1");
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential1"));

        store.deleteCredentialByName("credential2");
        assertNull(store.deleteCredentialByName("credential2"));
        ProvisioningTest.createCredential(store, "credential2");
        Collection<X509Certificate> credential2AuthKeys = createAuthKeys(store, "credential2");
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential2"));

        PresentationSession session = store.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        KeyPair ephemeralKeyPair = session.getEphemeralKeyPair();
        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        session.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        byte[] sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        session.setSessionTranscript(sessionTranscript);

        checkPresentation(session, "credential1",
                          credential1AuthKeys.iterator().next().getPublicKey(),
                          readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        // We should only have used a single key here.
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential1"));

        checkPresentation(session, "credential2",
                          credential2AuthKeys.iterator().next().getPublicKey(),
                          readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential2"));

        // Since it's the same session, additional getCredentialData() calls shouldn't consume
        // additional auth-keys. Check this.
        checkPresentation(session, "credential1",
                null,
                readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential1"));
    }

    static void checkPresentation(PresentationSession session,
                                  String credentialName,
                                  PublicKey expectedAuthKey,
                                  PrivateKey readerEphemeralPrivateKey,
                                  byte[] sessionTranscript) throws Exception {
        Map<String, Collection<String>> dsEntriesToRequest = new LinkedHashMap<>();
        dsEntriesToRequest.put("org.iso.18013-5.2019",
                Arrays.asList("First name",
                        "Last name",
                        "Home address",
                        "Birth date",
                        "Cryptanalyst",
                        "Portrait image",
                        "Height"));
        Map<String, Collection<String>> isEntriesToRequest = new LinkedHashMap<>();
        isEntriesToRequest.put("org.iso.18013-5.2019",
                Arrays.asList("First name",
                        "Last name",
                        "Home address",
                        "Birth date",
                        "Cryptanalyst",
                        "Portrait image",
                        "Height"));
        CredentialDataResult rd = session.getCredentialData(
            credentialName,
            new CredentialDataRequest.Builder()
                    .setDeviceSignedEntriesToRequest(dsEntriesToRequest)
                    .setDeviceSignedEntriesToRequest(isEntriesToRequest)
                    .setRequestMessage(Util.createItemsRequest(dsEntriesToRequest, null))
                    .build());
        byte[] resultCbor = rd.getDeviceNameSpaces();
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

        byte[] deviceAuthentication = Util.cborEncode(new CborBuilder()
                .addArray()
                .add("DeviceAuthentication")
                .add(Util.cborDecode(sessionTranscript))
                .add("org.iso.18013-5.2019.mdl")
                .add(Util.cborBuildTaggedByteString(resultCbor))
                .end()
                .build().get(0));

        byte[] mac = rd.getDeviceMac();
        byte[] signature = rd.getDeviceSignature();
        assertTrue(mac != null || signature != null);

        if (expectedAuthKey != null) {
            byte[] deviceAuthenticationBytes = Util.cborEncode(
                    Util.cborBuildTaggedByteString(deviceAuthentication));
            if (mac != null) {
                // Calculate the MAC by deriving the key using ECDH and HKDF.
                SecretKey eMacKey = Util.calcEMacKeyForReader(
                        expectedAuthKey,
                        readerEphemeralPrivateKey,
                        sessionTranscript);
                byte[] expectedMac = Util.cborEncode(Util.coseMac0(eMacKey,
                        new byte[0],                 // payload
                        deviceAuthenticationBytes));  // detached content

                // Then compare it with what the TA produced.
                assertArrayEquals(expectedMac, mac);
            } else {
                assertTrue(Util.coseSign1CheckSignature(
                        Util.cborDecode(signature),
                        deviceAuthenticationBytes,
                        expectedAuthKey));
            }
        }
    }
}
