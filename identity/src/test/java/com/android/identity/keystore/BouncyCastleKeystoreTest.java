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

package com.android.identity.keystore;

import com.android.identity.storage.EphemeralStorageEngine;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import javax.crypto.KeyAgreement;

public class BouncyCastleKeystoreTest {

    private static final String TAG = "BouncyCastleKeystoreTest";

    @Before
    public void setup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testEcKeyDeletion() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        // First create the key...
        ks.createKey("testKey", new BouncyCastleKeystore.CreateKeySettings.Builder().build());
        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        List<X509Certificate> certChain = keyInfo.getAttestation();
        Assert.assertTrue(certChain.size() >= 1);

        // Now delete it...
        ks.deleteKey("testKey");

        // Now that we know the key doesn't exist, check that ecKeySign() throws
        try {
            ks.sign("testKey", KeystoreEngine.ALGORITHM_ES256, new byte[] {1, 2}, null);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // Expected path.
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Now delete it again, this should not fail.
        ks.deleteKey("testKey");
    }

    @Test
    public void testEcKeySigning() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        ks.createKey("testKey", new BouncyCastleKeystore.CreateKeySettings.Builder().build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());
        Assert.assertNull(keyInfo.getAttestationKeyAlias());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", KeystoreEngine.ALGORITHM_ES256, dataToSign, null);
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(keyInfo.getAttestation().get(0).getPublicKey());
            signature.update(dataToSign);
            Assert.assertTrue(signature.verify(derSignature));
        } catch (NoSuchAlgorithmException
                 | SignatureException
                 | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeyCreate() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        ks.createKey("testKey", new BouncyCastleKeystore.CreateKeySettings.Builder().build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());
        Assert.assertNull(keyInfo.getAttestationKeyAlias());

        // Check the leaf certificate is self-signed.
        try {
            keyInfo.getAttestation().get(0).verify(keyInfo.getAttestation().get(0).getPublicKey());
        } catch (CertificateException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeyCreateWithAttestationKey() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        ks.createKey("attestationKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder().build());
        BouncyCastleKeystore.KeyInfo aKeyInfo = ks.getKeyInfo("attestationKey");
        List<X509Certificate> attestationKeyCertChain = aKeyInfo.getAttestation();
        Assert.assertTrue(attestationKeyCertChain.size() >= 1);

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .setAttestationKeyAlias("attestationKey")
                        .build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());
        Assert.assertEquals("attestationKey", keyInfo.getAttestationKeyAlias());

        // Check the leaf certificate is signed by attestationKey.
        try {
            keyInfo.getAttestation().get(0).verify(attestationKeyCertChain.get(0).getPublicKey());
        } catch (CertificateException
                 | InvalidKeyException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException
                 | SignatureException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeySigningWithKeyWithoutCorrectPurpose() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        ks.createKey("attestationKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .build());

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .setKeyPurposes(KeystoreEngine.KEY_PURPOSE_AGREE_KEY)
                        .setAttestationKeyAlias("attestationKey")
                        .build());
        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", KeystoreEngine.ALGORITHM_ES256, dataToSign, null);
            Assert.fail("Signing shouldn't work with a key w/o KEY_PURPOSE_SIGN");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Key does not have purpose KEY_PURPOSE_SIGN", e.getMessage());
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcdh() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("attestationKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .build());

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .setKeyPurposes(KeystoreEngine.KEY_PURPOSE_AGREE_KEY)
                        .setAttestationKeyAlias("attestationKey")
                        .build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());
        Assert.assertEquals("attestationKey", keyInfo.getAttestationKeyAlias());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // ...now do it from the perspective of the other side...
        byte[] theirSharedSecret;
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(otherKeyPair.getPrivate());
            ka.doPhase(keyInfo.getAttestation().get(0).getPublicKey(), true);
            theirSharedSecret = ka.generateSecret();
        } catch (NoSuchAlgorithmException
                 | InvalidKeyException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);
    }

    @Test
    public void testEcdhAndSigning() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .setKeyPurposes(KeystoreEngine.KEY_PURPOSE_AGREE_KEY
                                | KeystoreEngine.KEY_PURPOSE_SIGN)
                        .build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN
                | KeystoreEngine.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());
        Assert.assertNull(keyInfo.getAttestationKeyAlias());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // ...now do it from the perspective of the other side...
        byte[] theirSharedSecret;
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(otherKeyPair.getPrivate());
            ka.doPhase(keyInfo.getAttestation().get(0).getPublicKey(), true);
            theirSharedSecret = ka.generateSecret();
        } catch (NoSuchAlgorithmException
                 | InvalidKeyException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        // ... finally, check that both sides compute the same shared secret.
        Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", KeystoreEngine.ALGORITHM_ES256, dataToSign, null);
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(keyInfo.getAttestation().get(0).getPublicKey());
            signature.update(dataToSign);
            Assert.assertTrue(signature.verify(derSignature));
        } catch (NoSuchAlgorithmException
                 | SignatureException
                 | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcdhWithoutCorrectPurpose() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        //.setKeyPurpose(KeystoreEngine.KEY_PURPOSE_AGREE_KEY)
                        .build());

        try {
            ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
            Assert.fail("ECDH shouldn't work with a key w/o KEY_PURPOSE_AGREE_KEY");
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Key does not have purpose KEY_PURPOSE_AGREE_KEY", e.getMessage());
        }

    }

    @Test
    public void testEcKeySigningWithLockedKey() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        String passphrase = "verySekrit";
        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .setPassphraseRequired(true, passphrase)
                        .build());

        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(KeystoreEngine.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertTrue(keyInfo.isPassphraseProtected());
        Assert.assertNull(keyInfo.getAttestationKeyAlias());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature = new byte[0];
        try {
            derSignature = ks.sign("testKey",
                    KeystoreEngine.ALGORITHM_ES256,
                    dataToSign,
                    null);
            Assert.fail();
        } catch (KeystoreEngine.KeyLockedException e) {
            // This is the expected path.
        }

        // Try with the wrong passphrase. This should fail.
        try {
            derSignature = ks.sign("testKey",
                    KeystoreEngine.ALGORITHM_ES256,
                    dataToSign,
                    new BouncyCastleKeystore.KeyUnlockData("wrongPassphrase"));
            Assert.fail();
        } catch (KeystoreEngine.KeyLockedException e) {
            // This is the expected path.
        }

        // ... and with the right passphrase. This should work.
        try {
            derSignature = ks.sign("testKey",
                    KeystoreEngine.ALGORITHM_ES256,
                    dataToSign,
                    new BouncyCastleKeystore.KeyUnlockData(passphrase));
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Verify the signature is correct.
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(keyInfo.getAttestation().get(0).getPublicKey());
            signature.update(dataToSign);
            Assert.assertTrue(signature.verify(derSignature));
        } catch (NoSuchAlgorithmException
                 | SignatureException
                 | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeyCreationOverridesExistingAlias() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder().build());
        BouncyCastleKeystore.KeyInfo keyInfoOld = ks.getKeyInfo("testKey");
        List<X509Certificate> certChainOld = keyInfoOld.getAttestation();
        Assert.assertTrue(certChainOld.size() >= 1);

        ks.createKey("testKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder().build());
        BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        List<X509Certificate> certChain = keyInfo.getAttestation();
        Assert.assertTrue(certChain.size() >= 1);
        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature = new byte[0];
        try {
            derSignature = ks.sign("testKey", KeystoreEngine.ALGORITHM_ES256, dataToSign, null);
        } catch (KeystoreEngine.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Check new key is a different cert chain.
        Assert.assertNotEquals(
                certChainOld.get(0).getPublicKey().getEncoded(),
                certChain.get(0).getPublicKey().getEncoded());

        // Check new key is used to sign.
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(certChain.get(0).getPublicKey());
            signature.update(dataToSign);
            Assert.assertTrue(signature.verify(derSignature));
        } catch (NoSuchAlgorithmException
                 | SignatureException
                 | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeySigningAllCurves() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        int[] knownEcCurves = new int[] {
                KeystoreEngine.EC_CURVE_P256,
                KeystoreEngine.EC_CURVE_P384,
                KeystoreEngine.EC_CURVE_P521,
                KeystoreEngine.EC_CURVE_BRAINPOOLP256R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP320R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP384R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP512R1,
                // TODO: Edwards curve keys requires work in how private key is saved/loaded
                //KeystoreEngine.EC_CURVE_ED25519,
                //KeystoreEngine.EC_CURVE_ED448,
        };

        for (@KeystoreEngine.EcCurve int ecCurve : knownEcCurves) {
            ks.createKey("testKey",
                    new BouncyCastleKeystore.CreateKeySettings.Builder()
                            .setEcCurve(ecCurve)
                            .build());

            BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
            Assert.assertNotNull(keyInfo);
            Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
            Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
            Assert.assertEquals(ecCurve, keyInfo.getEcCurve());
            Assert.assertFalse(keyInfo.isHardwareBacked());
            Assert.assertFalse(keyInfo.isPassphraseProtected());
            Assert.assertNull(keyInfo.getAttestationKeyAlias());

            @KeystoreEngine.Algorithm int[] signatureAlgorithms = new int[0];
            switch (ecCurve) {
                case KeystoreEngine.EC_CURVE_P256:
                case KeystoreEngine.EC_CURVE_P384:
                case KeystoreEngine.EC_CURVE_P521:
                case KeystoreEngine.EC_CURVE_BRAINPOOLP256R1:
                case KeystoreEngine.EC_CURVE_BRAINPOOLP320R1:
                case KeystoreEngine.EC_CURVE_BRAINPOOLP384R1:
                case KeystoreEngine.EC_CURVE_BRAINPOOLP512R1:
                    signatureAlgorithms = new int[] {
                            KeystoreEngine.ALGORITHM_ES256,
                            KeystoreEngine.ALGORITHM_ES384,
                            KeystoreEngine.ALGORITHM_ES512};
                    break;

                case KeystoreEngine.EC_CURVE_ED25519:
                case KeystoreEngine.EC_CURVE_ED448:
                    signatureAlgorithms = new int[] {KeystoreEngine.ALGORITHM_EDDSA};
                    break;

                default:
                    Assert.fail();
            }

            for (@KeystoreEngine.Algorithm int signatureAlgorithm : signatureAlgorithms){
                byte[] dataToSign = new byte[]{4, 5, 6};
                byte[] derSignature = null;
                try {
                    derSignature = ks.sign("testKey", signatureAlgorithm, dataToSign, null);
                } catch (KeystoreEngine.KeyLockedException e) {
                    throw new AssertionError(e);
                }

                String signatureAlgorithmName = "";
                switch (signatureAlgorithm) {
                    case KeystoreEngine.ALGORITHM_ES256:
                        signatureAlgorithmName = "SHA256withECDSA";
                        break;
                    case KeystoreEngine.ALGORITHM_ES384:
                        signatureAlgorithmName = "SHA384withECDSA";
                        break;
                    case KeystoreEngine.ALGORITHM_ES512:
                        signatureAlgorithmName = "SHA512withECDSA";
                        break;
                    case KeystoreEngine.ALGORITHM_EDDSA:
                        if (ecCurve == KeystoreEngine.EC_CURVE_ED25519) {
                            signatureAlgorithmName = "Ed25519";
                        } else if (ecCurve == KeystoreEngine.EC_CURVE_ED448) {
                            signatureAlgorithmName = "Ed448";
                        } else {
                            Assert.fail("ALGORITHM_EDDSA can only be used with "
                                    + "EC_CURVE_ED_25519 and EC_CURVE_ED_448");
                        }
                        break;
                    default:
                        Assert.fail("Unsupported signing algorithm  with id " + signatureAlgorithm);
                }

                try {
                    Signature signature = Signature.getInstance(signatureAlgorithmName);
                    signature.initVerify(keyInfo.getAttestation().get(0).getPublicKey());
                    signature.update(dataToSign);
                    Assert.assertTrue(signature.verify(derSignature));
                } catch (NoSuchAlgorithmException
                         | SignatureException
                         | InvalidKeyException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    @Test
    public void testEcKeyEcdhAllCurves() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        BouncyCastleKeystore ks = new BouncyCastleKeystore(storage);

        // Because we're using curves that cannot be used for signatures we need to
        // create an key used to sign the key attestations
        ks.createKey("attestationKey",
                new BouncyCastleKeystore.CreateKeySettings.Builder()
                        .build());

        int[] knownEcCurves = new int[] {
                KeystoreEngine.EC_CURVE_P256,
                KeystoreEngine.EC_CURVE_P384,
                KeystoreEngine.EC_CURVE_P521,
                KeystoreEngine.EC_CURVE_BRAINPOOLP256R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP320R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP384R1,
                KeystoreEngine.EC_CURVE_BRAINPOOLP512R1,
                // TODO: Edwards curve keys requires work in how private key is saved/loaded
                //KeystoreEngine.EC_CURVE_X25519,
                //KeystoreEngine.EC_CURVE_X448,
        };

        for (@KeystoreEngine.EcCurve int ecCurve : knownEcCurves) {
            KeyPair otherKeyPair;
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                switch (ecCurve) {
                    case KeystoreEngine.EC_CURVE_P256:
                        kpg.initialize(new ECGenParameterSpec("secp256r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_P384:
                        kpg.initialize(new ECGenParameterSpec("secp384r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_P521:
                        kpg.initialize(new ECGenParameterSpec("secp521r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_BRAINPOOLP256R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_BRAINPOOLP320R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP320r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_BRAINPOOLP384R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP384r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_BRAINPOOLP512R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP512r1"));
                        break;
                    case KeystoreEngine.EC_CURVE_X25519:
                        kpg = KeyPairGenerator.getInstance("x25519", new BouncyCastleProvider());
                        break;
                    case KeystoreEngine.EC_CURVE_X448:
                        kpg = KeyPairGenerator.getInstance("x448", new BouncyCastleProvider());
                        break;
                    default:
                        throw new AssertionError("Unsupported curve " + ecCurve);
                }
                otherKeyPair = kpg.generateKeyPair();
            } catch (InvalidAlgorithmParameterException |
                     NoSuchAlgorithmException e) {
                throw new AssertionError("Unexpected exception", e);
            }

            ks.createKey("testKey",
                    new BouncyCastleKeystore.CreateKeySettings.Builder()
                            .setKeyPurposes(KeystoreEngine.KEY_PURPOSE_AGREE_KEY)
                            .setEcCurve(ecCurve)
                            .setAttestationKeyAlias("attestationKey")
                            .build());

            BouncyCastleKeystore.KeyInfo keyInfo = ks.getKeyInfo("testKey");
            Assert.assertNotNull(keyInfo);
            Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
            Assert.assertEquals(KeystoreEngine.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
            Assert.assertEquals(ecCurve, keyInfo.getEcCurve());
            Assert.assertFalse(keyInfo.isHardwareBacked());
            Assert.assertFalse(keyInfo.isPassphraseProtected());
            Assert.assertEquals("attestationKey", keyInfo.getAttestationKeyAlias());

            // First do the ECDH from the perspective of our side...
            byte[] ourSharedSecret;
            try {
                ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
            } catch (KeystoreEngine.KeyLockedException e) {
                throw new AssertionError(e);
            }

            // ...now do it from the perspective of the other side...
            byte[] theirSharedSecret;
            try {
                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                ka.init(otherKeyPair.getPrivate());
                ka.doPhase(keyInfo.getAttestation().get(0).getPublicKey(), true);
                theirSharedSecret = ka.generateSecret();
            } catch (NoSuchAlgorithmException
                     | InvalidKeyException e) {
                throw new AssertionError("Unexpected exception", e);
            }

            // ... finally, check that both sides compute the same shared secret.
            Assert.assertArrayEquals(theirSharedSecret, ourSharedSecret);
        }
    }
}
