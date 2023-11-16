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

package com.android.identity.credential;

import com.android.identity.securearea.SoftwareSecureArea;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.EphemeralStorageEngine;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Timestamp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CredentialUtilTest {
    StorageEngine mStorageEngine;

    SecureArea mSecureArea;

    SecureAreaRepository mSecureAreaRepository;

    @Before
    public void setup() {
        mStorageEngine = new EphemeralStorageEngine();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new SoftwareSecureArea(mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);
    }

    @Test
    public void testManagedAuthenticationKeyHelper() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential",
                mSecureArea,
                new SecureArea.CreateKeySettings(new byte[0]));

        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        SecureArea.CreateKeySettings authKeySettings =
                new SecureArea.CreateKeySettings(new byte[0]);

        int numAuthKeys = 10;
        int maxUsesPerKey = 5;
        long minValidTimeMillis = 10;
        int count;
        int numKeysCreated;
        String managedKeyDomain = "managedAuthenticationKeys";

        // Start the process at time 100 and certify all those keys so they're
        // valid until time 200.
        numKeysCreated = CredentialUtil.managedAuthenticationKeyHelper(
                credential,
                mSecureArea,
                authKeySettings,
                managedKeyDomain,
                Timestamp.ofEpochMilli(100),
                numAuthKeys,
                maxUsesPerKey,
                minValidTimeMillis);
        Assert.assertEquals(numAuthKeys, numKeysCreated);
        Assert.assertEquals(numAuthKeys, credential.getPendingAuthenticationKeys().size());
        count = 0;
        for (Credential.PendingAuthenticationKey pak : credential.getPendingAuthenticationKeys()) {
            Assert.assertTrue(pak.getApplicationData().getBoolean(managedKeyDomain));
            pak.certify(new byte[] {0, (byte) count++},
                    Timestamp.ofEpochMilli(100),
                    Timestamp.ofEpochMilli(200));
        }
        // We should now have |numAuthKeys| certified keys and none pending
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(numAuthKeys, credential.getAuthenticationKeys().size());

        // Certifying again at this point should not make a difference.
        numKeysCreated = CredentialUtil.managedAuthenticationKeyHelper(
                credential,
                mSecureArea,
                authKeySettings,
                managedKeyDomain,
                Timestamp.ofEpochMilli(100),
                numAuthKeys,
                maxUsesPerKey,
                minValidTimeMillis);
        Assert.assertEquals(0, numKeysCreated);
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        // Use up until just before the limit, and check it doesn't make a difference
        for (Credential.AuthenticationKey ak : credential.getAuthenticationKeys()) {
            for (int n = 0; n < maxUsesPerKey - 1; n++) {
                ak.increaseUsageCount();
            }
        }
        numKeysCreated = CredentialUtil.managedAuthenticationKeyHelper(
                credential,
                mSecureArea,
                authKeySettings,
                managedKeyDomain,
                Timestamp.ofEpochMilli(100),
                numAuthKeys,
                maxUsesPerKey,
                minValidTimeMillis);
        Assert.assertEquals(0, numKeysCreated);
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        // For the first 5, use one more time and check replacements are generated for those
        // Let the replacements expire just a tad later
        count = 0;
        for (Credential.AuthenticationKey ak : credential.getAuthenticationKeys()) {
            ak.increaseUsageCount();
            if (++count >= 5) {
                break;
            }
        }
        numKeysCreated = CredentialUtil.managedAuthenticationKeyHelper(
                credential,
                mSecureArea,
                authKeySettings,
                managedKeyDomain,
                Timestamp.ofEpochMilli(100),
                numAuthKeys,
                maxUsesPerKey,
                minValidTimeMillis);
        Assert.assertEquals(5, numKeysCreated);
        Assert.assertEquals(5, credential.getPendingAuthenticationKeys().size());
        count = 0;
        for (Credential.PendingAuthenticationKey pak : credential.getPendingAuthenticationKeys()) {
            Assert.assertEquals(managedKeyDomain, pak.getDomain());
            pak.certify(new byte[] {1, (byte) count++},
                    Timestamp.ofEpochMilli(100),
                    Timestamp.ofEpochMilli(210));
        }
        // We should now have |numAuthKeys| certified keys and none pending
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(numAuthKeys, credential.getAuthenticationKeys().size());
        // Check that the _right_ ones were removed by inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        count = 0;
        for (Credential.AuthenticationKey authKey : credential.getAuthenticationKeys()) {
            byte[][] expectedData = {
                    new byte[] {0, 5},
                    new byte[] {0, 6},
                    new byte[] {0, 7},
                    new byte[] {0, 8},
                    new byte[] {0, 9},
                    new byte[] {1, 0},
                    new byte[] {1, 1},
                    new byte[] {1, 2},
                    new byte[] {1, 3},
                    new byte[] {1, 4},
            };
            Assert.assertArrayEquals(expectedData[count++], authKey.getIssuerProvidedData());
        }

        // Now move close to the expiration date of the original five auth keys.
        // This should trigger just them for replacement
        numKeysCreated = CredentialUtil.managedAuthenticationKeyHelper(
                credential,
                mSecureArea,
                authKeySettings,
                managedKeyDomain,
                Timestamp.ofEpochMilli(195),
                numAuthKeys,
                maxUsesPerKey,
                minValidTimeMillis);
        Assert.assertEquals(5, numKeysCreated);
        Assert.assertEquals(5, credential.getPendingAuthenticationKeys().size());
        count = 0;
        for (Credential.PendingAuthenticationKey pak : credential.getPendingAuthenticationKeys()) {
            Assert.assertEquals(managedKeyDomain, pak.getDomain());
            pak.certify(new byte[] {2, (byte) count++},
                    Timestamp.ofEpochMilli(100),
                    Timestamp.ofEpochMilli(210));
        }
        // We should now have |numAuthKeys| certified keys and none pending
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(numAuthKeys, credential.getAuthenticationKeys().size());
        // Check that the _right_ ones were removed by inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        count = 0;
        for (Credential.AuthenticationKey authKey : credential.getAuthenticationKeys()) {
            byte[][] expectedData = {
                    new byte[] {1, 0},
                    new byte[] {1, 1},
                    new byte[] {1, 2},
                    new byte[] {1, 3},
                    new byte[] {1, 4},
                    new byte[] {2, 0},
                    new byte[] {2, 1},
                    new byte[] {2, 2},
                    new byte[] {2, 3},
                    new byte[] {2, 4},
            };
            Assert.assertArrayEquals(expectedData[count++], authKey.getIssuerProvidedData());
        }
    }
}
