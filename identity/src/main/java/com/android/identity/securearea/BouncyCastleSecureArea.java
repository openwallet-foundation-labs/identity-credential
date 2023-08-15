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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.storage.StorageEngine;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * An implementation of {@link SecureArea} using the
 * <a href="https://www.bouncycastle.org/">Bouncy Castle</a> library.
 *
 * <p>This implementation supports all the curves and algorithms defined by {@link SecureArea}
 * and also supports passphrase-protected keys. Key material is stored using the
 * {@link StorageEngine} abstraction and passphrase-protected keys are encrypted using
 * <a href="https://en.wikipedia.org/wiki/Galois/Counter_Mode">AES-GCM</a>
 * with 256-bit keys with the key derived from the passphrase using
 * <a href="https://en.wikipedia.org/wiki/HKDF">HKDF</a>.
 */
public class BouncyCastleSecureArea implements SecureArea {
    private static final String TAG = "BouncyCastleSA";  // limit to <= 23 chars
    private final StorageEngine mStorageEngine;

    // Prefix for storage items.
    private static final String PREFIX = "IC_BouncyCastleSA_";

    /**
     * Creates a new Bouncy Castle secure area.
     *
     * @param storageEngine the storage engine to use for storing key material.
     */
    public BouncyCastleSecureArea(@NonNull StorageEngine storageEngine) {
        mStorageEngine = storageEngine;
    }

    @Override
    public void createKey(@NonNull String alias,
                          @NonNull SecureArea.CreateKeySettings createKeySettings) {
        CreateKeySettings settings = (CreateKeySettings) createKeySettings;
        ArrayList<X509Certificate> certificateChain = new ArrayList<>();

        KeyPairGenerator kpg;
        String certSigningSignatureAlgorithm;
        try {
            kpg = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
            switch (settings.getEcCurve()) {
                case EC_CURVE_P256:
                    kpg.initialize(new ECGenParameterSpec("secp256r1"));
                    certSigningSignatureAlgorithm = "SHA256withECDSA";
                    break;
                case EC_CURVE_P384:
                    kpg.initialize(new ECGenParameterSpec("secp384r1"));
                    certSigningSignatureAlgorithm = "SHA384withECDSA";
                    break;
                case EC_CURVE_P521:
                    kpg.initialize(new ECGenParameterSpec("secp521r1"));
                    certSigningSignatureAlgorithm = "SHA512withECDSA";
                    break;
                case EC_CURVE_BRAINPOOLP256R1:
                    kpg.initialize(new ECGenParameterSpec("brainpoolP256r1"));
                    certSigningSignatureAlgorithm = "SHA256withECDSA";
                    break;
                case EC_CURVE_BRAINPOOLP320R1:
                    kpg.initialize(new ECGenParameterSpec("brainpoolP320r1"));
                    certSigningSignatureAlgorithm = "SHA256withECDSA";
                    break;
                case EC_CURVE_BRAINPOOLP384R1:
                    kpg.initialize(new ECGenParameterSpec("brainpoolP384r1"));
                    certSigningSignatureAlgorithm = "SHA384withECDSA";
                    break;
                case EC_CURVE_BRAINPOOLP512R1:
                    kpg.initialize(new ECGenParameterSpec("brainpoolP512r1"));
                    certSigningSignatureAlgorithm = "SHA512withECDSA";
                    break;
                case EC_CURVE_ED25519:
                    kpg = KeyPairGenerator.getInstance("Ed25519", new BouncyCastleProvider());
                    certSigningSignatureAlgorithm = "Ed25519";
                    break;
                case EC_CURVE_ED448:
                    kpg = KeyPairGenerator.getInstance("Ed448", new BouncyCastleProvider());
                    certSigningSignatureAlgorithm = "Ed448";
                    break;
                case EC_CURVE_X25519:
                    kpg = KeyPairGenerator.getInstance("x25519", new BouncyCastleProvider());
                    certSigningSignatureAlgorithm = "Ed25519";
                    break;
                case EC_CURVE_X448:
                    kpg = KeyPairGenerator.getInstance("x448", new BouncyCastleProvider());
                    certSigningSignatureAlgorithm = "Ed448";
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown curve with id " + settings.getEcCurve());
            }

            KeyPair keyPair = kpg.generateKeyPair();
            CborBuilder builder = new CborBuilder();
            MapBuilder<CborBuilder> map = builder.addMap();
            map.put("curve", settings.getEcCurve());
            map.put("keyPurposes", settings.getKeyPurposes());
            String attestationKeyAlias = settings.getAttestationKeyAlias();
            if (attestationKeyAlias != null) {
                map.put("attestationKeyAlias", attestationKeyAlias);
            }
            map.put("passphraseRequired", settings.getPassphraseRequired());
            if (!settings.getPassphraseRequired()) {
                map.put("privateKey", keyPair.getPrivate().getEncoded());
            } else {
                byte[] cleartextPrivateKey = keyPair.getPrivate().getEncoded();
                SecretKey secretKey = derivePrivateKeyEncryptionKey(keyPair.getPublic().getEncoded(),
                        settings.getPassphrase());
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    baos.write(cipher.getIV());
                    baos.write(cipher.doFinal(cleartextPrivateKey));
                    byte[] encryptedPrivateKey = baos.toByteArray();
                    map.put("publicKey", keyPair.getPublic().getEncoded());
                    map.put("encryptedPrivateKey", encryptedPrivateKey);
                } catch (NoSuchPaddingException
                        | IllegalBlockSizeException
                        | BadPaddingException
                        | InvalidKeyException e) {
                    throw new IllegalStateException("Error encrypting private key", e);
                }
            }

            X500Name issuer = new X500Name("CN=Android Identity Credential BC KS Impl");
            X500Name subject = new X500Name("CN=Android Identity Credential BC KS Impl");
            Date now = new Date();
            Date expirationDate = new Date(now.getTime() + MILLISECONDS.convert(365, DAYS));
            BigInteger serial = BigInteger.ONE;
            JcaX509v3CertificateBuilder certBuilder =
                    new JcaX509v3CertificateBuilder(issuer,
                            serial,
                            now,
                            expirationDate,
                            subject,
                            keyPair.getPublic());
            ContentSigner signer;
            if (attestationKeyAlias == null) {
                if ((settings.getKeyPurposes() & KEY_PURPOSE_SIGN) == 0) {
                    throw new IllegalArgumentException(
                            "Cannot self-sign certificate for a key without signing purpose. "
                            + "Use an attestation key.");
                }
                signer = new JcaContentSignerBuilder(certSigningSignatureAlgorithm)
                        .build(keyPair.getPrivate());
            } else {
                signer = getSignerForAlias(attestationKeyAlias);
            }
            byte[] encodedCert = certBuilder.build(signer).getEncoded();

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bais = new ByteArrayInputStream(encodedCert);
            certificateChain.add((X509Certificate) cf.generateCertificate(bais));

            ArrayBuilder<MapBuilder<CborBuilder>> attestationBuilder = map.putArray("attestation");
            for (X509Certificate certificate : certificateChain) {
                try {
                    attestationBuilder.add(certificate.getEncoded());
                } catch (CertificateEncodingException e) {
                    throw new IllegalStateException("Error encoding certificate chain", e);
                }
            }
            attestationBuilder.end();

            mStorageEngine.put(PREFIX + alias, Util.cborEncode(builder.build().get(0)));
        } catch (NoSuchAlgorithmException
                 | CertificateException
                 | InvalidAlgorithmParameterException
                 | OperatorCreationException
                 | IOException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private @NonNull ContentSigner getSignerForAlias(@NonNull String attestationKeyAlias) {
        KeyData attestationKeyData;
        try {
            attestationKeyData = loadKey(attestationKeyAlias, null);
        } catch (KeyLockedException e) {
            // TODO: we might revisit this and make it possible to pass KeyUnlockData
            //  for the attestation key through CreateKeySettings and make ecKeyCreate
            //  throw.
            throw new IllegalArgumentException("Attestation key cannot be locked");
        }

        if ((attestationKeyData.keyPurposes & KEY_PURPOSE_SIGN) == 0) {
            throw new IllegalArgumentException(
                    "Cannot sign certificate using a key without signing purpose.");
        }

        // Use the "natural" signature algorithm for the curve...
        String signatureAlgorithmName;
        switch (attestationKeyData.curve) {
            case SecureArea.EC_CURVE_P256:
                signatureAlgorithmName = "SHA256withECDSA";
                break;
            case SecureArea.EC_CURVE_P384:
                signatureAlgorithmName = "SHA384withECDSA";
                break;
            case SecureArea.EC_CURVE_P521:
                signatureAlgorithmName = "SHA512withECDSA";
                break;
            case SecureArea.EC_CURVE_BRAINPOOLP256R1:
                signatureAlgorithmName = "SHA256withECDSA";
                break;
            case SecureArea.EC_CURVE_BRAINPOOLP320R1:
                signatureAlgorithmName = "SHA256withECDSA";
                break;
            case SecureArea.EC_CURVE_BRAINPOOLP384R1:
                signatureAlgorithmName = "SHA384withECDSA";
                break;
            case SecureArea.EC_CURVE_BRAINPOOLP512R1:
                signatureAlgorithmName = "SHA512withECDSA";
                break;
            case SecureArea.EC_CURVE_ED25519:
                signatureAlgorithmName = "EdDSA";
                break;
            case SecureArea.EC_CURVE_ED448:
                signatureAlgorithmName = "EdDSA";
                break;

            default:
            case SecureArea.EC_CURVE_X25519:
            case SecureArea.EC_CURVE_X448:
                throw new IllegalStateException("Invalid curve " + attestationKeyData.curve
                        + "for attestation signing");
        }
        try {
            return new JcaContentSignerBuilder(signatureAlgorithmName)
                    .build(attestationKeyData.privateKey);
        } catch (OperatorCreationException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private SecretKey derivePrivateKeyEncryptionKey(@NonNull byte[] encodedPublicKey,
                                                    @NonNull String passphrase) {
        // TODO: replace with Argon2
        byte[] info = "ICPrivateKeyEncryption1".getBytes(StandardCharsets.UTF_8);
        byte[] derivedKey = Util.computeHkdf("HmacSha256",
                passphrase.getBytes(StandardCharsets.UTF_8),
                encodedPublicKey,
                info,
                32);
        return new SecretKeySpec(derivedKey, "AES");
    }

    @Override
    public void deleteKey(@NonNull String alias) {
        mStorageEngine.delete(PREFIX + alias);
    }

    private static class KeyData {
        @EcCurve int curve;
        @KeyPurpose int keyPurposes;
        PrivateKey privateKey;
    }

    private @NonNull KeyData loadKey(@NonNull String alias,
                                     @Nullable SecureArea.KeyUnlockData keyUnlockData)
            throws KeyLockedException {
        KeyData ret = new KeyData();

        String passphrase = null;
        if (keyUnlockData != null) {
            BouncyCastleSecureArea.KeyUnlockData unlockData = (BouncyCastleSecureArea.KeyUnlockData) keyUnlockData;
            passphrase = unlockData.mPassphrase;
        }

        byte[] data = mStorageEngine.get(PREFIX + alias);
        if (data == null) {
            throw new IllegalArgumentException("No key with given alias");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error decoded CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalStateException("Expected 1 item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalStateException("Item is not a map");
        }
        co.nstant.in.cbor.model.Map map = (co.nstant.in.cbor.model.Map) dataItems.get(0);
        ret.curve = (int) Util.cborMapExtractNumber(map, "curve");
        ret.keyPurposes = (int) Util.cborMapExtractNumber(map, "keyPurposes");

        byte[] encodedPrivateKey;
        boolean passphraseRequired = Util.cborMapExtractBoolean(map, "passphraseRequired");
        if (passphraseRequired) {
            if (passphrase == null) {
                throw new KeyLockedException("No passphrase provided");
            }
            KeyUnlockData keyUD = (KeyUnlockData) keyUnlockData;
            byte[] encodedPublicKey = Util.cborMapExtractByteString(map, "publicKey");
            byte[] encryptedPrivateKey = Util.cborMapExtractByteString(map, "encryptedPrivateKey");
            SecretKey secretKey = derivePrivateKeyEncryptionKey(encodedPublicKey, passphrase);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

                ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedPrivateKey);
                byte[] iv = new byte[12];
                byteBuffer.get(iv);
                byte[] cipherText = new byte[encryptedPrivateKey.length - 12];
                byteBuffer.get(cipherText);

                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                encodedPrivateKey = cipher.doFinal(cipherText);
            } catch (NoSuchAlgorithmException
                     | NoSuchPaddingException
                     | IllegalBlockSizeException
                     | BadPaddingException
                     | InvalidKeyException
                     | InvalidAlgorithmParameterException e) {
                throw new KeyLockedException("Error decrypting private key", e);
            }
        } else {
            encodedPrivateKey = Util.cborMapExtractByteString(map, "privateKey");
        }

        PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
        try {
            KeyFactory ecKeyFac = KeyFactory.getInstance("EC", new BouncyCastleProvider());
            ret.privateKey = ecKeyFac.generatePrivate(encodedKeySpec);
        } catch (NoSuchAlgorithmException |
                 InvalidKeySpecException e) {
            throw new IllegalStateException("Error loading private key", e);
        }
        return ret;
    }

    @NonNull
    @Override
    public byte[] sign(@NonNull String alias,
                       @Algorithm int signatureAlgorithm,
                       @NonNull byte[] dataToSign,
                       @Nullable SecureArea.KeyUnlockData keyUnlockData)
            throws SecureArea.KeyLockedException {
        KeyData keyData = loadKey(alias, keyUnlockData);
        if ((keyData.keyPurposes & KEY_PURPOSE_SIGN) == 0) {
            throw new IllegalArgumentException("Key does not have purpose KEY_PURPOSE_SIGN");
        }

        String signatureAlgorithmName;
        switch (signatureAlgorithm) {
            case ALGORITHM_ES256:
                signatureAlgorithmName = "SHA256withECDSA";
                break;
            case ALGORITHM_ES384:
                signatureAlgorithmName = "SHA384withECDSA";
                break;
            case ALGORITHM_ES512:
                signatureAlgorithmName = "SHA512withECDSA";
                break;
            case ALGORITHM_EDDSA:
                if (keyData.curve == EC_CURVE_ED25519) {
                    signatureAlgorithmName = "Ed25519";
                } else if (keyData.curve == EC_CURVE_ED448) {
                    signatureAlgorithmName = "Ed448";
                } else {
                    throw new IllegalArgumentException("ALGORITHM_EDDSA can only be used with "
                            + "EC_CURVE_ED_25519 and EC_CURVE_ED_448");
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported signing algorithm  with id " + signatureAlgorithm);
        }

        try {
            Signature s = Signature.getInstance(signatureAlgorithmName);
            s.initSign(keyData.privateKey);
            s.update(dataToSign);
            return s.sign();
        } catch (NoSuchAlgorithmException
                 | SignatureException
                 | InvalidKeyException e) {
            throw new IllegalStateException("Unexpected Exception", e);
        }
    }

    @Override
    public @NonNull byte[] keyAgreement(@NonNull String alias,
                                        @NonNull PublicKey otherKey,
                                        @Nullable SecureArea.KeyUnlockData keyUnlockData)
            throws KeyLockedException {
        KeyData keyData = loadKey(alias, keyUnlockData);
        if ((keyData.keyPurposes & KEY_PURPOSE_AGREE_KEY) == 0) {
            throw new IllegalArgumentException("Key does not have purpose KEY_PURPOSE_AGREE_KEY");
        }

        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(keyData.privateKey);
            ka.doPhase(otherKey, true);
            return ka.generateSecret();
        } catch (NoSuchAlgorithmException
                 | InvalidKeyException e) {
            throw new IllegalStateException("Unexpected Exception", e);
        }
    }

    /**
     * Bouncy Castle specific class for information about an EC key.
     */
    public static class KeyInfo extends SecureArea.KeyInfo {

        private final boolean mIsPassphraseProtected;
        private final @Nullable String mAttestationKeyAlias;

        KeyInfo(@NonNull List<X509Certificate> attestation,
                @KeyPurpose int keyPurposes, @EcCurve int ecCurve, boolean isHardwareBacked,
                boolean isPassphraseProtected, @Nullable String attestationKeyAlias) {
            super(attestation, keyPurposes, ecCurve, isHardwareBacked);
            mIsPassphraseProtected = isPassphraseProtected;
            mAttestationKeyAlias = attestationKeyAlias;
        }

        /**
         * Gets whether the key is passphrase protected.
         *
         * @return {@code true} if passphrase protected, {@code false} otherwise.
         */
        public boolean isPassphraseProtected() {
            return mIsPassphraseProtected;
        }

        /**
         * Gets the attestation key alias, if any.
         *
         * @return the attestation key alias or {@code null} if not using an attestation key.
         */
        public @Nullable String getAttestationKeyAlias() {
            return mAttestationKeyAlias;
        }
    }

    @Override
    public @NonNull KeyInfo getKeyInfo(@NonNull String alias) {
        byte[] data = mStorageEngine.get(PREFIX + alias);
        if (data == null) {
            throw new IllegalArgumentException("No key with given alias");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error decoded CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalStateException("Expected 1 item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalStateException("Item is not a map");
        }
        co.nstant.in.cbor.model.Map map = (co.nstant.in.cbor.model.Map) dataItems.get(0);
        @EcCurve int ecCurve = (int) Util.cborMapExtractNumber(map, "curve");
        @KeyPurpose int keyPurposes = (int) Util.cborMapExtractNumber(map, "keyPurposes");
        boolean passphraseRequired = Util.cborMapExtractBoolean(map, "passphraseRequired");
        String attestationKeyAlias = null;
        if (Util.cborMapHasKey(map, "attestationKeyAlias")) {
            attestationKeyAlias = Util.cborMapExtractString(map, "attestationKeyAlias");
        }

        DataItem attestationDataItem = map.get(new UnicodeString("attestation"));
        if (!(attestationDataItem instanceof Array)) {
            throw new IllegalStateException("attestation not found or not array");
        }
        List<X509Certificate> attestation = new ArrayList<>();
        for (DataItem item : ((Array) attestationDataItem).getDataItems()) {
            byte[] encodedCert = ((ByteString) item).getBytes();
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                ByteArrayInputStream certBais = new ByteArrayInputStream(encodedCert);
                attestation.add((X509Certificate) cf.generateCertificate(certBais));
            } catch (CertificateException e) {
                throw new IllegalStateException("Error decoding certificate blob", e);
            }
        }

        return new KeyInfo(attestation, keyPurposes, ecCurve, false,
                passphraseRequired, attestationKeyAlias);
    }

    /**
     * A class that can be used to provide information used for unlocking a key.
     *
     * <p>Currently only passphrases are supported.
     */
    public static class KeyUnlockData implements SecureArea.KeyUnlockData {
        private final String mPassphrase;

        /**
         * Constructs a new object used for unlocking a key.
         *
         * @param passphrase the passphrase.
         */
        public KeyUnlockData(@NonNull String passphrase) {
            mPassphrase = passphrase;
        }
    }

    /**
     * Class used to indicate key creation settings.
     */
    public static class CreateKeySettings extends SecureArea.CreateKeySettings {
        private final String mAttestationKeyAlias;
        private @KeyPurpose int mKeyPurposes;
        private final @EcCurve int mEcCurve;
        private final boolean mPassphraseRequired;
        private final String mPassphrase;

        private CreateKeySettings(boolean passphraseRequired,
                                  @NonNull String passphrase,
                                  @EcCurve int ecCurve,
                                  @KeyPurpose int keyPurposes,
                                  @Nullable String attestationKeyAlias) {
            super(BouncyCastleSecureArea.class);
            mPassphraseRequired = passphraseRequired;
            mPassphrase = passphrase;
            mEcCurve = ecCurve;
            mKeyPurposes = keyPurposes;
            mAttestationKeyAlias = attestationKeyAlias;
        }

        /**
         * Gets whether the key is protected by a passphrase.
         *
         * @return Whether the key is protected by a passphrase.
         */
        public boolean getPassphraseRequired() {
            return mPassphraseRequired;
        }

        /**
         * Gets the passphrase for the key.
         *
         * @return The passphrase for the key.
         */
        public @NonNull String getPassphrase() {
            return mPassphrase;
        }

        /**
         * Gets the curve used.
         *
         * @return the curve used.
         */
        public @EcCurve int getEcCurve() {
            return mEcCurve;
        }

        /**
         * Gets the key purposes.
         *
         * @return the key purposes.
         */
        public @KeyPurpose int getKeyPurposes() {
            return mKeyPurposes;
        }

        /**
         * Gets the attestation key alias, if any.
         *
         * @return The attestation key alias or {@code null}.
         */
        public @Nullable String getAttestationKeyAlias() {
            return mAttestationKeyAlias;
        }


        /**
         * A builder for {@link CreateKeySettings}.
         */
        public static class Builder {
            private @KeyPurpose int mKeyPurposes = KEY_PURPOSE_SIGN;
            private @EcCurve int mEcCurve = EC_CURVE_P256;
            private boolean mPassphraseRequired;
            private String mPassphrase = "";
            private String mAttestationKeyAlias;

            /**
             * Sets the key purpose.
             *
             * <p>By default the key purpose is {@link SecureArea#KEY_PURPOSE_SIGN}.
             *
             * @param keyPurposes one or more purposes.
             * @return the builder.
             * @throws IllegalArgumentException if no purpose is set.
             */
            public @NonNull Builder setKeyPurposes(@KeyPurpose int keyPurposes) {
                if (keyPurposes == 0) {
                    throw new IllegalArgumentException("Purpose cannot be empty");
                }
                mKeyPurposes = keyPurposes;
                return this;
            }

            /**
             * Sets the curve to use for EC keys.
             *
             * <p>By default {@link SecureArea#EC_CURVE_P256} is used.
             *
             * @param curve the curve to use.
             * @return the builder.
             */
            public @NonNull Builder setEcCurve(@EcCurve int curve) {
                mEcCurve = curve;
                return this;
            }

            /**
             * Sets the passphrase required to use a key.
             *
             * @param required whether a passphrase is required.
             * @param passphrase the passphrase to use, must not be {@code null} if
             *                   {@code required} is {@code true}.
             * @return the builder.
             */
            public @NonNull Builder setPassphraseRequired(boolean required, @Nullable String passphrase) {
                if (mPassphraseRequired && passphrase == null) {
                    throw new IllegalStateException("Passphrase cannot be null if it's required");
                }
                mPassphraseRequired = required;
                mPassphrase = passphrase;
                return this;
            }

            /**
             * Sets the alias of a key to use sign the returned X509 certificate.
             *
             * <p>If this is not set, the key itself will be used to sign the X509 certificate
             * which will only work if the curve used can be used for signing.
             *
             * <p>By default this isn't set.
             *
             * @param attestationKeyAlias the attest key alias or {@code null}.
             * @return the builder.
             */
            public @NonNull Builder setAttestationKeyAlias(@Nullable String attestationKeyAlias) {
                mAttestationKeyAlias = attestationKeyAlias;
                return this;
            }

            /**
             * Builds the {@link CreateKeySettings}.
             *
             * @return a new {@link CreateKeySettings}.
             */
            public @NonNull CreateKeySettings build() {
                return new CreateKeySettings(mPassphraseRequired, mPassphrase, mEcCurve,
                        mKeyPurposes, mAttestationKeyAlias);
            }
        }
    }
}
