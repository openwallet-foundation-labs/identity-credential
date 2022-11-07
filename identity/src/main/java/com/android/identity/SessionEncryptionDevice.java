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

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.OptionalLong;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * A helper class for encrypting and decrypting messages exchanged with a remote
 * mDL reader, conforming to ISO 18013-5 9.1.1 Session encryption.
 */
final class SessionEncryptionDevice {

    private static final String TAG = "SessionEncryptionDevice";

    private byte[] mEncodedEReaderKeyPub;
    private PublicKey mEReaderKeyPub;

    private final PrivateKey mEDeviceKeyPrivate;

    private SecretKeySpec mSKDevice;
    private SecretKeySpec mSKReader;
    private int mSKDeviceCounter = 1;
    private int mSKReaderCounter = 1;

    /**
     * Creates a new {@link SessionEncryptionDevice} object.
     *
     * <p>The <code>DeviceEngagement</code> and <code>Handover</code> CBOR referenced in the
     * parameters below must conform to the CDDL in ISO 18013-5.
     *
     * @param eDeviceKeyPrivate the device private ephemeral key.
     * @param encodedSessionTranscript
     * @param eReaderKeyPublic
     */
    public SessionEncryptionDevice(@NonNull PrivateKey eDeviceKeyPrivate,
                                   @NonNull PublicKey eReaderKeyPublic,
                                   @NonNull byte[] encodedSessionTranscript) {
        mEDeviceKeyPrivate = eDeviceKeyPrivate;
        mEReaderKeyPub = eReaderKeyPublic;

        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(mEDeviceKeyPrivate);
            ka.doPhase(mEReaderKeyPub, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] sessionTranscriptBytes = Util.cborEncode(
                    Util.cborBuildTaggedByteString(encodedSessionTranscript));
            byte[] salt = MessageDigest.getInstance("SHA-256").digest(sessionTranscriptBytes);

            byte[] info = "SKDevice".getBytes(UTF_8);
            byte[] derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);

            mSKDevice = new SecretKeySpec(derivedKey, "AES");

            info = "SKReader".getBytes(UTF_8);
            derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);
            mSKReader = new SecretKeySpec(derivedKey, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Error deriving keys", e);
        }
    }

    /**
     * Encrypts a message to the remote mDL reader.
     *
     * <p>This method returns <code>SessionData</code> CBOR as defined in ISO 18013-5 9.1.1
     * Session encryption.
     *
     * @param messagePlaintext if not <code>null</code>, the message to encrypt and include
     *                         in <code>SessionData</code>.
     * @param statusCode if set, the status code to include in <code>SessionData</code>.
     * @return the bytes of the <code>SessionData</code> CBOR as described above.
     * @exception IllegalStateException if trying to send a message to the reader before a
     *                                  message from the reader has been received.
     */
    public @NonNull byte[] encryptMessageToReader(@Nullable byte[] messagePlaintext,
            @NonNull OptionalLong statusCode) {
        byte[] messageCiphertextAndAuthTag = null;
        if (messagePlaintext != null) {
            try {
                // The IV and these constants are specified in ISO/IEC 18013-5:2021 clause 9.1.1.5.
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
                throw new IllegalStateException("Error encrypting message", e);
            }
            mSKDeviceCounter += 1;
        }

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        if (messageCiphertextAndAuthTag != null) {
            mapBuilder.put("data", messageCiphertextAndAuthTag);
        }
        if (statusCode.isPresent()) {
            mapBuilder.put("status", statusCode.getAsLong());
        }
        mapBuilder.end();
        return Util.cborEncode(builder.build().get(0));
    }

    /**
     * Decrypts a message received from the remote mDL reader.
     *
     * <p>This method expects the passed-in data to conform to the <code>SessionData</code>
     * or <code>SessionEstablishment</code> CDDL as defined in ISO 18013-5 9.1.1 Session encryption.
     *
     * <p>The return value is a pair of two values where both values are optional. The
     * first element is the decrypted data and the second element is the status.
     *
     * @param messageData the bytes of the <code>SessionData</code> CBOR as described above.
     * @return <code>null</code> if decryption fails, otherwise a pair with the decrypted data and
     *         status, as described above.
     * @exception IllegalArgumentException if the passed in data does not conform to the CDDL.
     */
    public @Nullable Pair<byte[], OptionalLong> decryptMessageFromReader(
            @NonNull byte[] messageData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
        List<DataItem> dataItems = null;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalArgumentException("Data is not valid CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalArgumentException("Expected 1 item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof Map)) {
            throw new IllegalArgumentException("Item is not a map");
        }
        Map map = (Map) dataItems.get(0);

        DataItem dataItemData = map.get(new UnicodeString("data"));
        byte[] messageCiphertext = null;
        if (dataItemData != null) {
            if (!(dataItemData instanceof ByteString)) {
                throw new IllegalArgumentException("data is not a bstr");
            }
            messageCiphertext = ((ByteString) dataItemData).getBytes();
        }

        OptionalLong status = OptionalLong.empty();
        DataItem dataItemStatus = map.get(new UnicodeString("status"));
        if (dataItemStatus != null) {
            status = OptionalLong.of(Util.checkedLongValue(dataItemStatus));
        }

        byte[] plainText = null;
        if (messageCiphertext != null) {
            ByteBuffer iv = ByteBuffer.allocate(12);
            iv.putInt(0, 0x00000000);
            iv.putInt(4, 0x00000000);
            iv.putInt(8, mSKReaderCounter);
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
                return null;
            }
            mSKReaderCounter += 1;
        }

        return new Pair<>(plainText, status);
    }

    /**
     * Gets the number of messages encrypted with
     * {@link #encryptMessageToReader(byte[], OptionalLong)} .
     *
     * @return Number of messages encrypted.
     */
    public int getNumMessagesEncrypted() {
        return mSKDeviceCounter - 1;
    }

    /**
     * Gets the number of messages decrypted with {@link #decryptMessageFromReader(byte[])}.
     *
     * @return Number of messages decrypted.
     */
    public int getNumMessagesDecrypted() {
        return mSKReaderCounter - 1;
    }
}
