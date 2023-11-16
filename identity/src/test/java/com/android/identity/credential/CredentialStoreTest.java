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

import com.android.identity.internal.Util;
import com.android.identity.securearea.SoftwareSecureArea;
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.EphemeralStorageEngine;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.ApplicationData;
import com.android.identity.util.Timestamp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class CredentialStoreTest {
    StorageEngine mStorageEngine;

    SecureArea mSecureArea;

    SecureAreaRepository mSecureAreaRepository;

    // This isn't really used, we only use a single domain.
    private final String AUTH_KEY_DOMAIN = "domain";

    @Before
    public void setup() {
        mStorageEngine = new EphemeralStorageEngine();

        mSecureAreaRepository = new SecureAreaRepository();
        mSecureArea = new SoftwareSecureArea(mStorageEngine);
        mSecureAreaRepository.addImplementation(mSecureArea);
    }

    @Test
    public void testListCredentials() {
        mStorageEngine.deleteAll();
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Assert.assertEquals(0, credentialStore.listCredentials().size());
        for (int n = 0; n < 10; n++) {
            credentialStore.createCredential(
                    "testCred" + n);
        }
        Assert.assertEquals(10, credentialStore.listCredentials().size());
        credentialStore.deleteCredential("testCred1");
        Assert.assertEquals(9, credentialStore.listCredentials().size());
        for (int n = 0; n < 10; n++) {
            if (n == 1) {
                Assert.assertFalse(credentialStore.listCredentials().contains("testCred" + n));
            } else {
                Assert.assertTrue(credentialStore.listCredentials().contains("testCred" + n));
            }
        }
    }

    @Test
    public void testCreationDeletion() {

        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");
        Assert.assertEquals("testCredential", credential.getName());

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

    /* Validates that the same instance is returned for the same credential name. This
     * relies on Credential.equals() not being overridden.
     */
    @Test
    public void testCaching() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential a = credentialStore.createCredential(
                "a");

        Credential b = credentialStore.createCredential(
                "b");

        Assert.assertEquals(a, credentialStore.lookupCredential("a"));
        Assert.assertEquals(a, credentialStore.lookupCredential("a"));
        Assert.assertEquals(b, credentialStore.lookupCredential("b"));
        Assert.assertEquals(b, credentialStore.lookupCredential("b"));

        credentialStore.deleteCredential("a");
        Assert.assertNull(credentialStore.lookupCredential("a"));

        Credential a_prime = credentialStore.createCredential(
                "a");
        Assert.assertEquals(a_prime, credentialStore.lookupCredential("a"));
        Assert.assertEquals(a_prime, credentialStore.lookupCredential("a"));

        Assert.assertNotEquals(a_prime, a);

        Assert.assertEquals(b, credentialStore.lookupCredential("b"));
    }


    @Test
    public void testNameSpacedData() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .build();
        credential.getApplicationData().setNameSpacedData("credentialData", nameSpacedData);

        Credential loadedCredential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(loadedCredential);
        Assert.assertEquals("testCredential", loadedCredential.getName());

        // We check that NameSpacedData is preserved across loads by simply comparing the
        // encoded data.
        Assert.assertArrayEquals(
                Util.cborEncode(credential.getApplicationData().getNameSpacedData("credentialData").toCbor()),
                Util.cborEncode(loadedCredential.getApplicationData().getNameSpacedData("credentialData").toCbor()));
    }

    @Test
    public void testAuthenticationKeyUsage() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        Timestamp timeBeforeValidity = Timestamp.ofEpochMilli(40);
        Timestamp timeValidityBegin = Timestamp.ofEpochMilli(50);
        Timestamp timeDuringValidity = Timestamp.ofEpochMilli(100);
        Timestamp timeValidityEnd = Timestamp.ofEpochMilli(150);
        Timestamp timeAfterValidity = Timestamp.ofEpochMilli(200);

        // By default, we don't have any auth keys nor any pending auth keys.
        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getAuthenticationKeyCounter());

        // Since none are certified or even pending yet, we can't present anything.
        Assert.assertNull(credential.findAuthenticationKey(timeDuringValidity));

        // Create ten authentication keys...
        for (int n = 0; n < 10; n++) {
            credential.createPendingAuthenticationKey(
                    AUTH_KEY_DOMAIN,
                    mSecureArea,
                    new SecureArea.CreateKeySettings(new byte[0]),
                    null);
        }
        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(10, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(10, credential.getAuthenticationKeyCounter());

        // ... and certify all of them
        int n = 0;
        for (Credential.PendingAuthenticationKey pendingAuthenticationKey :
                credential.getPendingAuthenticationKeys()) {
            byte[] issuerProvidedAuthenticationData = {1, 2, (byte) n++};
            pendingAuthenticationKey.certify(
                    issuerProvidedAuthenticationData,
                    timeValidityBegin,
                    timeValidityEnd);
            Assert.assertEquals(n, pendingAuthenticationKey.getAuthenticationKeyCounter());
        }
        Assert.assertEquals(10, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        // If at a time before anything is valid, should not be able to present
        Assert.assertNull(credential.findAuthenticationKey(timeBeforeValidity));

        // Ditto for right after
        Assert.assertNull(credential.findAuthenticationKey(timeAfterValidity));

        // Check we're able to present at a time when the auth keys are valid
        Credential.AuthenticationKey authKey = credential.findAuthenticationKey(timeDuringValidity);
        Assert.assertNotNull(authKey);

        Assert.assertEquals(0, authKey.getUsageCount());

        // B/c of how findAuthenticationKey() we know we get the first key. Match
        // up with expected issuer signed data as per above.
        Assert.assertEquals((byte) 0, authKey.getIssuerProvidedData()[2]);

        Assert.assertEquals(0, authKey.getUsageCount());
        authKey.increaseUsageCount();
        Assert.assertEquals(1, authKey.getUsageCount());

        // Simulate nine more presentations, all of them should now be used up
        for (n = 0; n < 9; n++) {
            authKey = credential.findAuthenticationKey(timeDuringValidity);
            Assert.assertNotNull(authKey);

            // B/c of how findAuthenticationKey() we know we get the keys after
            // the first one in order. Match up with expected issuer signed data as per above.
            Assert.assertEquals((byte) (n + 1), authKey.getIssuerProvidedData()[2]);

            authKey.increaseUsageCount();
        }

        // All ten auth keys should now have a use count of 1.
        for (Credential.AuthenticationKey authenticationKey : credential.getAuthenticationKeys()) {
            Assert.assertEquals(1, authenticationKey.getUsageCount());
        }

        // Simulate ten more presentations
        for (n = 0; n < 10; n++) {
            authKey = credential.findAuthenticationKey(timeDuringValidity);
            Assert.assertNotNull(authKey);
            authKey.increaseUsageCount();
        }

        // All ten auth keys should now have a use count of 2.
        for (Credential.AuthenticationKey authenticationKey : credential.getAuthenticationKeys()) {
            Assert.assertEquals(2, authenticationKey.getUsageCount());
        }

        // Create and certify five replacements
        for (n = 0; n < 5; n++) {
            credential.createPendingAuthenticationKey(
                    AUTH_KEY_DOMAIN,
                    mSecureArea,
                    new SecureArea.CreateKeySettings(new byte[0]),
                    null);
        }
        Assert.assertEquals(10, credential.getAuthenticationKeys().size());
        Assert.assertEquals(5, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(15, credential.getAuthenticationKeyCounter());
        n = 11;
        for (Credential.PendingAuthenticationKey pendingAuthenticationKey :
                credential.getPendingAuthenticationKeys()) {
            pendingAuthenticationKey.certify(
                    new byte[0],
                    timeValidityBegin,
                    timeValidityEnd);
            Assert.assertEquals(n, pendingAuthenticationKey.getAuthenticationKeyCounter());
            n++;
        }
        Assert.assertEquals(15, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        // Simulate ten presentations and check we get the newly created ones
        for (n = 0; n < 10; n++) {
            authKey = credential.findAuthenticationKey(timeDuringValidity);
            Assert.assertNotNull(authKey);
            Assert.assertEquals(0, authKey.getIssuerProvidedData().length);
            authKey.increaseUsageCount();
        }

        // All fifteen auth keys should now have a use count of 2.
        for (Credential.AuthenticationKey authenticationKey : credential.getAuthenticationKeys()) {
            Assert.assertEquals(2, authenticationKey.getUsageCount());
        }

        // Simulate 15 more presentations
        for (n = 0; n < 15; n++) {
            authKey = credential.findAuthenticationKey(timeDuringValidity);
            Assert.assertNotNull(authKey);
            authKey.increaseUsageCount();
        }

        // All fifteen auth keys should now have a use count of 3. This shows that
        // we're hitting the auth keys evenly (both old and new).
        for (Credential.AuthenticationKey authenticationKey : credential.getAuthenticationKeys()) {
            Assert.assertEquals(3, authenticationKey.getUsageCount());
        }
    }

    @Test
    public void testAuthenticationKeyPersistence() {
        int n;

        Timestamp timeValidityBegin = Timestamp.ofEpochMilli(50);
        Timestamp timeValidityEnd = Timestamp.ofEpochMilli(150);

        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        // Create ten pending auth keys and certify four of them
        for (n = 0; n < 4; n++) {
            credential.createPendingAuthenticationKey(
                    AUTH_KEY_DOMAIN,
                    mSecureArea,
                    new SecureArea.CreateKeySettings(new byte[0]),
                    null);
        }
        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(4, credential.getPendingAuthenticationKeys().size());
        n = 0;
        for (Credential.PendingAuthenticationKey pendingAuthenticationKey :
                credential.getPendingAuthenticationKeys()) {
            // Because we check that we serialize things correctly below, make sure
            // the data and validity times vary for each key...
            Credential.AuthenticationKey authenticationKey =
                    pendingAuthenticationKey.certify(
                            new byte[]{1, 2, (byte) n},
                            Timestamp.ofEpochMilli(timeValidityBegin.toEpochMilli() + n),
                            Timestamp.ofEpochMilli(timeValidityEnd.toEpochMilli() + 2 * n));
            for (int m = 0; m < n; m++) {
                authenticationKey.increaseUsageCount();
            }
            Assert.assertEquals(n, authenticationKey.getUsageCount());
        }
        Assert.assertEquals(4, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        for (n = 0; n < 6; n++) {
            credential.createPendingAuthenticationKey(
                    AUTH_KEY_DOMAIN,
                    mSecureArea,
                    new SecureArea.CreateKeySettings(new byte[0]),
                    null);
        }
        Assert.assertEquals(4, credential.getAuthenticationKeys().size());
        Assert.assertEquals(6, credential.getPendingAuthenticationKeys().size());

        Credential credential2 = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(credential2);
        Assert.assertEquals(4, credential2.getAuthenticationKeys().size());
        Assert.assertEquals(6, credential2.getPendingAuthenticationKeys().size());

        // Now check that what we loaded matches what we created in-memory just above. We
        // use the fact that the order of the keys are preserved across save/load.
        Iterator<Credential.AuthenticationKey> it1 = credential.getAuthenticationKeys().iterator();
        Iterator<Credential.AuthenticationKey> it2 = credential2.getAuthenticationKeys().iterator();
        for (n = 0; n < 4; n++) {
            Credential.AuthenticationKey key1 = it1.next();
            Credential.AuthenticationKey key2 = it2.next();
            Assert.assertEquals(key1.getAlias(), key2.getAlias());
            Assert.assertEquals(key1.getValidFrom(), key2.getValidFrom());
            Assert.assertEquals(key1.getValidUntil(), key2.getValidUntil());
            Assert.assertEquals(key1.getUsageCount(), key2.getUsageCount());
            Assert.assertArrayEquals(key1.getIssuerProvidedData(), key2.getIssuerProvidedData());
            Assert.assertArrayEquals(key1.getAttestation().toArray(), key2.getAttestation().toArray());
        }

        Iterator<Credential.PendingAuthenticationKey> itp1 = credential.getPendingAuthenticationKeys().iterator();
        Iterator<Credential.PendingAuthenticationKey> itp2 = credential2.getPendingAuthenticationKeys().iterator();
        for (n = 0; n < 6; n++) {
            Credential.PendingAuthenticationKey key1 = itp1.next();
            Credential.PendingAuthenticationKey key2 = itp2.next();
            Assert.assertEquals(key1.mAlias, key2.mAlias);
            Assert.assertArrayEquals(key1.getAttestation().toArray(),
                    key2.getAttestation().toArray());
        }
    }

    @Test
    public void testAuthenticationKeyValidity() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        // We want to check the behavior for when the holder has a birthday and the issuer
        // carefully sends half the MSOs to be used before the birthday (with age_in_years set to
        // 17) and half the MSOs for after the birthday (with age_in_years set to 18).
        //
        // The validity periods are carefully set so the MSOs for 17 are have validUntil set to
        // to the holders birthday and the MSOs for 18 are set so validFrom starts at the birthday.
        //

        Timestamp timeValidityBegin = Timestamp.ofEpochMilli(50);
        Timestamp timeOfUseBeforeBirthday = Timestamp.ofEpochMilli(80);
        Timestamp timeOfBirthday = Timestamp.ofEpochMilli(100);
        Timestamp timeOfUseAfterBirthday = Timestamp.ofEpochMilli(120);
        Timestamp timeValidityEnd = Timestamp.ofEpochMilli(150);

        // Create and certify ten auth keys. Put age_in_years as the issuer provided data so we can
        // check it below.
        int n;
        for (n = 0; n < 10; n++) {
            credential.createPendingAuthenticationKey(
                    AUTH_KEY_DOMAIN,
                    mSecureArea,
                    new SecureArea.CreateKeySettings(new byte[0]),
                    null);
        }
        Assert.assertEquals(10, credential.getPendingAuthenticationKeys().size());

        n = 0;
        for (Credential.PendingAuthenticationKey pendingAuthenticationKey :
                credential.getPendingAuthenticationKeys()) {
            if (n < 5) {
                pendingAuthenticationKey.certify(new byte[]{17}, timeValidityBegin, timeOfBirthday);
            } else {
                pendingAuthenticationKey.certify(new byte[]{18}, timeOfBirthday, timeValidityEnd);
            }
            n++;
        }

        // Simulate ten presentations before the birthday
        for (n = 0; n < 10; n++) {
            Credential.AuthenticationKey authenticationKey =
                    credential.findAuthenticationKey(timeOfUseBeforeBirthday);
            Assert.assertNotNull(authenticationKey);
            // Check we got a key with age 17.
            Assert.assertEquals((byte) 17, authenticationKey.getIssuerProvidedData()[0]);
            authenticationKey.increaseUsageCount();
        }

        // Simulate twenty presentations after the birthday
        for (n = 0; n < 20; n++) {
            Credential.AuthenticationKey authenticationKey =
                    credential.findAuthenticationKey(timeOfUseAfterBirthday);
            Assert.assertNotNull(authenticationKey);
            // Check we got a key with age 18.
            Assert.assertEquals((byte) 18, authenticationKey.getIssuerProvidedData()[0]);
            authenticationKey.increaseUsageCount();
        }

        // Examine the authentication keys. The first five should have use count 2, the
        // latter five use count 4.
        n = 0;
        for (Credential.AuthenticationKey authenticationKey : credential.getAuthenticationKeys()) {
            if (n++ < 5) {
                Assert.assertEquals(2, authenticationKey.getUsageCount());
            } else {
                Assert.assertEquals(4, authenticationKey.getUsageCount());
            }
        }
    }

    @Test
    public void testApplicationData() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        ApplicationData appData = credential.getApplicationData();
        Assert.assertFalse(appData.keyExists("key1"));
        Assert.assertThrows(IllegalArgumentException.class, () -> appData.getData("key1"));
        Assert.assertFalse(appData.keyExists("key2"));
        Assert.assertThrows(IllegalArgumentException.class, () -> appData.getData("key2"));

        appData.setString("key1", "value1");
        Assert.assertEquals("value1", credential.getApplicationData()
                .getString("key1"));

        appData.setString("key2", "value2");
        Assert.assertEquals("value2", credential.getApplicationData()
                .getString("key2"));

        appData.setData("key3", new byte[]{1, 2, 3, 4});
        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4}, credential.getApplicationData()
                .getData("key3"));

        appData.setData("key2", (byte[]) null);
        Assert.assertFalse(credential.getApplicationData().keyExists("key2"));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                credential.getApplicationData().getData("key2"));

        // Load the credential again and check that data is still there
        Credential loadedCredential = credentialStore.lookupCredential("testCredential");
        Assert.assertNotNull(loadedCredential);
        Assert.assertEquals("testCredential", loadedCredential.getName());

        Assert.assertEquals("value1", loadedCredential.getApplicationData()
                .getString("key1"));
        Assert.assertFalse(loadedCredential.getApplicationData().keyExists("key2"));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                loadedCredential.getApplicationData().getData("key2"));
        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4}, loadedCredential.getApplicationData()
                .getData("key3"));
    }

    @Test
    public void testAuthKeyApplicationData() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        for (int n = 0; n < 10; n++) {
            Credential.PendingAuthenticationKey pendingAuthKey =
                    credential.createPendingAuthenticationKey(
                            AUTH_KEY_DOMAIN,
                            mSecureArea,
                            new SecureArea.CreateKeySettings(new byte[0]),
                            null);
            String value = String.format(Locale.US, "bar%02d", n);
            ApplicationData pendingAppData = pendingAuthKey.getApplicationData();
            pendingAppData.setString("foo", value);
            pendingAppData.setData("bar", new byte[0]);
            Assert.assertEquals(value, pendingAppData.getString("foo"));
            Assert.assertEquals(0, pendingAppData.getData("bar").length);
            Assert.assertFalse(pendingAppData.keyExists("non-existent"));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    pendingAppData.getString("non-existent"));
        }
        Assert.assertEquals(10, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getAuthenticationKeys().size());

        // Check that it's persisted to disk.
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        credential = credentialStore.lookupCredential("testCredential");
        Assert.assertEquals(10, credential.getPendingAuthenticationKeys().size());
        int n = 0;
        for (Credential.PendingAuthenticationKey pendingAuthKey : credential.getPendingAuthenticationKeys()) {
            String value = String.format(Locale.US, "bar%02d", n++);
            ApplicationData pendingAppData = pendingAuthKey.getApplicationData();
            Assert.assertEquals(value, pendingAppData.getString("foo"));
            Assert.assertEquals(0, pendingAppData.getData("bar").length);
            Assert.assertFalse(pendingAppData.keyExists("non-existent"));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    pendingAppData.getString("non-existent"));
        }

        // Certify and check that data carries over from PendingAuthenticationKey
        // to AuthenticationKey
        n = 0;
        for (Credential.PendingAuthenticationKey pendingAuthKey : credential.getPendingAuthenticationKeys()) {
            String value = String.format(Locale.US, "bar%02d", n++);
            ApplicationData pendingAppData = pendingAuthKey.getApplicationData();
            Assert.assertEquals(value, pendingAppData.getString("foo"));
            Assert.assertEquals(0, pendingAppData.getData("bar").length);
            Assert.assertFalse(pendingAppData.keyExists("non-existent"));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    pendingAppData.getString("non-existent"));
            Credential.AuthenticationKey authKey = pendingAuthKey.certify(new byte[] {0, (byte) n},
                    Timestamp.ofEpochMilli(100),
                    Timestamp.ofEpochMilli(200));
            ApplicationData appData = authKey.getApplicationData();
            Assert.assertEquals(value, appData.getString("foo"));
            Assert.assertEquals(0, appData.getData("bar").length);
            Assert.assertFalse(appData.keyExists("non-existent"));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    appData.getString("non-existent"));
        }

        // Check it's persisted to disk.
        n = 0;
        for (Credential.AuthenticationKey authKey : credential.getAuthenticationKeys()) {
            String value = String.format(Locale.US, "bar%02d", n++);
            ApplicationData appData = authKey.getApplicationData();
            Assert.assertEquals(value, appData.getString("foo"));
            Assert.assertEquals(0, appData.getData("bar").length);
            Assert.assertFalse(appData.keyExists("non-existent"));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    appData.getString("non-existent"));
        }
    }

    @Test
    public void testAuthKeyReplacement() {
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mSecureAreaRepository);

        Credential credential = credentialStore.createCredential(
                "testCredential");

        Assert.assertEquals(0, credential.getAuthenticationKeys().size());
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());

        for (int n = 0; n < 10; n++) {
            Credential.PendingAuthenticationKey pendingAuthKey =
                    credential.createPendingAuthenticationKey(
                            AUTH_KEY_DOMAIN,
                            mSecureArea,
                            new SecureArea.CreateKeySettings(new byte[0]),
                            null);
            pendingAuthKey.certify(new byte[] {0, (byte) n},
                    Timestamp.ofEpochMilli(100),
                    Timestamp.ofEpochMilli(200));
        }
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(10, credential.getAuthenticationKeys().size());

        // Now replace the fifth authentication key
        Credential.AuthenticationKey keyToReplace = credential.getAuthenticationKeys().get(5);
        Assert.assertArrayEquals(new byte[] {0, 5}, keyToReplace.getIssuerProvidedData());
        Credential.PendingAuthenticationKey pendingAuthKey =
                credential.createPendingAuthenticationKey(
                        AUTH_KEY_DOMAIN,
                        mSecureArea,
                        new SecureArea.CreateKeySettings(new byte[0]),
                        keyToReplace);
        // ... it's not replaced until certify() is called
        Assert.assertEquals(1, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(10, credential.getAuthenticationKeys().size());
        pendingAuthKey.certify(new byte[] {1, 0},
                Timestamp.ofEpochMilli(100),
                Timestamp.ofEpochMilli(200));
        // ... now it shuold be gone.
        Assert.assertEquals(0, credential.getPendingAuthenticationKeys().size());
        Assert.assertEquals(10, credential.getAuthenticationKeys().size());

        // Check that it was indeed the fifth key that was replaced inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        int count = 0;
        for (Credential.AuthenticationKey authKey : credential.getAuthenticationKeys()) {
            byte[][] expectedData = {
                    new byte[] {0, 0},
                    new byte[] {0, 1},
                    new byte[] {0, 2},
                    new byte[] {0, 3},
                    new byte[] {0, 4},
                    new byte[] {0, 6},
                    new byte[] {0, 7},
                    new byte[] {0, 8},
                    new byte[] {0, 9},
                    new byte[] {1, 0},
            };
            Assert.assertArrayEquals(expectedData[count++], authKey.getIssuerProvidedData());
        }

        // Test the case where the replacement key is prematurely deleted. The key
        // being replaced should no longer reference it has a replacement...
        Credential.AuthenticationKey toBeReplaced = credential.getAuthenticationKeys().get(0);
        Credential.PendingAuthenticationKey replacement =
                credential.createPendingAuthenticationKey(
                        AUTH_KEY_DOMAIN,
                        mSecureArea,
                        new SecureArea.CreateKeySettings(new byte[0]),
                        toBeReplaced);
        Assert.assertEquals(toBeReplaced, replacement.getReplacementFor());
        Assert.assertEquals(replacement, toBeReplaced.getReplacement());
        replacement.delete();
        Assert.assertNull(toBeReplaced.getReplacement());

        // Similarly, test the case where the key to be replaced is prematurely deleted.
        // The replacement key should no longer indicate it's a replacement key.
        replacement = credential.createPendingAuthenticationKey(
                AUTH_KEY_DOMAIN,
                mSecureArea,
                new SecureArea.CreateKeySettings(new byte[0]),
                toBeReplaced);
        Assert.assertEquals(toBeReplaced, replacement.getReplacementFor());
        Assert.assertEquals(replacement, toBeReplaced.getReplacement());
        toBeReplaced.delete();
        Assert.assertNull(replacement.getReplacementFor());
    }
}
