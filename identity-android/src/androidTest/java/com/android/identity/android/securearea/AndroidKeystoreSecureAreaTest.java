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

package com.android.identity.android.securearea;

import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.identity.AndroidAttestationExtensionParser;
import com.android.identity.android.storage.AndroidStorageEngine;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.CertificateKt;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKeyKt;
import com.android.identity.securearea.CreateKeySettings;
import com.android.identity.crypto.EcCurve;
import com.android.identity.securearea.KeyInfo;
import com.android.identity.securearea.KeyLockedException;
import com.android.identity.securearea.KeyPurpose;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Timestamp;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Set;

public class AndroidKeystoreSecureAreaTest {

    @Before
    public void setup() {
        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in Android.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testEcKeyDeletion() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3}).build();

        // First create the key...
        ks.createKey("testKey", settings);

        // Now delete it...
        ks.deleteKey("testKey");

        // Now that we know the key doesn't exist, check that ecKeySign() throws
        try {
            ks.sign("testKey", Algorithm.ES256, new byte[] {1, 2}, null);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // Expected path.
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Now delete it again, this should not fail.
        ks.deleteKey("testKey");
    }

    @Test
    public void testEcKeySigning() {
        testEcKeySigningHelper(false);
    }

    @Test
    public void testEcKeySigningStrongBox() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE));
        testEcKeySigningHelper(true);
    }

    public void testEcKeySigningHelper(boolean useStrongBox) {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setUseStrongBox(useStrongBox)
                        .build();

        ks.createKey("testKey", settings);

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", Algorithm.ES256, dataToSign, null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        Assert.assertTrue(Crypto.checkSignature(keyInfo.getPublicKey(), dataToSign, Algorithm.ES256, derSignature));
    }

    @Test
    public void testEcKeySigningAuthBound() {
        testEcKeySigningAuthBoundHelper(false);
    }

    @Test
    public void testEcKeySigningAuthBoundStrongBox() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE));
        testEcKeySigningAuthBoundHelper(true);
    }

    public void testEcKeySigningAuthBoundHelper(boolean useStrongBox) {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setUseStrongBox(useStrongBox)
                        .setUserAuthenticationRequired(true,
                                42,
                                Set.of(UserAuthenticationType.LSKF,
                                                UserAuthenticationType.BIOMETRIC))
                        .build();

        ks.createKey("testKey", settings);

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertTrue(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(42, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertEquals(
                Set.of(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC),
                keyInfo.getUserAuthenticationTypes());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null);
            Assert.fail("Should not be reached");
        } catch (KeyLockedException e) {
            /* expected path */
        }
    }

    @Test
    public void testEcKeyAuthenticationTypeLskf() {
        // setUserAuthenticationParameters() is only available on API 30 or later.
        //
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);

        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        Set<UserAuthenticationType> type = Set.of(UserAuthenticationType.LSKF);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setUserAuthenticationRequired(true, 42, type)
                        .build();

        ks.createKey("testKey", settings);
        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertFalse(keyInfo.isStrongBoxBacked());
        Assert.assertTrue(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(42, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertEquals(type, keyInfo.getUserAuthenticationTypes());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null);
            Assert.fail("Should not be reached");
        } catch (KeyLockedException e) {
            /* expected path */
        }
    }

    @Test
    public void testEcKeyAuthenticationTypeBiometric() {
        // setUserAuthenticationParameters() is only available on API 30 or later.
        //
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);

        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        Set<UserAuthenticationType> type = Set.of(UserAuthenticationType.BIOMETRIC);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setUserAuthenticationRequired(true, 42, type)
                        .build();

        ks.createKey("testKey", settings);
        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertFalse(keyInfo.isStrongBoxBacked());
        Assert.assertTrue(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(42, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertEquals(type, keyInfo.getUserAuthenticationTypes());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null);
            Assert.fail("Should not be reached");
        } catch (KeyLockedException e) {
            /* expected path */
        }
    }

    @Test
    public void testEcKeyAuthenticationTypeNone() {
        // setUserAuthenticationParameters() is only available on API 30 or later.
        //
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);

        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        Set<UserAuthenticationType> type = Set.of();

        byte[] challenge = new byte[] {1, 2, 3};
        try {
            AndroidKeystoreCreateKeySettings settings =
                    new AndroidKeystoreCreateKeySettings.Builder(challenge)
                            .setUserAuthenticationRequired(true, 42, type)
                            .build();
            Assert.fail("Should not be reached");
        } catch (IllegalArgumentException e) {
            /* expected path */
        }
    }

    // Curve 25519 on Android is currently broken, see b/282063229 for details. Ignore test for now.
    @Ignore
    @Test
    public void testEcKeySigningEd25519() {
        // ECDH is only available on Android 12 or later (only HW-backed on Keymint 1.0 or later)
        //
        // Also note it's not available on StrongBox.
        //
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);

        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setEcCurve(EcCurve.ED25519)
                        .build();
        ks.createKey("testKey", settings);

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.ED25519, keyInfo.getPublicKey().getCurve());
        Assert.assertFalse(keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", Algorithm.EDDSA, dataToSign, null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        Assert.assertTrue(Crypto.checkSignature(keyInfo.getPublicKey(), dataToSign, Algorithm.EDDSA, derSignature));
    }

    @Test
    public void testEcKeySigningWithKeyWithoutCorrectPurpose() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_HARDWARE_KEYSTORE
        // ECDH is available if FEATURE_HARDWARE_KEYSTORE is >= 100.
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HARDWARE_KEYSTORE, 100));

        ks.createKey("testKey",
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3})
                        .setKeyPurposes(Set.of(KeyPurpose.AGREE_KEY))
                        .build());
        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", Algorithm.ES256, dataToSign, null);
            Assert.fail("Signing shouldn't work with a key w/o KEY_PURPOSE_SIGN");
        } catch (IllegalArgumentException e) {
            // Expected path.
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }
    }
    @Test
    public void testEcdh() {
        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_HARDWARE_KEYSTORE
        // ECDH is available if FEATURE_HARDWARE_KEYSTORE is >= 100.
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HARDWARE_KEYSTORE, 100));
        testEcdhHelper(false);
    }

    @Test
    public void testEcdhStrongBox() {
        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_STRONGBOX_KEYSTORE
        // ECDH is available if FEATURE_STRONGBOX_KEYSTORE is >= 100.
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_STRONGBOX_KEYSTORE, 100));
        testEcdhHelper(true);
    }

    public void testEcdhHelper(boolean useStrongBox) {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        EcPrivateKey otherKey = Crypto.createEcPrivateKey(EcCurve.P256);

        ks.createKey("testKey",
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3})
                        .setUseStrongBox(useStrongBox)
                        .setKeyPurposes(Set.of(KeyPurpose.AGREE_KEY))
                        .build());

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.AGREE_KEY), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKey.getPublicKey(), null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        // ...now do it from the perspective of the other side...
        byte[] theirSharedSecret = Crypto.keyAgreement(otherKey, keyInfo.getPublicKey());

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);
    }

    // Curve 25519 on Android is currently broken, see b/282063229 for details. Ignore test for now.
    @Ignore
    @Test
    public void testEcdhX25519() {
        // ECDH is only available on Android 12 or later (only HW-backed on Keymint 1.0 or later)
        //
        // Also note it's not available on StrongBox.
        //
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);

        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        EcPrivateKey otherKey = Crypto.createEcPrivateKey(EcCurve.X25519);

        ks.createKey("testKey",
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3})
                        .setKeyPurposes(Set.of(KeyPurpose.AGREE_KEY))
                        .setEcCurve(EcCurve.X25519)
                        .build());

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.AGREE_KEY), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.X25519, keyInfo.getPublicKey().getCurve());
        Assert.assertFalse(keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKey.getPublicKey(), null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        // ...now do it from the perspective of the other side...
        byte[] theirSharedSecret = Crypto.keyAgreement(otherKey, keyInfo.getPublicKey());

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);
    }

    @Test
    public void testEcdhAndSigning() {
        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_HARDWARE_KEYSTORE
        // ECDH is available if FEATURE_HARDWARE_KEYSTORE is >= 100.
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HARDWARE_KEYSTORE, 100));
        testEcdhAndSigningHelper(false);
    }

    @Test
    public void testEcdhAndSigningStrongBox() {
        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_STRONGBOX_KEYSTORE
        // ECDH is available if FEATURE_STRONGBOX_KEYSTORE is >= 100.
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_STRONGBOX_KEYSTORE, 100));
        testEcdhAndSigningHelper(true);
    }

    public void testEcdhAndSigningHelper(boolean useStrongBox) {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        EcPrivateKey otherKey = Crypto.createEcPrivateKey(EcCurve.P256);

        ks.createKey("testKey",
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3})
                        .setUseStrongBox(useStrongBox)
                        .setKeyPurposes(Set.of(KeyPurpose.AGREE_KEY, KeyPurpose.SIGN))
                        .build());

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.AGREE_KEY, KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKey.getPublicKey(), null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        // ...now do it from the perspective of the other side...
        byte[] theirSharedSecret = Crypto.keyAgreement(otherKey, keyInfo.getPublicKey());

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", Algorithm.ES256, dataToSign, null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        Assert.assertTrue(Crypto.checkSignature(keyInfo.getPublicKey(), dataToSign, Algorithm.ES256, derSignature));
    }

    @Test
    public void testEcdhWithoutCorrectPurpose() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        // According to https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_HARDWARE_KEYSTORE
        // ECDH is available if FEATURE_HARDWARE_KEYSTORE is >= 100.
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HARDWARE_KEYSTORE, 100));

        EcPrivateKey otherKey = Crypto.createEcPrivateKey(EcCurve.P256);

        ks.createKey("testKey",
                new AndroidKeystoreCreateKeySettings.Builder(new byte[] {1, 2, 3})
                        //.setKeyPurposes(Set.of(KeyPurpose.AGREE_KEY))
                        .build());

        try {
            ks.keyAgreement("testKey", otherKey.getPublicKey(), null);
            Assert.fail("ECDH shouldn't work with a key w/o KEY_PURPOSE_AGREE_KEY");
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        } catch (IllegalArgumentException e) {
            // Expected path.
        }
    }


    @Test
    public void testEcKeyCreationOverridesExistingAlias() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge).build();

        ks.createKey("testKey", settings);
        KeyInfo keyInfoOld = ks.getKeyInfo("testKey");
        Assert.assertTrue(keyInfoOld.getAttestation().getCertificates().size() >= 1);

        ks.createKey("testKey", settings);
        KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", Algorithm.ES256, dataToSign, null);
        } catch (KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Check new key is a different cert chain.
        Assert.assertNotEquals(
                keyInfoOld.getAttestation().getCertificates().get(0).getPublicKey(),
                keyInfo.getAttestation().getCertificates().get(0).getPublicKey());

        // Check new key is used to sign.
        Assert.assertTrue(Crypto.checkSignature(keyInfo.getPublicKey(), dataToSign, Algorithm.ES256, derSignature));
    }

    @Test
    public void testAttestation() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        testAttestationHelper(context, false);
    }

    @Test
    public void testAttestationStrongBox() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE));
        testAttestationHelper(context, true);
    }

    public void testAttestationHelper(Context context, boolean useStrongBox) throws IOException {
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        Calendar validFromCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        validFromCalendar.set(2023, 5, 15, 0, 0, 0);
        Calendar validUntilCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        validUntilCalendar.set(2024, 5, 15, 0, 0, 0);
        Timestamp validFrom = Timestamp.ofEpochMilli(validFromCalendar.getTimeInMillis());
        Timestamp validUntil = Timestamp.ofEpochMilli(validUntilCalendar.getTimeInMillis());

        byte[] challenge = new byte[] {1, 2, 3};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setUseStrongBox(useStrongBox)
                        .setValidityPeriod(validFrom, validUntil)
                        .build();

        ks.deleteKey("testKey");

        ks.createKey("testKey", settings);

        // On Android, at least three certificates are present in the chain.
        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 3);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertEquals(validFrom, keyInfo.getValidFrom());
        Assert.assertEquals(validUntil, keyInfo.getValidUntil());

        // Check the attestation extension
        AndroidAttestationExtensionParser parser =
                new AndroidAttestationExtensionParser(
                        CertificateKt.getJavaX509Certificate(keyInfo.getAttestation().getCertificates().get(0)));
        Assert.assertArrayEquals(challenge, parser.getAttestationChallenge());
        AndroidAttestationExtensionParser.SecurityLevel securityLevel = parser.getKeymasterSecurityLevel();
        Assert.assertEquals(
                useStrongBox ? AndroidAttestationExtensionParser.SecurityLevel.STRONG_BOX
                        : AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT, securityLevel);

        // tag 400: https://source.android.com/docs/security/features/keystore/tags#active_datetime
        Assert.assertEquals(validFrom.toEpochMilli(),
                parser.getSoftwareAuthorizationLong(400).get().longValue());

        // tag 401: https://source.android.com/docs/security/features/keystore/tags#origination_expire_datetime
        Assert.assertEquals(validUntil.toEpochMilli(),
                parser.getSoftwareAuthorizationLong(401).get().longValue());
    }

    @Test
    public void testAttestKey() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY));
        testAttestKeyHelper(context, false);
    }

    @Test
    public void testAttestKeyStrongBox() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_KEYSTORE_APP_ATTEST_KEY));
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE));
        testAttestKeyHelper(context, true);
    }

    public void testAttestKeyHelper(Context context, boolean useStrongBox) throws IOException {
        String attestKeyAlias = "icTestAttestKey";
        Certificate[] attestKeyCertificates;
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    attestKeyAlias,
                    KeyProperties.PURPOSE_ATTEST_KEY);
            builder.setAttestationChallenge(new byte[]{1, 2, 3});
            if (useStrongBox) {
                builder.setIsStrongBoxBacked(true);
            }
            kpg.initialize(builder.build());
            kpg.generateKeyPair();

            KeyStore aks = KeyStore.getInstance("AndroidKeyStore");
            aks.load(null);
            attestKeyCertificates = aks.getCertificateChain(attestKeyAlias);
        } catch (InvalidAlgorithmParameterException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException
                 | KeyStoreException
                 | CertificateException e) {
            throw new IllegalStateException("Error creating attest key", e);
        }

        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {4, 5, 6, 7};
        AndroidKeystoreCreateKeySettings settings =
                new AndroidKeystoreCreateKeySettings.Builder(challenge)
                        .setAttestKeyAlias(attestKeyAlias)
                        .setUseStrongBox(useStrongBox)
                        .build();

        ks.deleteKey("testKey");

        ks.createKey("testKey", settings);

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertEquals(useStrongBox, keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertTrue(keyInfo.getUserAuthenticationTypes().isEmpty());
        Assert.assertEquals(attestKeyAlias, keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        // When using an attest key, only one certificate is returned ...
        Assert.assertEquals(1, keyInfo.getAttestation().getCertificates().size());
        // ... and this certificate is signed by the attest key. Check that.
        Assert.assertTrue(
                keyInfo.getAttestation().getCertificates().get(0).verify(
                        EcPublicKeyKt.toEcPublicKey(attestKeyCertificates[0].getPublicKey(), EcCurve.P256))
                );

        // Check the attestation extension
        AndroidAttestationExtensionParser parser =
                new AndroidAttestationExtensionParser(
                        CertificateKt.getJavaX509Certificate(keyInfo.getAttestation().getCertificates().get(0)));
        Assert.assertArrayEquals(challenge, parser.getAttestationChallenge());
        AndroidAttestationExtensionParser.SecurityLevel securityLevel = parser.getKeymasterSecurityLevel();
        Assert.assertEquals(
                useStrongBox ? AndroidAttestationExtensionParser.SecurityLevel.STRONG_BOX
                        : AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT, securityLevel);
    }

    @Test
    public void testUsingGenericCreateKeySettings() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();
        AndroidKeystoreSecureArea ks = new AndroidKeystoreSecureArea(context, storageEngine);

        byte[] challenge = new byte[] {1, 2, 3, 4};
        ks.createKey("testKey", new CreateKeySettings(challenge, Set.of(KeyPurpose.SIGN), EcCurve.P256));

        AndroidKeystoreKeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());

        AndroidAttestationExtensionParser parser =
                new AndroidAttestationExtensionParser(
                        CertificateKt.getJavaX509Certificate(keyInfo.getAttestation().getCertificates().get(0)));
        Assert.assertArrayEquals(challenge, parser.getAttestationChallenge());

        // Now delete it...
        ks.deleteKey("testKey");
    }
}
