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

package com.android.identity.securearea;

import androidx.annotation.NonNull;

import com.android.identity.storage.EphemeralStorageEngine;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.KeyAgreement;

public class SoftwareSecureAreaTest {

    private static final String TAG = "SoftwareSecureAreaTest";

    PrivateKey mAttestationKey;
    String mAttestationKeySignatureAlgorithm;
    List<X509Certificate> mAttestationKeyCertification;

    @Before
    public void setup() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        // Create an attestation key...
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair attestationKeyPair = kpg.generateKeyPair();
            mAttestationKey = attestationKeyPair.getPrivate();
            mAttestationKeySignatureAlgorithm = "SHA256withECDSA";

            long nowMillis = System.currentTimeMillis();
            JcaX509v3CertificateBuilder certBuilder =
                    new JcaX509v3CertificateBuilder(new X500Name("CN=Test Attestation Key"),
                            BigInteger.ONE,
                            new Date(nowMillis),
                            new Date(nowMillis + 24*3600*1000),
                            new X500Name("CN=Test Attestation Key"),
                            attestationKeyPair.getPublic());
            ContentSigner signer;
            signer = new JcaContentSignerBuilder("SHA256withECDSA")
                    .build(attestationKeyPair.getPrivate());
            byte[] encodedCert = certBuilder.build(signer).getEncoded();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream certBais = new ByteArrayInputStream(encodedCert);
            mAttestationKeyCertification = new ArrayList<>();
            mAttestationKeyCertification.add((X509Certificate) cf.generateCertificate(certBais));

        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | IOException |
                 CertificateException | OperatorCreationException | NoSuchProviderException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeyDeletion() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        // First create the key...
        ks.createKey("testKey", new SecureArea.CreateKeySettings(new byte[0]));
        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        List<X509Certificate> certChain = keyInfo.getAttestation();
        Assert.assertTrue(certChain.size() >= 1);

        // Now delete it...
        ks.deleteKey("testKey");

        // Now that we know the key doesn't exist, check that ecKeySign() throws
        try {
            ks.sign("testKey", SecureArea.ALGORITHM_ES256, new byte[] {1, 2}, null);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // Expected path.
        } catch (SecureArea.KeyLockedException e) {
            throw new AssertionError(e);
        }

        // Now delete it again, this should not fail.
        ks.deleteKey("testKey");
    }

    @Test
    public void testEcKeySigning() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        ks.createKey("testKey", new SecureArea.CreateKeySettings(new byte[0]));

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature;
        try {
            derSignature = ks.sign("testKey", SecureArea.ALGORITHM_ES256, dataToSign, null);
        } catch (SecureArea.KeyLockedException e) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        ks.createKey("testKey", new SecureArea.CreateKeySettings(new byte[0]));

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

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

    public static @NonNull
    byte[] getChallenge(@NonNull X509Certificate cert) {
        byte[] octetString = cert.getExtensionValue(AttestationExtension.ATTESTATION_OID);
        try {
            ASN1InputStream asn1InputStream = new ASN1InputStream(octetString);
            byte[] encodedCbor = ((ASN1OctetString) asn1InputStream.readObject()).getOctets();
            return AttestationExtension.decode(encodedCbor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEcKeyCreateWithAttestationKey() throws SecureArea.KeyLockedException {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        byte[] challenge = new byte[] {1, 2, 3};
        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(challenge)
                        .setAttestationKey(mAttestationKey,
                                mAttestationKeySignatureAlgorithm,
                                mAttestationKeyCertification)
                        .build());

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 2);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

        // Check challenge.
        Assert.assertArrayEquals(challenge, getChallenge(keyInfo.getAttestation().get(0)));

        // Check the leaf certificate is signed by mAttestationKey.
        try {
            keyInfo.getAttestation().get(0).verify(mAttestationKeyCertification.get(0).getPublicKey());
        } catch (CertificateException
                 | InvalidKeyException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException
                 | SignatureException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcKeyWithGenericCreateKeySettings() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        byte[] challenge = new byte[] {1, 2, 3};
        ks.createKey("testKey", new SecureArea.CreateKeySettings(challenge));

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

        // Check challenge.
        Assert.assertArrayEquals(challenge, getChallenge(keyInfo.getAttestation().get(0)));
    }

    @Test
    public void testEcKeySigningWithKeyWithoutCorrectPurpose() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                        .setKeyPurposes(SecureArea.KEY_PURPOSE_AGREE_KEY)
                        .build());
        byte[] dataToSign = new byte[] {4, 5, 6};
        try {
            ks.sign("testKey", SecureArea.ALGORITHM_ES256, dataToSign, null);
            Assert.fail("Signing shouldn't work with a key w/o KEY_PURPOSE_SIGN");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Key does not have purpose KEY_PURPOSE_SIGN", e.getMessage());
        } catch (SecureArea.KeyLockedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testEcdh() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                        .setKeyPurposes(SecureArea.KEY_PURPOSE_AGREE_KEY)
                        .build());

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
        } catch (SecureArea.KeyLockedException e) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                        .setKeyPurposes(SecureArea.KEY_PURPOSE_AGREE_KEY
                                | SecureArea.KEY_PURPOSE_SIGN)
                        .build());

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN
                | SecureArea.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertFalse(keyInfo.isPassphraseProtected());

        // First do the ECDH from the perspective of our side...
        byte[] ourSharedSecret;
        try {
            ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
        } catch (SecureArea.KeyLockedException e) {
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
            derSignature = ks.sign("testKey", SecureArea.ALGORITHM_ES256, dataToSign, null);
        } catch (SecureArea.KeyLockedException e) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        KeyPair otherKeyPair;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            otherKeyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Unexpected exception", e);
        }

        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                        //.setKeyPurpose(SecureArea.KEY_PURPOSE_AGREE_KEY)
                        .build());

        try {
            ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
            Assert.fail("ECDH shouldn't work with a key w/o KEY_PURPOSE_AGREE_KEY");
        } catch (SecureArea.KeyLockedException e) {
            throw new AssertionError(e);
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Key does not have purpose KEY_PURPOSE_AGREE_KEY", e.getMessage());
        }

    }

    @Test
    public void testEcKeySigningWithLockedKey() {
        EphemeralStorageEngine storage = new EphemeralStorageEngine();
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        String passphrase = "verySekrit";
        ks.createKey("testKey",
                new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                        .setPassphraseRequired(true, passphrase)
                        .build());

        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        Assert.assertNotNull(keyInfo);
        Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
        Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
        Assert.assertEquals(SecureArea.EC_CURVE_P256, keyInfo.getEcCurve());
        Assert.assertFalse(keyInfo.isHardwareBacked());
        Assert.assertTrue(keyInfo.isPassphraseProtected());

        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature = new byte[0];
        try {
            derSignature = ks.sign("testKey",
                    SecureArea.ALGORITHM_ES256,
                    dataToSign,
                    null);
            Assert.fail();
        } catch (SecureArea.KeyLockedException e) {
            // This is the expected path.
        }

        // Try with the wrong passphrase. This should fail.
        try {
            derSignature = ks.sign("testKey",
                    SecureArea.ALGORITHM_ES256,
                    dataToSign,
                    new SoftwareSecureArea.KeyUnlockData("wrongPassphrase"));
            Assert.fail();
        } catch (SecureArea.KeyLockedException e) {
            // This is the expected path.
        }

        // ... and with the right passphrase. This should work.
        try {
            derSignature = ks.sign("testKey",
                    SecureArea.ALGORITHM_ES256,
                    dataToSign,
                    new SoftwareSecureArea.KeyUnlockData(passphrase));
        } catch (SecureArea.KeyLockedException e) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        ks.createKey("testKey",
                new SecureArea.CreateKeySettings(new byte[0]));
        SoftwareSecureArea.KeyInfo keyInfoOld = ks.getKeyInfo("testKey");
        List<X509Certificate> certChainOld = keyInfoOld.getAttestation();
        Assert.assertTrue(certChainOld.size() >= 1);

        ks.createKey("testKey",
                new SecureArea.CreateKeySettings(new byte[0]));
        SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
        List<X509Certificate> certChain = keyInfo.getAttestation();
        Assert.assertTrue(certChain.size() >= 1);
        byte[] dataToSign = new byte[] {4, 5, 6};
        byte[] derSignature = new byte[0];
        try {
            derSignature = ks.sign("testKey", SecureArea.ALGORITHM_ES256, dataToSign, null);
        } catch (SecureArea.KeyLockedException e) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        int[] knownEcCurves = new int[] {
                SecureArea.EC_CURVE_P256,
                SecureArea.EC_CURVE_P384,
                SecureArea.EC_CURVE_P521,
                SecureArea.EC_CURVE_BRAINPOOLP256R1,
                SecureArea.EC_CURVE_BRAINPOOLP320R1,
                SecureArea.EC_CURVE_BRAINPOOLP384R1,
                SecureArea.EC_CURVE_BRAINPOOLP512R1,
                // TODO: Edwards curve keys requires work in how private key is saved/loaded
                //SecureArea.EC_CURVE_ED25519,
                //SecureArea.EC_CURVE_ED448,
        };

        for (@SecureArea.EcCurve int ecCurve : knownEcCurves) {
            ks.createKey("testKey",
                    new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                            .setEcCurve(ecCurve)
                            .build());

            SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
            Assert.assertNotNull(keyInfo);
            Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
            Assert.assertEquals(SecureArea.KEY_PURPOSE_SIGN, keyInfo.getKeyPurposes());
            Assert.assertEquals(ecCurve, keyInfo.getEcCurve());
            Assert.assertFalse(keyInfo.isHardwareBacked());
            Assert.assertFalse(keyInfo.isPassphraseProtected());

            @SecureArea.Algorithm int[] signatureAlgorithms = new int[0];
            switch (ecCurve) {
                case SecureArea.EC_CURVE_P256:
                case SecureArea.EC_CURVE_P384:
                case SecureArea.EC_CURVE_P521:
                case SecureArea.EC_CURVE_BRAINPOOLP256R1:
                case SecureArea.EC_CURVE_BRAINPOOLP320R1:
                case SecureArea.EC_CURVE_BRAINPOOLP384R1:
                case SecureArea.EC_CURVE_BRAINPOOLP512R1:
                    signatureAlgorithms = new int[] {
                            SecureArea.ALGORITHM_ES256,
                            SecureArea.ALGORITHM_ES384,
                            SecureArea.ALGORITHM_ES512};
                    break;

                case SecureArea.EC_CURVE_ED25519:
                case SecureArea.EC_CURVE_ED448:
                    signatureAlgorithms = new int[] {SecureArea.ALGORITHM_EDDSA};
                    break;

                default:
                    Assert.fail();
            }

            for (@SecureArea.Algorithm int signatureAlgorithm : signatureAlgorithms){
                byte[] dataToSign = new byte[]{4, 5, 6};
                byte[] derSignature = null;
                try {
                    derSignature = ks.sign("testKey", signatureAlgorithm, dataToSign, null);
                } catch (SecureArea.KeyLockedException e) {
                    throw new AssertionError(e);
                }

                String signatureAlgorithmName = "";
                switch (signatureAlgorithm) {
                    case SecureArea.ALGORITHM_ES256:
                        signatureAlgorithmName = "SHA256withECDSA";
                        break;
                    case SecureArea.ALGORITHM_ES384:
                        signatureAlgorithmName = "SHA384withECDSA";
                        break;
                    case SecureArea.ALGORITHM_ES512:
                        signatureAlgorithmName = "SHA512withECDSA";
                        break;
                    case SecureArea.ALGORITHM_EDDSA:
                        if (ecCurve == SecureArea.EC_CURVE_ED25519) {
                            signatureAlgorithmName = "Ed25519";
                        } else if (ecCurve == SecureArea.EC_CURVE_ED448) {
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
        SoftwareSecureArea ks = new SoftwareSecureArea(storage);

        int[] knownEcCurves = new int[] {
                SecureArea.EC_CURVE_P256,
                SecureArea.EC_CURVE_P384,
                SecureArea.EC_CURVE_P521,
                SecureArea.EC_CURVE_BRAINPOOLP256R1,
                SecureArea.EC_CURVE_BRAINPOOLP320R1,
                SecureArea.EC_CURVE_BRAINPOOLP384R1,
                SecureArea.EC_CURVE_BRAINPOOLP512R1,
                // TODO: Edwards curve keys requires work in how private key is saved/loaded
                //SecureArea.EC_CURVE_X25519,
                //SecureArea.EC_CURVE_X448,
        };

        for (@SecureArea.EcCurve int ecCurve : knownEcCurves) {
            KeyPair otherKeyPair;
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                switch (ecCurve) {
                    case SecureArea.EC_CURVE_P256:
                        kpg.initialize(new ECGenParameterSpec("secp256r1"));
                        break;
                    case SecureArea.EC_CURVE_P384:
                        kpg.initialize(new ECGenParameterSpec("secp384r1"));
                        break;
                    case SecureArea.EC_CURVE_P521:
                        kpg.initialize(new ECGenParameterSpec("secp521r1"));
                        break;
                    case SecureArea.EC_CURVE_BRAINPOOLP256R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
                        break;
                    case SecureArea.EC_CURVE_BRAINPOOLP320R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP320r1"));
                        break;
                    case SecureArea.EC_CURVE_BRAINPOOLP384R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP384r1"));
                        break;
                    case SecureArea.EC_CURVE_BRAINPOOLP512R1:
                        kpg.initialize(new ECGenParameterSpec("brainpoolP512r1"));
                        break;
                    case SecureArea.EC_CURVE_X25519:
                        kpg = KeyPairGenerator.getInstance("x25519", BouncyCastleProvider.PROVIDER_NAME);
                        break;
                    case SecureArea.EC_CURVE_X448:
                        kpg = KeyPairGenerator.getInstance("x448", BouncyCastleProvider.PROVIDER_NAME);
                        break;
                    default:
                        throw new AssertionError("Unsupported curve " + ecCurve);
                }
                otherKeyPair = kpg.generateKeyPair();
            } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException |
                     NoSuchProviderException e) {
                throw new AssertionError("Unexpected exception", e);
            }

            ks.createKey("testKey",
                    new SoftwareSecureArea.CreateKeySettings.Builder(new byte[0])
                            .setKeyPurposes(SecureArea.KEY_PURPOSE_AGREE_KEY)
                            .setEcCurve(ecCurve)
                            .build());

            SoftwareSecureArea.KeyInfo keyInfo = ks.getKeyInfo("testKey");
            Assert.assertNotNull(keyInfo);
            Assert.assertTrue(keyInfo.getAttestation().size() >= 1);
            Assert.assertEquals(SecureArea.KEY_PURPOSE_AGREE_KEY, keyInfo.getKeyPurposes());
            Assert.assertEquals(ecCurve, keyInfo.getEcCurve());
            Assert.assertFalse(keyInfo.isHardwareBacked());
            Assert.assertFalse(keyInfo.isPassphraseProtected());

            // First do the ECDH from the perspective of our side...
            byte[] ourSharedSecret;
            try {
                ourSharedSecret = ks.keyAgreement("testKey", otherKeyPair.getPublic(), null);
            } catch (SecureArea.KeyLockedException e) {
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
