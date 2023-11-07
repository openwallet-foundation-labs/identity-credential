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

package com.android.identity.android.credential;

import android.content.Context;

import com.android.identity.AndroidAttestationExtensionParser;
import com.android.identity.android.securearea.AndroidKeystoreSecureArea;
import com.android.identity.android.storage.AndroidStorageEngine;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialStore;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.StorageEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;

// See CredentialStoreTest in non-Android tests for main tests for CredentialStore. These
// tests are just for the Android-specific bits including attestation.
//
public class AndroidKeystoreSecureAreaCredentialStoreTest {

    StorageEngine mStorageEngine;

    SecureArea mSecureArea;

    SecureAreaRepository mSecureAreaRepository;

    @Before
    public void setup() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        mStorageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new AndroidKeystoreSecureArea(context, mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);
    }

    @Test
    public void testBasic() throws IOException {

        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        byte[] credentialKeyAttestationChallenge = new byte[] {10, 11, 12};

        Credential credential = credentialStore.createCredential(
                "testCredential",
                mSecureArea,
                new AndroidKeystoreSecureArea.CreateKeySettings.Builder(credentialKeyAttestationChallenge).build());
        Assert.assertEquals("testCredential", credential.getName());
        List<X509Certificate> certChain = credential.getAttestation();

        // Check the attestation extension
        AndroidAttestationExtensionParser parser = new AndroidAttestationExtensionParser(certChain.get(0));
        Assert.assertArrayEquals(credentialKeyAttestationChallenge, parser.getAttestationChallenge());
        AndroidAttestationExtensionParser.SecurityLevel securityLevel = parser.getKeymasterSecurityLevel();
        Assert.assertEquals(AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT, securityLevel);

        // Create pending authentication key and check its attestation
        byte[] authKeyChallenge = new byte[] {20, 21, 22};
        Credential.PendingAuthenticationKey pendingAuthenticationKey =
                credential.createPendingAuthenticationKey(
                        mSecureArea,
                        new AndroidKeystoreSecureArea.CreateKeySettings.Builder(authKeyChallenge)
                                .setUserAuthenticationRequired(true, 30*1000,
                                        AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                                                | AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC)
                                .build(),
                        null);
        parser = new AndroidAttestationExtensionParser(pendingAuthenticationKey.getAttestation().get(0));
        Assert.assertArrayEquals(authKeyChallenge,
                parser.getAttestationChallenge());
        Assert.assertEquals(AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT,
                parser.getKeymasterSecurityLevel());

        // Check we can load the credential...
        credential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(credential);
        Assert.assertEquals("testCredential", credential.getName());
        List<X509Certificate> certChain2 = credential.getAttestation();
        Assert.assertEquals(certChain.size(), certChain2.size());
        for (int n = 0; n < certChain.size(); n++) {
            Assert.assertEquals(certChain.get(n), certChain2.get(n));
        }

        Assert.assertNull(credentialStore.lookupCredential("nonExistingCredential"));

        // Check creating a credential with an existing name overwrites the existing one
        credential = credentialStore.createCredential(
                "testCredential",
                mSecureArea,
                new AndroidKeystoreSecureArea.CreateKeySettings.Builder(credentialKeyAttestationChallenge).build());
        Assert.assertEquals("testCredential", credential.getName());
        // At least the leaf certificate should be different
        List<X509Certificate> certChain3 = credential.getAttestation();
        Assert.assertNotEquals(certChain3.get(0), certChain2.get(0));

        credential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(credential);
        Assert.assertEquals("testCredential", credential.getName());

        credentialStore.deleteCredential("testCredential");
        Assert.assertNull(credentialStore.lookupCredential("testCredential"));
    }
}
