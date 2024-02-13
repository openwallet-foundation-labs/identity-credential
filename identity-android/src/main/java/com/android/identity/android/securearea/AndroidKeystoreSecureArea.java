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

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.FeatureInfo;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;

import com.android.identity.internal.Util;
import com.android.identity.securearea.Algorithm;
import com.android.identity.securearea.EcCurve;
import com.android.identity.securearea.KeyLockedException;
import com.android.identity.securearea.KeyPurpose;
import com.android.identity.securearea.SecureArea;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Logger;
import com.android.identity.util.Timestamp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.KeyAgreement;

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
 * An implementation of {@link SecureArea} using Android Keystore.
 *
 * <p>Keys created using this implementation are hardware-backed, that is the private key
 * material is designed to never leave Secure Hardware. In this context Secure Hardware
 * can mean either the TEE (Trusted Execution Environment) or an SE (Secure Element), specifically
 * anything meeting the definition of an <em>Isolated Execution Environment</em> as per
 * <a href="https://source.android.com/docs/compatibility/13/android-13-cdd#911_keys_and_credentials">
 * section 9.11 of the Android CDD</a>.
 *
 * <p>Any key created will be attested to by the Secure Hardware, using
 * <a href="https://developer.android.com/training/articles/security-key-attestation">Android
 * Keystore Key Attestation</a>. This gives remote parties (such as real-world identity credential
 * issuers) a high level of assurance that the private part of the key exists only in Secure
 * Hardware and also gives a strong signal about the general state of the device (including whether
 * <a href="https://source.android.com/docs/security/features/verifiedboot">verified boot</a>
 * is enabled, latest patch level, etc.) and which particular Android application (identified by
 * <a href="https://developer.android.com/build/configure-app-module#set_the_application_id">
 * Application Id</a>) created the key.
 *
 * <p>Curve {@link EcCurve#P256} for signing using algorithm
 * {@link Algorithm#ES256} is guaranteed to be implemented in
 * Secure Hardware on any Android device shipping with Android 8.1 or later. As of 2023
 * this includes nearly all Android devices.
 *
 * <p>If the device has a <a href="https://source.android.com/docs/compatibility/13/android-13-cdd#9112_strongbox">
 * StrongBox Android Keystore</a>, keys can be stored there using
 * {@link CreateKeySettings.Builder#setUseStrongBox(boolean)}.
 *
 * <p>Other optional features may be available depending on the version of the underlying
 * software (called <a href="https://source.android.com/docs/security/features/keystore">Keymint</a>)
 * running in the Secure Area. The {@link Capabilities} helper class can be used to determine
 * what the device supports.
 *
 * <p>This implementation works only on Android and requires API level 24 or later.
 */
public class AndroidKeystoreSecureArea implements SecureArea {
    /**
     * The Secure Area identifier for the Android Keystore Secure Area.
     */
    public static final String SECURE_AREA_IDENTIFIER = "AndroidKeystoreSecureArea";

    private static final String TAG = "AndroidKeystoreSA";  // limit to <= 23 chars
    private final Context mContext;
    private final StorageEngine mStorageEngine;

    // Prefix used for storage items, the key alias follows.
    private static final String PREFIX = "IC_AndroidKeystore_";

    /**
     * Flag indicating that authentication is needed using the user's Lock-Screen Knowledge Factor.
     */
    public static final int USER_AUTHENTICATION_TYPE_LSKF = 1<<0;

    /**
     * Flag indicating that authentication is needed using the user's biometric.
     */
    public static final int USER_AUTHENTICATION_TYPE_BIOMETRIC = 1<<1;
    private final int mKeymintTeeFeatureLevel;
    private final int mKeymintSbFeatureLevel;

    /**
     * An annotation used to indicate different types of user authentication.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {
                    USER_AUTHENTICATION_TYPE_LSKF,
                    USER_AUTHENTICATION_TYPE_BIOMETRIC
            })
    @interface UserAuthenticationType {}

    /**
     * Constructs a new {@link AndroidKeystoreSecureArea}.
     *
     * @param context the application context.
     * @param storageEngine the storage engine to use for storing metadata about keys.
     */
    public AndroidKeystoreSecureArea(@NonNull Context context,
                                     @NonNull StorageEngine storageEngine) {
        mContext = context;
        mStorageEngine = storageEngine;
        mKeymintTeeFeatureLevel = getFeatureVersionKeystore(context, false);
        mKeymintSbFeatureLevel = getFeatureVersionKeystore(context, true);
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return SECURE_AREA_IDENTIFIER;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Android Keystore Secure Area";
    }

    @Override
    public void createKey(@NonNull String alias,
                          @NonNull com.android.identity.securearea.CreateKeySettings createKeySettings) {
        CreateKeySettings aSettings;
        if (createKeySettings instanceof CreateKeySettings) {
            aSettings = (CreateKeySettings) createKeySettings;
        } else {
            // Use default settings if user passed in a generic SecureArea.CreateKeySettings.
            aSettings = new AndroidKeystoreSecureArea.CreateKeySettings.Builder(
                    createKeySettings.getAttestationChallenge())
                    .build();
        }

        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

            int purposes = 0;
            if (aSettings.getKeyPurposes().contains(KeyPurpose.SIGN)) {
                purposes |= KeyProperties.PURPOSE_SIGN;
            }
            if (aSettings.getKeyPurposes().contains(KeyPurpose.AGREE_KEY)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    purposes |= KeyProperties.PURPOSE_AGREE_KEY;
                } else {
                    throw new IllegalArgumentException(
                            "PURPOSE_AGREE_KEY not supported on this device");
                }

                // Android KeyStore tries to be "helpful" by creating keys in Software if
                // the Secure World (Keymint) lacks support for the requested feature, for
                // example ECDH. This will never work (for RWI, the credential issuer will
                // detect it when examining the attestation) so just bail early if this
                // is the case.
                if (aSettings.getUseStrongBox()) {
                    if (mKeymintSbFeatureLevel < 100) {
                        throw new IllegalArgumentException("PURPOSE_AGREE_KEY not supported on " +
                                "this StrongBox KeyMint version");
                    }
                } else {
                    if (mKeymintTeeFeatureLevel < 100) {
                        throw new IllegalArgumentException("PURPOSE_AGREE_KEY not supported on " +
                                "this KeyMint version");
                    }
                }
            }

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias, purposes);
            switch (aSettings.getEcCurve()) {
                case P256:
                    // Works with both purposes.
                    builder.setDigests(KeyProperties.DIGEST_SHA256);
                    break;

                case ED25519:
                    // Only works with KEY_PURPOSE_SIGN
                    if (!aSettings.getKeyPurposes().contains(KeyPurpose.SIGN)) {
                        throw new IllegalArgumentException(
                                "Curve Ed25519 only works with purpose SIGN");
                    }
                    builder.setAlgorithmParameterSpec(new ECGenParameterSpec("ed25519"));
                    break;

                case X25519:
                    // Only works with KEY_PURPOSE_AGREE_KEY
                    if (!aSettings.getKeyPurposes().contains(KeyPurpose.AGREE_KEY)) {
                        throw new IllegalArgumentException(
                                "Curve X25519 only works with purpose AGREE_KEY");
                    }
                    builder.setAlgorithmParameterSpec(new ECGenParameterSpec("x25519"));
                    break;

                case BRAINPOOLP256R1:
                case BRAINPOOLP320R1:
                case BRAINPOOLP384R1:
                case BRAINPOOLP512R1:
                case ED448:
                case P384:
                case P521:
                case X448:
                default:
                    throw new IllegalArgumentException("Curve is not supported");
            }

            if (aSettings.getUserAuthenticationRequired()) {
                builder.setUserAuthenticationRequired(true);
                long timeoutMillis = aSettings.getUserAuthenticationTimeoutMillis();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @UserAuthenticationType int userAuthenticationType = aSettings.getUserAuthenticationType();
                    int type = 0;
                    if ((userAuthenticationType & USER_AUTHENTICATION_TYPE_LSKF) != 0) {
                        type |= KeyProperties.AUTH_DEVICE_CREDENTIAL;
                    }
                    if ((userAuthenticationType & USER_AUTHENTICATION_TYPE_BIOMETRIC) != 0) {
                        type |= KeyProperties.AUTH_BIOMETRIC_STRONG;
                    }
                    if (timeoutMillis == 0) {
                        builder.setUserAuthenticationParameters(0, type);
                    } else {
                        int timeoutSeconds = (int) Math.max(1, timeoutMillis/1000);
                        builder.setUserAuthenticationParameters(timeoutSeconds, type);
                    }
                } else {
                    if (timeoutMillis == 0) {
                        builder.setUserAuthenticationValidityDurationSeconds(-1);
                    } else {
                        int timeoutSeconds = (int) Math.max(1, timeoutMillis / 1000);
                        builder.setUserAuthenticationValidityDurationSeconds(timeoutSeconds);
                    }
                }
                builder.setInvalidatedByBiometricEnrollment(false);

            }
            if (aSettings.getUseStrongBox()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setIsStrongBoxBacked(true);
                }
            }
            if (aSettings.getAttestKeyAlias() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAttestKeyAlias(aSettings.getAttestKeyAlias());
                }
            }
            builder.setAttestationChallenge(aSettings.getAttestationChallenge());

            if (aSettings.getValidFrom() != null) {
                Date notBefore = new Date(aSettings.getValidFrom().toEpochMilli());
                Date notAfter = new Date(aSettings.getValidUntil().toEpochMilli());
                builder.setKeyValidityStart(notBefore);
                builder.setCertificateNotBefore(notBefore);
                builder.setKeyValidityEnd(notAfter);
                builder.setCertificateNotAfter(notAfter);
            }

            try {
                kpg.initialize(builder.build());
            } catch (InvalidAlgorithmParameterException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException
                 | NoSuchProviderException e) {
            throw new IllegalStateException("Error creating key", e);
        }

        List<X509Certificate> attestation = new ArrayList<>();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            Certificate[] certificates = ks.getCertificateChain(alias);
            for (Certificate certificate : certificates) {
                attestation.add((X509Certificate) certificate);
            }
        } catch (CertificateException
                | KeyStoreException
                | IOException
                | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error generate certificate chain", e);
        }
        Logger.d(TAG, "EC key with alias '" + alias + "' created");

        saveKeyMetadata(alias, aSettings, attestation);
    }

    /**
     * Creates a key for an existing Android KeyStore key.
     *
     * <p>This doesn't actually create a key but creates the out-of-band data
     * structures so an existing Android KeyStore key can be used with e.g.
     * {@link #getKeyInfo(String)},
     * {@link #sign(String, Algorithm, byte[], com.android.identity.securearea.KeyUnlockData)},
     * {@link #keyAgreement(String, PublicKey, com.android.identity.securearea.KeyUnlockData)}
     * and other methods.
     *
     * @param existingAlias the alias of the existing key.
     */
    public void createKeyForExistingAlias(@NonNull String existingAlias) {
        android.security.keystore.KeyInfo keyInfo = null;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(existingAlias, null);
            if (entry == null) {
                throw new IllegalArgumentException("No entry for alias");
            }
            PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

            KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
            try {
                keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo.class);
            } catch (InvalidKeySpecException e) {
                throw new IllegalStateException("Given key is not an Android Keystore key", e);
            }
        } catch (UnrecoverableEntryException
                 | CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        // Need to generate the data which getKeyInfo() reads from disk.
        CreateKeySettings.Builder settingsBuilder =
                new CreateKeySettings.Builder("".getBytes(StandardCharsets.UTF_8));

        // attestation
        List<X509Certificate> attestation = new ArrayList<>();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            Certificate[] certificates = ks.getCertificateChain(existingAlias);
            for (Certificate certificate : certificates) {
                attestation.add((X509Certificate) certificate);
            }
        } catch (CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error generating certificate chain", e);
        }

        // curve - not available in KeyInfo, assume P-256

        // keyPurposes
        Set<KeyPurpose> purposes = new LinkedHashSet<>();
        int ksPurposes = keyInfo.getPurposes();
        if ((ksPurposes & KeyProperties.PURPOSE_SIGN) != 0) {
            purposes.add(KeyPurpose.SIGN);
        }
        if ((ksPurposes & KeyProperties.PURPOSE_AGREE_KEY) != 0) {
            purposes.add(KeyPurpose.AGREE_KEY);
        }
        settingsBuilder.setKeyPurposes(purposes);

        // useStrongBox
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            settingsBuilder.setUseStrongBox(keyInfo.getSecurityLevel() == KeyProperties.SECURITY_LEVEL_STRONGBOX);
        }

        // attestKeyAlias - not available in KeyInfo

        // userAuthentication*
        @UserAuthenticationType int userAuthenticationType = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int ksAuthType = keyInfo.getUserAuthenticationType();
            if ((ksAuthType & KeyProperties.AUTH_DEVICE_CREDENTIAL) != 0) {
                userAuthenticationType |= USER_AUTHENTICATION_TYPE_LSKF;
            }
            if ((ksAuthType & KeyProperties.AUTH_BIOMETRIC_STRONG) != 0) {
                userAuthenticationType |= USER_AUTHENTICATION_TYPE_BIOMETRIC;
            }
        } else {
            userAuthenticationType = AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF +
                    AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC;
        }
        settingsBuilder.setUserAuthenticationRequired(
                keyInfo.isUserAuthenticationRequired(),
                keyInfo.getUserAuthenticationValidityDurationSeconds()*1000L,
                userAuthenticationType);

        saveKeyMetadata(existingAlias, settingsBuilder.build(), attestation);
        Logger.d(TAG, "EC existing key with alias '" + existingAlias + "' created");
    }

    @Override
    public void deleteKey(@NonNull String alias) {
        KeyStore ks;
        KeyStore.Entry entry;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(alias)) {
                Logger.w(TAG, "Key with alias '" + alias + "' doesn't exist");
                return;
            }
            ks.deleteEntry(alias);
            mStorageEngine.delete(PREFIX + alias);
        } catch (CertificateException
                 | IOException
                 | NoSuchAlgorithmException
                 | KeyStoreException e) {
            throw new IllegalStateException("Error loading keystore", e);
        }
        Logger.d(TAG, "EC key with alias '" + alias + "' deleted");
    }

    static String getSignatureAlgorithmName(Algorithm signatureAlgorithm) {
        switch (signatureAlgorithm) {
            case ES256:
                return "SHA256withECDSA";

            case ES384:
                return "SHA384withECDSA";

            case ES512:
                return "SHA512withECDSA";

            case EDDSA:
                return "Ed25519";

            default:
                throw new IllegalArgumentException(
                        "Unsupported signing algorithm with id " + signatureAlgorithm);
        }
    }

    @Override
    public @NonNull byte[] sign(@NonNull String alias,
                                Algorithm signatureAlgorithm,
                                @NonNull byte[] dataToSign,
                                @Nullable com.android.identity.securearea.KeyUnlockData keyUnlockData)
            throws KeyLockedException {
        if (keyUnlockData != null) {
            KeyUnlockData unlockData = (KeyUnlockData) keyUnlockData;
            if (!unlockData.mAlias.equals(alias)) {
                throw new IllegalArgumentException(
                        String.format("keyUnlockData has alias %s which differs"
                                        + " from passed-in alias %s",
                                unlockData.mAlias,
                                alias));
            }
            if (unlockData.mSignature != null) {
                if (unlockData.mSignatureAlgorithm != signatureAlgorithm) {
                    throw new IllegalArgumentException(
                            String.format("keyUnlockData has signature algorithm %d which differs"
                            + " from passed-in algorithm %d",
                                    unlockData.mSignatureAlgorithm,
                                    signatureAlgorithm));
                }
                try {
                    unlockData.mSignature.update(dataToSign);
                    return unlockData.mSignature.sign();
                } catch (SignatureException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        }

        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(alias, null);
            if (entry == null) {
                throw new IllegalArgumentException("No entry for alias");
            }
            PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
            Signature s = Signature.getInstance(getSignatureAlgorithmName(signatureAlgorithm));
            s.initSign(privateKey);
            s.update(dataToSign);
            return s.sign();
        } catch (UserNotAuthenticatedException e) {
            throw new KeyLockedException("User not authenticated", e);
        } catch (UnrecoverableEntryException
                 | CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException
                 | SignatureException e) {
            // This is a work-around for Android Keystore throwing a SignatureException
            // when it should be throwing UserNotAuthenticatedException instead. b/282174161
            //
            if (e instanceof SignatureException &&
                    ((SignatureException) e).getMessage().startsWith(
                            "android.security.KeyStoreException: Key user not authenticated")) {
                throw new KeyLockedException("User not authenticated", e);
            }
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public @NonNull byte[] keyAgreement(@NonNull String alias,
                                        @NonNull PublicKey otherKey,
                                        @Nullable com.android.identity.securearea.KeyUnlockData keyUnlockData)
            throws KeyLockedException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(alias, null);
            if (entry == null) {
                throw new IllegalArgumentException("No entry for alias");
            }
            PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
            KeyAgreement ka = KeyAgreement.getInstance("ECDH", "AndroidKeyStore");
            ka.init(privateKey);
            ka.doPhase(otherKey, true);
            return ka.generateSecret();
        } catch (UserNotAuthenticatedException e) {
            throw new KeyLockedException("User not authenticated", e);
        } catch (UnrecoverableEntryException
                 | CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (ProviderException e) {
            // This is a work-around for Android Keystore throwing a ProviderException
            // when it should be throwing UserNotAuthenticatedException instead. b/282174161
            //
            if (e.getCause() != null
                && e.getCause().getMessage().startsWith("Key user not authenticated")) {
                throw new KeyLockedException("User not authenticated", e);
            }
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * A class that can be used to provide information used for unlocking a key.
     *
     * <p>Currently only user-authentication is supported.
     */
    public static class KeyUnlockData implements com.android.identity.securearea.KeyUnlockData {

        private Signature mSignature;
        private final String mAlias;
        private BiometricPrompt.CryptoObject mCryptoObjectForSigning;
        private Algorithm mSignatureAlgorithm;

        /**
         * Constructs a new object used for unlocking a key.
         *
         * @param alias the alias of the key to unlock.
         */
        public KeyUnlockData(@NonNull String alias) {
            mAlias = alias;
        }

        /**
         * Gets a {@link BiometricPrompt.CryptoObject} for signing data.
         *
         * <p>This can be used with {@link BiometricPrompt} to unlock the key.
         * On successful authentication, this object should be passed to
         * {@link AndroidKeystoreSecureArea#sign(String, Algorithm, byte[], com.android.identity.securearea.KeyUnlockData)}.
         *
         * <p>Note that a {@link BiometricPrompt.CryptoObject} is returned only if the key is
         * configured to require authentication for every use of the key, that is, when the
         * key was created with a zero timeout as per
         * {@link AndroidKeystoreSecureArea.CreateKeySettings.Builder#setUserAuthenticationRequired(boolean, long, int)}.
         *
         * @param signatureAlgorithm the signature algorithm to use.
         * @return A {@link BiometricPrompt.CryptoObject} or {@code null}.
         */
        public @Nullable BiometricPrompt.CryptoObject getCryptoObjectForSigning(Algorithm signatureAlgorithm) {
            if (mCryptoObjectForSigning != null) {
                return mCryptoObjectForSigning;
            }
            try {
                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                KeyStore.Entry entry = ks.getEntry(mAlias, null);
                if (entry == null) {
                    throw new IllegalArgumentException("No entry for alias");
                }
                PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

                KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
                try {
                    android.security.keystore.KeyInfo keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo.class);
                    if (keyInfo.getUserAuthenticationValidityDurationSeconds() > 0) {
                        // Key is not auth-per-op, no CryptoObject required.
                        return null;
                    }
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException("Given key is not an Android Keystore key", e);
                }

                mSignature = Signature.getInstance(getSignatureAlgorithmName(signatureAlgorithm));
                mSignature.initSign(privateKey);
                mCryptoObjectForSigning = new BiometricPrompt.CryptoObject(mSignature);
                mSignatureAlgorithm = signatureAlgorithm;
                return mCryptoObjectForSigning;
            } catch (UnrecoverableEntryException
                     | CertificateException
                     | KeyStoreException
                     | IOException
                     | NoSuchAlgorithmException
                     | InvalidKeyException
                     | NoSuchProviderException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        /**
         * Gets a {@link BiometricPrompt.CryptoObject} for ECDH.
         *
         * <p>This can be used with {@link BiometricPrompt} to unlock the key.
         * On successful authentication, this object should be passed to
         * {@link AndroidKeystoreSecureArea#keyAgreement(String, PublicKey, com.android.identity.securearea.KeyUnlockData)}.
         *
         * <p>Note that a {@link BiometricPrompt.CryptoObject} is returned only if the key is
         * configured to require authentication for every use of the key, that is, when the
         * key was created with a zero timeout as per
         * {@link AndroidKeystoreSecureArea.CreateKeySettings.Builder#setUserAuthenticationRequired(boolean, long, int)}.
         *
         * @return A {@link BiometricPrompt.CryptoObject} or {@code null}.
         */
        public @Nullable BiometricPrompt.CryptoObject getCryptoObjectForKeyAgreement() {
            if (mCryptoObjectForSigning != null) {
                return mCryptoObjectForSigning;
            }
            try {
                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);
                KeyStore.Entry entry = ks.getEntry(mAlias, null);
                if (entry == null) {
                    throw new IllegalArgumentException("No entry for alias");
                }
                PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

                KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
                try {
                    android.security.keystore.KeyInfo keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo.class);
                    if (keyInfo.getUserAuthenticationValidityDurationSeconds() > 0) {
                        // Key is not auth-per-op, no CryptoObject required.
                        return null;
                    }
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException("Given key is not an Android Keystore key", e);
                }

                // TODO: Unfortunately we forgot to add support in CryptoObject for KeyAgreement
                //  when we added ECHD to AOSP so this will not work until the platform gains
                //  support for constructing a CryptoObject from a KeyAgreement object. See
                //  b/282058146 for details.
                throw new IllegalStateException("ECDH for keys with timeout 0 is not currently supported");

            } catch (UnrecoverableEntryException
                     | CertificateException
                     | KeyStoreException
                     | IOException
                     | NoSuchAlgorithmException
                     | NoSuchProviderException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /**
     * Android Keystore specific class for information about a key.
     */
    public static class KeyInfo extends com.android.identity.securearea.KeyInfo {

        private final boolean mUserAuthenticationRequired;
        private final boolean mIsStrongBoxBacked;
        private final long mUserAuthenticationTimeoutMillis;
        private final @UserAuthenticationType int mUserAuthenticationType;
        private final String mAttestKeyAlias;
        private final Timestamp mValidFrom;
        private final Timestamp mValidUntil;

        KeyInfo(@NonNull List<X509Certificate> attestation,
                Set<KeyPurpose> keyPurposes,
                EcCurve ecCurve,
                boolean isHardwareBacked,
                @Nullable String attestKeyAlias,
                boolean userAuthenticationRequired,
                long userAuthenticationTimeoutMillis,
                @UserAuthenticationType int userAuthenticationType,
                boolean isStrongBoxBacked,
                @Nullable Timestamp validFrom,
                @Nullable Timestamp validUntil) {
            super(attestation, keyPurposes, ecCurve, isHardwareBacked);
            mUserAuthenticationRequired = userAuthenticationRequired;
            mUserAuthenticationTimeoutMillis = userAuthenticationTimeoutMillis;
            mUserAuthenticationType = userAuthenticationType;
            mIsStrongBoxBacked = isStrongBoxBacked;
            mAttestKeyAlias = attestKeyAlias;
            mValidFrom = validFrom;
            mValidUntil = validUntil;
        }

        /**
         * Gets whether the key is StrongBox based.
         *
         * @return {@code true} if StrongBox based, {@code false} otherwise.
         */
        public boolean isStrongBoxBacked() {
            return mIsStrongBoxBacked;
        }

        /**
         * Gets whether the user authentication is required to use the key.
         *
         * @return {@code true} if authentication is required, {@code false} otherwise.
         */
        public boolean isUserAuthenticationRequired() {
            return mUserAuthenticationRequired;
        }

        /**
         * Gets the timeout for user authentication.
         *
         * @return the timeout in milliseconds or 0 if user authentication is needed for
         *         every use of the key.
         */
        public long getUserAuthenticationTimeoutMillis() {
            return mUserAuthenticationTimeoutMillis;
        }

        /**
         * Gets user authentication type.
         *
         * @return a combination of the flags {@link #USER_AUTHENTICATION_TYPE_LSKF} and
         *         {@link #USER_AUTHENTICATION_TYPE_BIOMETRIC} or 0 if user authentication is
         *         not required.
         */
        public @UserAuthenticationType int getUserAuthenticationType() {
            return mUserAuthenticationType;
        }

        /**
         * Gets the attest key alias for the key, if any.
         *
         * @return the attest key alias or {@code null} if no attest key is used.
         */
        public @Nullable String getAttestKeyAlias() {
            return mAttestKeyAlias;
        }

        /**
         * Gets the point in time before which the key is not valid.
         *
         * @return the point in time before which the key is not valid or {@code null} if not set.
         */
        public @Nullable Timestamp getValidFrom() {
            return mValidFrom;
        }

        /**
         * Gets the point in time after which the key is not valid.
         *
         * @return the point in time after which the key is not valid or {@code null} if not set.
         */
        public @Nullable Timestamp getValidUntil() {
            return mValidUntil;
        }
    }

    @Override
    public @NonNull KeyInfo getKeyInfo(@NonNull String alias) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(alias, null);
            if (entry == null) {
                throw new IllegalArgumentException("No entry for alias");
            }
            PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
            KeyFactory factory = KeyFactory.getInstance(privateKey.getAlgorithm(), "AndroidKeyStore");
            android.security.keystore.KeyInfo keyInfo = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo.class);

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

            EcCurve ecCurve = EcCurve.Companion.fromInt((int) Util.cborMapExtractNumber(map, "curve"));
            Set<KeyPurpose> keyPurposes = KeyPurpose.Companion.decodeSet((int) Util.cborMapExtractNumber(map, "keyPurposes"));
            boolean userAuthenticationRequired = Util.cborMapExtractBoolean(map, "userAuthenticationRequired");
            long userAuthenticationTimeoutMillis = Util.cborMapExtractNumber(map, "userAuthenticationTimeoutMillis");
            boolean isStrongBoxBacked = Util.cborMapExtractBoolean(map, "useStrongBox");
            String attestKeyAlias = null;
            if (Util.cborMapHasKey(map, "attestKeyAlias")) {
                attestKeyAlias = Util.cborMapExtractString(map, "attestKeyAlias");
            }
            boolean isHardwareBacked = keyInfo.isInsideSecureHardware();
            Timestamp validFrom = null;
            Timestamp validUntil = null;
            if (keyInfo.getKeyValidityStart() != null) {
                validFrom = Timestamp.ofEpochMilli(keyInfo.getKeyValidityStart().getTime());
            }
            if (keyInfo.getKeyValidityForOriginationEnd() != null) {
                validUntil = Timestamp.ofEpochMilli(keyInfo.getKeyValidityForOriginationEnd().getTime());
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

            @UserAuthenticationType int userAuthenticationType = USER_AUTHENTICATION_TYPE_LSKF
                    | USER_AUTHENTICATION_TYPE_BIOMETRIC;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                userAuthenticationType = 0;
                int type = keyInfo.getUserAuthenticationType();
                if ((type & KeyProperties.AUTH_DEVICE_CREDENTIAL) != 0) {
                    userAuthenticationType |= USER_AUTHENTICATION_TYPE_LSKF;
                }
                if ((type & KeyProperties.AUTH_BIOMETRIC_STRONG) != 0) {
                    userAuthenticationType |= USER_AUTHENTICATION_TYPE_BIOMETRIC;
                }
            }

            return new KeyInfo(
                    attestation,
                    keyPurposes,
                    ecCurve,
                    isHardwareBacked,
                    attestKeyAlias,
                    userAuthenticationRequired,
                    userAuthenticationTimeoutMillis,
                    userAuthenticationType,
                    isStrongBoxBacked,
                    validFrom,
                    validUntil);
        } catch (UnrecoverableEntryException
                 | CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException
                 | NoSuchProviderException
                 | InvalidKeySpecException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void saveKeyMetadata(@NonNull String alias,
                                 @NonNull CreateKeySettings settings,
                                 @NonNull List<X509Certificate> attestation) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put("curve", settings.getEcCurve().getCoseCurveIdentifier());
        map.put("keyPurposes", KeyPurpose.Companion.encodeSet(settings.getKeyPurposes()));
        String attestKeyAlias = settings.getAttestKeyAlias();
        if (attestKeyAlias != null) {
            map.put("attestKeyAlias", attestKeyAlias);
        }
        map.put("userAuthenticationRequired", settings.getUserAuthenticationRequired());
        map.put("userAuthenticationTimeoutMillis", settings.getUserAuthenticationTimeoutMillis());
        map.put("useStrongBox", settings.getUseStrongBox());

        ArrayBuilder<MapBuilder<CborBuilder>> attestationBuilder = map.putArray("attestation");
        for (X509Certificate certificate : attestation) {
            try {
                attestationBuilder.add(certificate.getEncoded());
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("Error encoding certificate chain", e);
            }
        }
        attestationBuilder.end();

        mStorageEngine.put(PREFIX + alias, Util.cborEncode(builder.build().get(0)));
    }

    /**
     * Class for holding Android Keystore-specific settings related to key creation.
     */
    public static class CreateKeySettings extends com.android.identity.securearea.CreateKeySettings {
        private final boolean mUserAuthenticationRequired;
        private final long mUserAuthenticationTimeoutMillis;
        private final @UserAuthenticationType int mUserAuthenticationType;
        private final boolean mUseStrongBox;
        private final String mAttestKeyAlias;
        private final Timestamp mValidFrom;
        private final Timestamp mValidUntil;

        private CreateKeySettings(Set<KeyPurpose> keyPurposes,
                                  EcCurve ecCurve,
                                  @NonNull byte[] attestationChallenge,
                                  boolean userAuthenticationRequired,
                                  long userAuthenticationTimeoutMillis,
                                  @UserAuthenticationType int userAuthenticationType,
                                  boolean useStrongBox,
                                  @Nullable String attestKeyAlias,
                                  @Nullable Timestamp validFrom,
                                  @Nullable Timestamp validUntil) {
            super(attestationChallenge, keyPurposes, ecCurve);
            mUserAuthenticationRequired = userAuthenticationRequired;
            mUserAuthenticationTimeoutMillis = userAuthenticationTimeoutMillis;
            mUserAuthenticationType = userAuthenticationType;
            mUseStrongBox = useStrongBox;
            mAttestKeyAlias = attestKeyAlias;
            mValidFrom = validFrom;
            mValidUntil = validUntil;
        }

        /**
         * Gets whether user authentication is required.
         *
         * @return whether user authentication is required.
         */
        public boolean getUserAuthenticationRequired() {
            return mUserAuthenticationRequired;
        }

        /**
         * Gets user authentication timeout, if any.
         *
         * @return timeout in milliseconds, or 0 if authentication is required on every use.
         */
        public long getUserAuthenticationTimeoutMillis() {
            return mUserAuthenticationTimeoutMillis;
        }

        /**
         * Gets user authentication type.
         *
         * @return a combination of the flags {@link #USER_AUTHENTICATION_TYPE_LSKF} and
         * {@link #USER_AUTHENTICATION_TYPE_BIOMETRIC}.
         */
        public @UserAuthenticationType int getUserAuthenticationType() {
            return mUserAuthenticationType;
        }

        /**
         * Gets whether StrongBox is used.
         *
         * @return whether StrongBox is used.
         */
        public boolean getUseStrongBox() {
            return mUseStrongBox;
        }

        /**
         * Gets the attest key alias, if any.
         *
         * @return the attest key alias or {@code null} if an attest key is not used.
         */
        public @Nullable String getAttestKeyAlias() {
            return mAttestKeyAlias;
        }

        /**
         * Gets the point in time before which the key is not valid.
         *
         * @return the point in time before which the key is not valid or {@code null} if not set.
         */
        public @Nullable Timestamp getValidFrom() {
            return mValidFrom;
        }

        /**
         * Gets the point in time after which the key is not valid.
         *
         * @return the point in time after which the key is not valid or {@code null} if not set.
         */
        public @Nullable Timestamp getValidUntil() {
            return mValidUntil;
        }

        /**
         * A builder for {@link CreateKeySettings}.
         */
        public static class Builder {
            private Set<KeyPurpose> mKeyPurposes = Set.of(KeyPurpose.SIGN);
            private EcCurve mEcCurve = EcCurve.P256;
            private final byte[] mAttestationChallenge;
            private boolean mUserAuthenticationRequired;
            private long mUserAuthenticationTimeoutMillis;
            private @UserAuthenticationType int mUserAuthenticationType;
            private boolean mUseStrongBox;
            private String mAttestKeyAlias;
            private Timestamp mValidFrom;
            private Timestamp mValidUntil;

            /**
             * Constructor.
             *
             * @param attestationChallenge challenge to include in attestation for the key.
             */
            public Builder(@NonNull byte[] attestationChallenge) {
                mAttestationChallenge = attestationChallenge;
            }

            /**
             * Sets the key purpose.
             *
             * <p>By default the key purpose is {@link KeyPurpose#SIGN}.
             *
             * @param keyPurposes one or more purposes.
             * @return the builder.
             * @throws IllegalArgumentException if no purpose is set.
             */
            public @NonNull CreateKeySettings.Builder setKeyPurposes(@NonNull Set<KeyPurpose> keyPurposes) {
                if (keyPurposes.isEmpty()) {
                    throw new IllegalArgumentException("Purposes cannot be empty");
                }
                mKeyPurposes = keyPurposes;
                return this;
            }

            /**
             * Sets the curve to use for EC keys.
             *
             * <p>By default {@link EcCurve#P256} is used.
             *
             * @param curve the curve to use.
             * @return the builder.
             */
            public @NonNull CreateKeySettings.Builder setEcCurve(EcCurve curve) {
                mEcCurve = curve;
                return this;
            }

            /**
             * Method to specify if user authentication is required to use the key.
             *
             * <p>On devices with prior to API 30, {@code userAuthenticationType} must be
             * {@link #USER_AUTHENTICATION_TYPE_LSKF} combined with
             * {@link #USER_AUTHENTICATION_TYPE_BIOMETRIC}. On API 30 and later
             * either flag may be used independently. The value cannot be zero if user
             * authentication is required.
             *
             * <p>By default, no user authentication is required.
             *
             * @param required True if user authentication is required, false otherwise.
             * @param timeoutMillis If 0, user authentication is required for every use of
             *                      the key, otherwise it's required within the given amount
             *                      of milliseconds.
             * @param userAuthenticationType a combination of the flags
             *   {@link #USER_AUTHENTICATION_TYPE_LSKF} and
             *   {@link #USER_AUTHENTICATION_TYPE_BIOMETRIC}.
             * @return the builder.
             */
            public @NonNull Builder setUserAuthenticationRequired(
                    boolean required,
                    long timeoutMillis,
                    @UserAuthenticationType int userAuthenticationType) {
                if (required) {
                    if (userAuthenticationType == 0) {
                        throw new IllegalArgumentException(
                                "userAuthenticationType must be set when user authentication is required");
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        if (userAuthenticationType != (USER_AUTHENTICATION_TYPE_LSKF | USER_AUTHENTICATION_TYPE_BIOMETRIC)) {
                            throw new IllegalArgumentException(
                                    "Only LSKF and Strong Biometric supported on this API level");
                        }
                    }
                }
                mUserAuthenticationRequired = required;
                mUserAuthenticationTimeoutMillis = timeoutMillis;
                mUserAuthenticationType = userAuthenticationType;
                return this;
            }


            /**
             * Method to specify if StrongBox Android Keystore should be used, if available.
             *
             * <p>By default StrongBox isn't used.
             *
             * @param useStrongBox Whether to use StrongBox.
             * @return the builder.
             */
            public @NonNull Builder setUseStrongBox(boolean useStrongBox) {
                mUseStrongBox = useStrongBox;
                return this;
            }

            /**
             * Method to specify if an attest key should be used.
             *
             * <p>By default no attest key is used. See
             * <a href="https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setAttestKeyAlias(java.lang.String)">setAttestKeyAlias() method</a>
             * for more information about attest keys.
             *
             * @param attestKeyAlias the Android Keystore alias of the attest key or {@code null} to not use an attest key.
             * @return the builder.
             */
            public @NonNull Builder setAttestKeyAlias(@Nullable String attestKeyAlias) {
                mAttestKeyAlias = attestKeyAlias;
                return this;
            }

            /**
             * Sets the key validity period.
             *
             * <p>By default the key validity period is unbounded.
             *
             * @param validFrom the point in time before which the key is not valid.
             * @param validUntil the point in time after which the key is not valid.
             * @return the builder.
             */
            public @NonNull Builder setValidityPeriod(@NonNull Timestamp validFrom,
                                                      @NonNull Timestamp validUntil) {
                mValidFrom = validFrom;
                mValidUntil = validUntil;
                return this;
            }

            /**
             * Builds the {@link CreateKeySettings}.
             *
             * @return a new {@link CreateKeySettings}.
             */
            public @NonNull CreateKeySettings build() {
                return new CreateKeySettings(
                        mKeyPurposes,
                        mEcCurve,
                        mAttestationChallenge,
                        mUserAuthenticationRequired,
                        mUserAuthenticationTimeoutMillis,
                        mUserAuthenticationType,
                        mUseStrongBox,
                        mAttestKeyAlias,
                        mValidFrom,
                        mValidUntil);
            }
        }

    }

    private static int getFeatureVersionKeystore(@NonNull Context appContext, boolean useStrongbox) {
        String feature = PackageManager.FEATURE_HARDWARE_KEYSTORE;
        if (useStrongbox) {
            feature = PackageManager.FEATURE_STRONGBOX_KEYSTORE;
        }
        PackageManager pm = appContext.getPackageManager();
        if (pm.hasSystemFeature(feature)) {
            FeatureInfo info = null;
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo i = infos[n];
                if (i.name.equals(feature)) {
                    info = i;
                    break;
                }
            }
            int version = 0;
            if (info != null) {
                version = info.version;
            }
            // It's entirely possible that the feature exists but the version number hasn't
            // been set. In that case, assume it's at least KeyMaster 4.1.
            if (version < 41) {
                version = 41;
            }
            return version;
        }
        // It's only a requirement to set PackageManager.FEATURE_HARDWARE_KEYSTORE since
        // Android 12 so for old devices this isn't set. However all devices since Android
        // 8.1 has had HW-backed keystore so in this case we can report KeyMaster 4.1
        if (!useStrongbox) {
            return 41;
        }
        return 0;
    }

    /**
     * Helper class to determine capabilities of the device.
     *
     * <p>This class can be used by applications to determine the extent of
     * Android Keystore support on the device the application is running on.
     */
    public static class Capabilities {
        private final KeyguardManager mKeyguardManager;
        private final int mApiLevel;
        private final int mTeeFeatureLevel;
        private final int mSbFeatureLevel;

        /**
         * Construct a new Capabilities object.
         *
         * <p>Once constructed, the application may query this object to determine
         * which Android Keystore features are available.
         *
         * <p>In general this is implemented by examining
         * <a href="https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_HARDWARE_KEYSTORE">
         * FEATURE_HARDWARE_KEYSTORE</a> and
         * <a href="https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_STRONGBOX_KEYSTORE">
         * FEATURE_STRONGBOX_KEYSTORE</a> to determine the KeyMint version for both
         * the normal hardware-backed keystore and - if available - the StrongBox-backed keystore.
         *
         * @param context the application context.
         */
        public Capabilities(@NonNull Context context) {
            mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            mTeeFeatureLevel = getFeatureVersionKeystore(context, false);
            mSbFeatureLevel = getFeatureVersionKeystore(context, true);
            mApiLevel = Build.VERSION.SDK_INT;
        }

        /**
         * Gets whether a Secure Lock Screen has been set up.
         *
         * <p>This checks whether the device currently has a secure lock
         * screen (either PIN, pattern, or password).
         *
         * @return {@code true} if Secure Lock Screen has been set up, {@link false} otherwise.
         */
        public boolean getSecureLockScreenSetup() {
            return mKeyguardManager.isDeviceSecure();
        }

        /**
         * Gets whether it's possible to specify multiple authentication types.
         *
         * <p>On Android versions before API 30 (Android 11), it's not possible to specify whether
         * LSKF or Biometric or both can be used to unlock a key (both are always possible).
         * Starting with Android 11, it's possible to specify all three combinations (LSKF only,
         * Biometric only, or both).
         *
         * @return {@code true} if possible to use multiple authentication types, {@link false} otherwise.
         */
        public boolean getMultipleAuthenticationTypesSupported() {
            return mApiLevel >= Build.VERSION_CODES.R;
        }

        /**
         * Gets whether Attest Keys are supported.
         *
         * <p>This is only supported in KeyMint 1.0 (version 100) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getAttestKeySupported() {
            return mTeeFeatureLevel >= 100;
        }

        /**
         * Gets whether Key Agreement is supported.
         *
         * <p>This is only supported in KeyMint 1.0 (version 100) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getKeyAgreementSupported() {
            return mTeeFeatureLevel >= 100;
        }

        /**
         * Gets whether Curve25519 is supported.
         *
         * <p>This is only supported in KeyMint 2.0 (version 200) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getCurve25519Supported() {
            return mTeeFeatureLevel >= 200;
        }

        /**
         * Gets whether StrongBox is supported.
         *
         * <p>StrongBox requires dedicated hardware and is not available on all devices.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getStrongBoxSupported() {
            return mSbFeatureLevel > 0;
        }

        /**
         * Gets whether StrongBox Attest Keys are supported.
         *
         * <p>This is only supported in StrongBox KeyMint 1.0 (version 100) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getStrongBoxAttestKeySupported() {
            return mSbFeatureLevel >= 100;
        }

        /**
         * Gets whether StrongBox Key Agreement is supported.
         *
         * <p>This is only supported in StrongBox KeyMint 1.0 (version 100) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getStrongBoxKeyAgreementSupported() {
            return mSbFeatureLevel >= 100;
        }

        /**
         * Gets whether StrongBox Curve25519 is supported.
         *
         * <p>This is only supported in StrongBox KeyMint 2.0 (version 200) and higher.
         *
         * @return {@code true} if supported, {@link false} otherwise.
         */
        public boolean getStrongBoxCurve25519Supported() {
            return mSbFeatureLevel >= 200;
        }
    }
}
