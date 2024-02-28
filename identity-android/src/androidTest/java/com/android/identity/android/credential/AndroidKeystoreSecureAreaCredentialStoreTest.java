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
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings;
import com.android.identity.android.securearea.AndroidKeystoreSecureArea;
import com.android.identity.android.securearea.UserAuthenticationType;
import com.android.identity.android.securearea.UserAuthenticationTypeKt;
import com.android.identity.android.storage.AndroidStorageEngine;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialStore;
import com.android.identity.credential.PendingAuthenticationKey;
import com.android.identity.crypto.CertificateKt;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.StorageEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Set;

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

    // This isn't really used, we only use a single domain.
    private final String AUTH_KEY_DOMAIN = "domain";

    @Test
    public void testBasic() throws IOException {

        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        byte[] credentialKeyAttestationChallenge = new byte[] {10, 11, 12};

        Credential credential = credentialStore.createCredential(
                "testCredential");
        Assert.assertEquals("testCredential", credential.getName());

        // Create pending authentication key and check its attestation
        byte[] authKeyChallenge = new byte[] {20, 21, 22};
        PendingAuthenticationKey pendingAuthenticationKey =
                credential.createPendingAuthenticationKey(
                        AUTH_KEY_DOMAIN,
                        mSecureArea,
                        new AndroidKeystoreCreateKeySettings.Builder(authKeyChallenge)
                                .setUserAuthenticationRequired(true, 30*1000,
                                        Set.of(UserAuthenticationType.LSKF,
                                                UserAuthenticationType.BIOMETRIC))
                                .build(),
                        null);
        AndroidAttestationExtensionParser parser =
                new AndroidAttestationExtensionParser(CertificateKt.getJavaX509Certificate(
                        pendingAuthenticationKey.getAttestation().getCertificates().get(0)));
        Assert.assertArrayEquals(authKeyChallenge,
                parser.getAttestationChallenge());
        Assert.assertEquals(AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT,
                parser.getKeymasterSecurityLevel());

        // Check we can load the credential...
        credential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(credential);
        Assert.assertEquals("testCredential", credential.getName());

        Assert.assertNull(credentialStore.lookupCredential("nonExistingCredential"));

        // Check creating a credential with an existing name overwrites the existing one
        credential = credentialStore.createCredential(
                "testCredential");
        Assert.assertEquals("testCredential", credential.getName());

        credential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(credential);
        Assert.assertEquals("testCredential", credential.getName());

        credentialStore.deleteCredential("testCredential");
        Assert.assertNull(credentialStore.lookupCredential("testCredential"));
    }
}
