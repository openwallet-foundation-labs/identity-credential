/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.identity.android.legacy;

import android.annotation.SuppressLint;
import android.icu.util.Calendar;
import android.os.Build;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.biometric.BiometricPrompt;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@RequiresApi(Build.VERSION_CODES.R)
class HardwareIdentityCredential extends IdentityCredential {

    private static final String TAG = "HardwareIdentityCredential";

    private KeyPair mEphemeralKeyPair = null;
    private PublicKey mReaderEphemeralPublicKey = null;
    private byte[] mSessionTranscript = null;

    private SecretKey mSKDevice = null;
    private SecretKey mSKReader = null;

    private int mSKDeviceCounter;
    private int mSKReaderCounter;

    private android.security.identity.IdentityCredential mCredential  = null;

    HardwareIdentityCredential(android.security.identity.IdentityCredential credential) {
        mCredential = credential;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NonNull KeyPair createEphemeralKeyPair() {
        if (mEphemeralKeyPair == null) {
            mEphemeralKeyPair = mCredential.createEphemeralKeyPair();
        }
        return mEphemeralKeyPair;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setReaderEphemeralPublicKey(@NonNull PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException {
        mReaderEphemeralPublicKey = readerEphemeralPublicKey;
        mCredential.setReaderEphemeralPublicKey(readerEphemeralPublicKey);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setSessionTranscript(@NonNull byte[] sessionTranscript) {
        if (mSessionTranscript != null) {
            throw new RuntimeException("SessionTranscript already set");
        }
        mSessionTranscript = sessionTranscript.clone();
    }

    private void ensureSessionEncryptionKey() {
        if (mSKDevice != null) {
            return;
        }
        if (mReaderEphemeralPublicKey == null) {
            throw new RuntimeException("Reader ephemeral key not set");
        }
        if (mSessionTranscript == null) {
            throw new RuntimeException("Session transcript not set");
        }
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(mEphemeralKeyPair.getPrivate());
            ka.doPhase(mReaderEphemeralPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] sessionTranscriptBytes =
                    Util.cborEncode(Util.cborBuildTaggedByteString(mSessionTranscript));
            byte[] salt = MessageDigest.getInstance("SHA-256").digest(sessionTranscriptBytes);

            byte[] info = new byte[] {'S', 'K', 'D', 'e', 'v', 'i', 'c', 'e'};
            byte[] derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);
            mSKDevice = new SecretKeySpec(derivedKey, "AES");

            info = new byte[] {'S', 'K', 'R', 'e', 'a', 'd', 'e', 'r'};
            derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);
            mSKReader = new SecretKeySpec(derivedKey, "AES");

            mSKDeviceCounter = 1;
            mSKReaderCounter = 1;

        } catch (InvalidKeyException
                | NoSuchAlgorithmException e) {
            throw new RuntimeException("Error performing key agreement", e);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NonNull
    byte[] encryptMessageToReader(@NonNull byte[] messagePlaintext) {
        ensureSessionEncryptionKey();
        byte[] messageCiphertextAndAuthTag = null;
        try {
            ByteBuffer iv = ByteBuffer.allocate(12);
            iv.putInt(0, 0x00000000);
            iv.putInt(4, 0x00000001);
            iv.putInt(8, mSKDeviceCounter);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec encryptionParameterSpec = new GCMParameterSpec(128, iv.array());
            cipher.init(Cipher.ENCRYPT_MODE, mSKDevice, encryptionParameterSpec);
            messageCiphertextAndAuthTag = cipher.doFinal(messagePlaintext);
        } catch (BadPaddingException
                | IllegalBlockSizeException
                | NoSuchPaddingException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Error encrypting message", e);
        }
        mSKDeviceCounter += 1;
        return messageCiphertextAndAuthTag;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NonNull
    byte[] decryptMessageFromReader(@NonNull byte[] messageCiphertext)
            throws MessageDecryptionException {
        ensureSessionEncryptionKey();
        ByteBuffer iv = ByteBuffer.allocate(12);
        iv.putInt(0, 0x00000000);
        iv.putInt(4, 0x00000000);
        iv.putInt(8, mSKReaderCounter);
        byte[] plainText = null;
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, mSKReader, new GCMParameterSpec(128,
                    iv.array()));
            plainText = cipher.doFinal(messageCiphertext);
        } catch (BadPaddingException
                | IllegalBlockSizeException
                | InvalidAlgorithmParameterException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchPaddingException e) {
            throw new MessageDecryptionException("Error decrypting message", e);
        }
        mSKReaderCounter += 1;
        return plainText;
    }

    @Override
    public @NonNull
    Collection<X509Certificate> getCredentialKeyCertificateChain() {
        return mCredential.getCredentialKeyCertificateChain();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys) {
        mCredential.setAllowUsingExhaustedKeys(allowUsingExhaustedKeys);
    }

    @Override
    @Nullable
    public BiometricPrompt.CryptoObject getCryptoObject() {
        BiometricPrompt.CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(mCredential);
        return cryptoObject;
    }

    @Override
    @NonNull
    @SuppressWarnings("deprecation")
    public ResultData getEntries(
            @Nullable byte[] requestMessage,
            @NonNull java.util.Map<String, Collection<String>> entriesToRequest,
            @Nullable byte[] readerSignature)
            throws NoAuthenticationKeyAvailableException,
            InvalidReaderSignatureException, InvalidRequestMessageException,
            EphemeralPublicKeyNotFoundException {

        android.security.identity.ResultData rd;
        try {
            rd = mCredential.getEntries(requestMessage,
                    entriesToRequest,
                    mSessionTranscript,
                    readerSignature);
        } catch (android.security.identity.NoAuthenticationKeyAvailableException e) {
            throw new NoAuthenticationKeyAvailableException(e.getMessage(), e);
        } catch (android.security.identity.InvalidReaderSignatureException e) {
            throw new InvalidReaderSignatureException(e.getMessage(), e);
        } catch (android.security.identity.InvalidRequestMessageException e) {
            throw new InvalidRequestMessageException(e.getMessage(), e);
        } catch (android.security.identity.EphemeralPublicKeyNotFoundException e) {
            throw new EphemeralPublicKeyNotFoundException(e.getMessage(), e);
        } catch (android.security.identity.SessionTranscriptMismatchException e) {
            throw new RuntimeException("Unexpected SessionMismatchException", e);
        }

        SimpleResultData.Builder builder = new SimpleResultData.Builder();
        builder.setMessageAuthenticationCode(rd.getMessageAuthenticationCode());
        builder.setAuthenticatedData(rd.getAuthenticatedData());
        builder.setStaticAuthenticationData(rd.getStaticAuthenticationData());

        for (String namespaceName : rd.getNamespaces()) {
            for (String entryName : rd.getEntryNames(namespaceName)) {
                @ResultData.Status int status = convertFromAndroidStatus(
                    rd.getStatus(namespaceName, entryName));
                if (status == ResultData.STATUS_OK) {
                    byte[] value = rd.getEntry(namespaceName, entryName);
                    builder.addEntry(namespaceName, entryName, value);
                } else {
                    builder.addErrorStatus(namespaceName, entryName, status);
                }
            }
        }

        return builder.build();
    }

    /**
     * Returns the {@link ResultData} status code corresponding to
     * the given one from {@link android.security.identity.ResultData}.
     */
    @SuppressLint("WrongConstant")
    @VisibleForTesting
    static @ResultData.Status
    int convertFromAndroidStatus(int status) {
        // Because our status codes are defined consistently with the ones in the Android platform,
        // no conversion is needed.
        return status;
    }

    @Override
    public void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey) {
        mCredential.setAvailableAuthenticationKeys(keyCount, maxUsesPerKey);
    }

    @Override
    public @NonNull
    Collection<X509Certificate> getAuthKeysNeedingCertification() {
        return mCredential.getAuthKeysNeedingCertification();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void storeStaticAuthenticationData(@NonNull X509Certificate authenticationKey,
            @NonNull byte[] staticAuthData) throws UnknownAuthenticationKeyException {
        try {
            mCredential.storeStaticAuthenticationData(authenticationKey, staticAuthData);
        } catch (android.security.identity.UnknownAuthenticationKeyException e) {
            throw new UnknownAuthenticationKeyException(e.getMessage(), e);
        }
    }

    @Override
    public @NonNull
    int[] getAuthenticationDataUsageCount() {
        return mCredential.getAuthenticationDataUsageCount();
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static class ApiImplS {
        @SuppressWarnings("deprecation")
        @DoNotInline
        static void callSetAllowUsingExpiredKeys(
                @NonNull android.security.identity.IdentityCredential credential,
                boolean allowUsingExpiredKeys) {
            credential.setAllowUsingExpiredKeys(allowUsingExpiredKeys);
        }

        @DoNotInline
        static void callStoreStaticAuthenticationData(
                @NonNull android.security.identity.IdentityCredential credential,
                @NonNull X509Certificate authenticationKey,
                @NonNull Instant expirationDate,
                @NonNull byte[] staticAuthData)
                throws android.security.identity.UnknownAuthenticationKeyException {
            credential.storeStaticAuthenticationData(authenticationKey,
                    expirationDate,
                    staticAuthData);
        }

        @DoNotInline
        static @NonNull byte[] callProveOwnership(
                @NonNull android.security.identity.IdentityCredential credential,
                @NonNull byte[] challenge) {
            return credential.proveOwnership(challenge);
        }

        @DoNotInline
        static @NonNull byte[] callDelete(
                @NonNull android.security.identity.IdentityCredential credential,
                @NonNull byte[] challenge) {
            return credential.delete(challenge);
        }

        @DoNotInline
        static @NonNull byte[] callUpdate(
                @NonNull android.security.identity.IdentityCredential credential,
                @NonNull android.security.identity.PersonalizationData personalizationData) {
            return credential.update(personalizationData);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setAllowUsingExpiredKeys(boolean allowUsingExpiredKeys) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ApiImplS.callSetAllowUsingExpiredKeys(mCredential, allowUsingExpiredKeys);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void storeStaticAuthenticationData(
            @NonNull X509Certificate authenticationKey,
            @NonNull Calendar expirationDate,
            @NonNull byte[] staticAuthData)
            throws UnknownAuthenticationKeyException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Instant expirationDateAsInstant =
                        Instant.ofEpochMilli(expirationDate.getTimeInMillis());
                ApiImplS.callStoreStaticAuthenticationData(mCredential,
                        authenticationKey,
                        expirationDateAsInstant,
                        staticAuthData);
            } catch (android.security.identity.UnknownAuthenticationKeyException e) {
                throw new UnknownAuthenticationKeyException(e.getMessage(), e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public @NonNull byte[] proveOwnership(@NonNull byte[] challenge)  {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ApiImplS.callProveOwnership(mCredential, challenge);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public @NonNull byte[] delete(@NonNull byte[] challenge)  {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ApiImplS.callDelete(mCredential, challenge);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public @NonNull byte[] update(@NonNull PersonalizationData personalizationData) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ApiImplS.callUpdate(mCredential,
                    HardwareWritableIdentityCredential.convertPDFromJetpack(personalizationData));
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
