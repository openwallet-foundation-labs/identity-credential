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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * An interface to a Secure Area.
 *
 * <p>This interface exists to abstract the underlying secure area used
 * used for creation of key material and other security objects related
 * to credentials.
 */
public interface SecureArea {

    /** The curve identifier for P-256 */
    int EC_CURVE_P256 = 1;
    /** The curve identifier for P-384 */
    int EC_CURVE_P384 = 2;
    /** The curve identifier for P-521 */
    int EC_CURVE_P521 = 3;
    /** The curve identifier for brainpoolP256r1 */
    int EC_CURVE_BRAINPOOLP256R1 = -65537;
    /** The curve identifier for brainpoolP320r1 */
    int EC_CURVE_BRAINPOOLP320R1 = -65538;
    /** The curve identifier for brainpoolP384r1 */
    int EC_CURVE_BRAINPOOLP384R1 = -65539;
    /** The curve identifier for brainpoolP512r1 */
    int EC_CURVE_BRAINPOOLP512R1 = -65540;
    /** The curve identifier for Ed25519 (EdDSA only) */
    int EC_CURVE_ED25519 = 6;
    /** The curve identifier for X25519 (ECDH only) */
    int EC_CURVE_X25519 = 4;
    /** The curve identifier for Ed448 (EdDSA only) */
    int EC_CURVE_ED448 = 7;
    /** The curve identifier for X448 (ECDH only) */
    int EC_CURVE_X448 = 5;

    /**
     * An annotation used to specify allowed curve identifiers.
     *
     * <p>All curve identifiers are from the
     * <a href="https://www.iana.org/assignments/cose/cose.xhtml">
     * IANA COSE registry</a>.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            EC_CURVE_P256,
            EC_CURVE_P384,
            EC_CURVE_P521,
            EC_CURVE_BRAINPOOLP256R1,
            EC_CURVE_BRAINPOOLP320R1,
            EC_CURVE_BRAINPOOLP384R1,
            EC_CURVE_BRAINPOOLP512R1,
            EC_CURVE_ED25519,
            EC_CURVE_ED448,
            EC_CURVE_X25519,
            EC_CURVE_X448,
    })
    @interface EcCurve {}

    /** Used to indicate the algorithm is unset. */
    int ALGORITHM_UNSET = Integer.MAX_VALUE;

    /** The algorithm identifier for signatures using ECDSA with SHA-256 */
    int ALGORITHM_ES256 = -7;
    /** The algorithm identifier for signatures using ECDSA with SHA-384 */
    int ALGORITHM_ES384 = -35;
    /** The algorithm identifier for signatures using ECDSA with SHA-512 */
    int ALGORITHM_ES512 = -36;
    /** The algorithm identifier for signatures using EdDSA */
    int ALGORITHM_EDDSA = -8;

    /**
     * An annotation used to specify algorithms.
     *
     * <p>All algorithm identifiers are from the
     * <a href="https://www.iana.org/assignments/cose/cose.xhtml">
     * IANA COSE registry</a>.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            ALGORITHM_UNSET,
            ALGORITHM_ES256,
            ALGORITHM_ES384,
            ALGORITHM_ES512,
            ALGORITHM_EDDSA
    })
    @interface Algorithm {}

    /**
     * Purpose of key: signing.
     */
    int KEY_PURPOSE_SIGN = 1<<0;

    /**
     * Purpose of key: key agreement.
     */
    int KEY_PURPOSE_AGREE_KEY = 1<<1;

    /**
     * An annotation used for indicating the purpose of a key.
     *
     * <p>A key may have multiple purposes.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {
                    KEY_PURPOSE_SIGN,
                    KEY_PURPOSE_AGREE_KEY
            })
    @interface KeyPurpose {}

    /**
     * Gets a stable identifier for the Secure Area.
     *
     * <p>This is typically just the class name but for secure areas allowing
     * multiple instances, this could differ.
     *
     * @return a stable identifier for the Secure Area.
     */
    @NonNull String getIdentifier();

    /**
     * Gets a string suitable to display to the end user, for identifying the Secure Area instance.
     *
     * @return the display name for the Secure Area.
     */
    @NonNull String getDisplayName();

    /**
     * Creates an new key.
     *
     * <p>This creates an EC key-pair where the private part of the key never
     * is exposed to the user of this interface.
     *
     * <p>The key is attested to and the generated certificate-chain depends on
     * the specific Secure Area used and the only guarantee is that
     * the leaf certificate contains the public key of the created key. Usually
     * a list of certificates chaining up to a well-known root is returned along
     * with platform specific information in the leaf certificate. The attestation
     * for the created key can be obtained via {@link KeyInfo#getAttestation()}.
     *
     * <p>If an existing key with the given alias already exists it will be
     * replaced by the new key.
     *
     * @param alias             A unique string to identify the newly created key.
     * @param createKeySettings A {@link CreateKeySettings} object.
     * @throws IllegalArgumentException if the underlying Secure Area Implementation
     *                                  does not support the requested creation
     *                                  settings, for example the EC curve to use.
     */
    void createKey(@NonNull String alias, @NonNull CreateKeySettings createKeySettings);

    /**
     * Deletes a previously created key.
     *
     * <p>If the key to delete doesn't exist, this is a no-op.
     *
     * @param alias The alias of the EC key to delete.
     */
    void deleteKey(@NonNull String alias);

    /**
     * Signs data with a key.
     *
     * <p>If the key needs unlocking before use (for example user authentication
     * in any shape or form) and {@code keyUnlockData} isn't set or doesn't contain
     * what's needed, {@link KeyLockedException} is thrown.
     *
     * @param alias The alias of the EC key to sign with.
     * @param signatureAlgorithm the signature algorithm to use.
     * @param dataToSign the data to sign.
     * @param keyUnlockData data used to unlock the key or {@code null}.
     * @return a DER encoded string with the signature.
     * @throws IllegalArgumentException if there is no key with the given alias
     *                                  or the key wasn't created with purpose
     *                                  {@link #KEY_PURPOSE_SIGN}.
     * @throws IllegalArgumentException if the signature algorithm isn’t compatible
     *                                  with the key.
     * @throws KeyLockedException if the key needs unlocking.
     */
    @NonNull byte[] sign(@NonNull String alias,
                         @Algorithm int signatureAlgorithm,
                         @NonNull byte[] dataToSign,
                         @Nullable KeyUnlockData keyUnlockData)
            throws KeyLockedException;

    /**
     * Performs Key Agreement.
     *
     * <p>If the key needs unlocking before use (for example user authentication
     * in any shape or form) and {@code keyUnlockData} isn't set or doesn't contain
     * what's needed, {@link KeyLockedException} is thrown.
     *
     * @param alias the alias of the EC key to use.
     * @param otherKey The public EC key from the other party
     * @param keyUnlockData data used to unlock the key or {@code null}.
     * @return The shared secret.
     * @throws IllegalArgumentException if the other key isn't the same curve.
     * @throws IllegalArgumentException if there is no key with the given alias
     *                                  or the key wasn't created with purpose
     *                                  {@link #KEY_PURPOSE_AGREE_KEY}.
     * @throws KeyLockedException if the key needs unlocking.
     */
    @NonNull byte[] keyAgreement(@NonNull String alias,
                                 @NonNull PublicKey otherKey,
                                 @Nullable SecureArea.KeyUnlockData keyUnlockData)
            throws KeyLockedException;

    /**
     * Gets information about a key.
     *
     * @param alias the alias of the EC key to use.
     * @return a {@link KeyInfo} object.
     * @throws IllegalArgumentException if there is no key with the given alias.
     */
    @NonNull
    KeyInfo getKeyInfo(@NonNull String alias);

    /**
     * Class with information about a key.
     *
     * <p>Concrete {@link SecureArea} implementations may subclass this to provide additional
     * implementation-specific information about the key.
     */
    class KeyInfo {
        private List<X509Certificate> mAttestation;
        private final @KeyPurpose int mKeyPurposes;
        private final @EcCurve int mEcCurve;
        private final boolean mIsHardwareBacked;

        protected KeyInfo(@NonNull List<X509Certificate> attestation,
                          @KeyPurpose int keyPurposes,
                          @EcCurve int ecCurve,
                          boolean isHardwareBacked) {
            mAttestation = attestation;
            mKeyPurposes = keyPurposes;
            mEcCurve = ecCurve;
            mIsHardwareBacked = isHardwareBacked;
        }

        /**
         * Gets the attestation for the key.
         *
         * @return A list of certificates representing a certificate chain.
         */
        public @NonNull List<X509Certificate> getAttestation() {
            return Collections.unmodifiableList(mAttestation);
        }

        /**
         * Gets the purposes of a key.
         *
         * @return One or more {@link KeyPurpose} flags.
         */
        public @KeyPurpose int getKeyPurposes() {
            return mKeyPurposes;
        }

        /**
         * Gets the curve used for a key.
         *
         * @return A constant from {@link EcCurve}.
         */
        public @EcCurve int getEcCurve() {
            return mEcCurve;
        }

        /**
         * Gets whether the key is hardware backed.
         *
         * @return {@code true} if the key is hardware-backed, {@code false} otherwise.
         */
        public boolean isHardwareBacked() {
            return mIsHardwareBacked;
        }
    }

    /**
     * Exception thrown when trying to use a key which hasn't been unlocked.
     */
    class KeyLockedException extends Exception {
        /**
         * Construct a new exception.
         */
        public KeyLockedException() {}

        /**
         * Construct a new exception.
         *
         * @param message the message.
         */
        public KeyLockedException(@NonNull String message) {
            super(message);
        }

        /**
         * Construct a new exception.
         *
         * @param message the message.
         * @param cause the cause.
         */
        public KeyLockedException(@NonNull String message,
                                  @NonNull Exception cause) {
            super(message, cause);
        }

        /**
         * Construct a new exception.
         *
         * @param cause the cause.
         */
        public KeyLockedException(@NonNull Exception cause) {
            super(cause);
        }
    }

    /**
     * Abstract type with information used when operating on a key that
     * has been unlocked.
     */
    interface KeyUnlockData {
    }

    /**
     * Base class for key creation settings.
     *
     * <p>This can be used for any conforming {@link SecureArea} implementations.
     * although such implementations will typically supply their own implementations
     * with additional settings to e.g. configure user authentication, passphrase
     * protections, and other things.
     */
    class CreateKeySettings {
        protected final byte[] mAttestationChallenge;

        /**
         * Creates a new settings object.
         *
         * @param attestationChallenge challenge to include in attestation for the created key.
         */
        public CreateKeySettings(@NonNull byte[] attestationChallenge) {
            mAttestationChallenge = attestationChallenge;
        }

        /**
         * Gets the attestation challenge.
         *
         * @return the attestation challenge.
         */
        public @NonNull byte[] getAttestationChallenge() {
            return mAttestationChallenge;
        }
    }
}
