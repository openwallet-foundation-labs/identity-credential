/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.identity;


import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

class KeystorePresentationSession extends PresentationSession {

    private static final String TAG = "KSPresentationSession"; // limit to <= 23 chars
    private @IdentityCredentialStore.Ciphersuite
    final int mCipherSuite;
    private final Context mContext;
    private final Map<String, KeystoreIdentityCredential> mCredentialCache = new LinkedHashMap<>();
    private final File mStorageDirectory;

    private KeyPair mEphemeralDeviceKeyPair;
    private byte[] mSessionTranscript;
    private PublicKey mReaderEphemeralPublicKey;

    KeystorePresentationSession(@NonNull Context context,
                                @NonNull File storageDirectory,
                                @IdentityCredentialStore.Ciphersuite int cipherSuite) {
        mContext = context;
        mStorageDirectory = storageDirectory;
        mCipherSuite = cipherSuite;
    }

    @Override
    public @NonNull KeyPair getEphemeralKeyPair() {
        if (mEphemeralDeviceKeyPair == null) {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
                ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime256v1");
                kpg.initialize(ecSpec);
                mEphemeralDeviceKeyPair = kpg.generateKeyPair();
            } catch (NoSuchAlgorithmException
                    | InvalidAlgorithmParameterException e) {
                throw new IllegalStateException("Error generating ephemeral key", e);
            }
        }
        return mEphemeralDeviceKeyPair;
    }

    @Override
    public void setReaderEphemeralPublicKey(@NonNull PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException {
        if (mReaderEphemeralPublicKey != null) {
            throw new IllegalStateException("Reader ephemeral key already set");
        }
        mReaderEphemeralPublicKey = readerEphemeralPublicKey;
    }

    @Override
    public void setSessionTranscript(@NonNull byte[] sessionTranscript) {
        if (mSessionTranscript != null) {
            throw new IllegalStateException("SessionTranscript already set");
        }
        mSessionTranscript = sessionTranscript.clone();
    }

    @SuppressWarnings("deprecation")
    @Override
    public @Nullable CredentialDataResult getCredentialData(@NonNull String credentialName,
                                                            @NonNull CredentialDataRequest request)
            throws NoAuthenticationKeyAvailableException, InvalidReaderSignatureException,
            InvalidRequestMessageException, EphemeralPublicKeyNotFoundException {
        try {
            // Cache the IdentityCredential to satisfy the property that AuthKey usage counts are
            // incremented on only the _first_ getCredentialData() call.
            //
            KeystoreIdentityCredential credential = mCredentialCache.get(credentialName);
            if (credential == null) {
                credential = new KeystoreIdentityCredential(
                        mContext, mStorageDirectory, credentialName, mCipherSuite,
                        this);
                if (!credential.loadData()) {
                    return null;
                }
                mCredentialCache.put(credentialName, credential);

                credential.setAllowUsingExhaustedKeys(request.isAllowUsingExhaustedKeys());
                credential.setAllowUsingExpiredKeys(request.isAllowUsingExpiredKeys());
                credential.setIncrementKeyUsageCount(request.isIncrementUseCount());
                if (mSessionTranscript != null) {
                    credential.setSessionTranscript(mSessionTranscript);
                }
            }

            ResultData deviceSignedResult = credential.getEntries(
                    request.getRequestMessage(),
                    request.getDeviceSignedEntriesToRequest(),
                    request.getReaderSignature());

            // By design this second getEntries() call consumes the same auth-key.

            ResultData issuerSignedResult = credential.getEntries(
                    request.getRequestMessage(),
                    request.getIssuerSignedEntriesToRequest(),
                    request.getReaderSignature());

            return new SimpleCredentialDataResult(deviceSignedResult, issuerSignedResult);

        } catch (CipherSuiteNotSupportedException e) {
            throw new IllegalStateException("Unexpected CipherSuiteNotSupportedException", e);
        }
    }

    @SuppressWarnings("deprecation")
    private SecretKey getAuthPerPresentationKey(String alias) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(alias, null);
            if (entry != null) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(128)
                    // Can't use setUserAuthenticationParameters() since we need to run
                    // on API level 24.
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(-1); // Auth for every use
            kg.init(builder.build());
            kg.generateKey();
            entry = ks.getEntry(alias, null);
            if (entry != null) {
                Log.d(TAG, "Created key with alias " + alias);
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            throw new IllegalStateException("Error getting secretKey after creating it");
        } catch (InvalidAlgorithmParameterException e) {
            // This exception is thrown only if no biometrics are enrolled or if the device
            // doesn't support biometrics.
            return null;
        } catch (CertificateException
                | IOException
                | NoSuchAlgorithmException
                | KeyStoreException
                | UnrecoverableEntryException
                | NoSuchProviderException e) {
            throw new IllegalStateException("Error ensuring authPerPresentationKey", e);
        }
    }

    // This is the alias for a key used for implementing auth on every presentation. It is shared
    // by all presentations. Keep naming in sync with get*Alias*() methods in CredentialData.java.
    //
    private static final String KEY_FOR_AUTH_PER_PRESENTATION_ALIAS =
            "identity_credential_auth_per_presentation_key";

    private boolean mPerReaderSessionAuthSatisfied = false;
    private boolean mPerReaderSessionAuthSatisfiedCalculated = false;
    private BiometricPrompt.CryptoObject mLastCryptoObjectCreated;

    // This returns a new CryptoObject every time.
    //
    @Override
    public @Nullable BiometricPrompt.CryptoObject getCryptoObject() {
        try {
            SecretKey authPerPresentationKey =
                    getAuthPerPresentationKey(KEY_FOR_AUTH_PER_PRESENTATION_ALIAS);
            if (authPerPresentationKey == null) {
                mLastCryptoObjectCreated = null;
            } else {
                Cipher authPerPresentationCipher = Cipher.getInstance("AES/GCM/NoPadding");
                authPerPresentationCipher.init(Cipher.ENCRYPT_MODE, authPerPresentationKey);
                mLastCryptoObjectCreated = new BiometricPrompt.CryptoObject(authPerPresentationCipher);
            }
            mPerReaderSessionAuthSatisfiedCalculated = false;
            return mLastCryptoObjectCreated;
        } catch (NoSuchPaddingException
                | InvalidKeyException
                | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error creating Cipher for perReaderSessionKey", e);
        }
    }

    // Called by KeystoreIdentityCredential to determine if the user successfully authenticated
    // for the CryptoObject returned to the application by getCryptoObject() above.
    //
    boolean isPerReaderSessionAuthSatisfied() {
        if (!mPerReaderSessionAuthSatisfiedCalculated) {
            mPerReaderSessionAuthSatisfied = calculatePerReaderSessionAuthSatisfied();
            mPerReaderSessionAuthSatisfiedCalculated = true;
        }
        return mPerReaderSessionAuthSatisfied;
    }

    private boolean calculatePerReaderSessionAuthSatisfied() {
        if (mLastCryptoObjectCreated == null) {
            // In this case the app never requested a CryptoObject so we are sure authentication
            // never happened meaning per-reader session authentication isn't satisfied.
            return false;
        }
        try {
            Cipher cipher = mLastCryptoObjectCreated.getCipher();
            byte[] clearText = new byte[16];
            // We don't care about the cipherText, only whether the key is unlocked.
            cipher.doFinal(clearText);
            return true;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // If we get here, it's because the user didn't auth.
            return false;
        }
    }

}
