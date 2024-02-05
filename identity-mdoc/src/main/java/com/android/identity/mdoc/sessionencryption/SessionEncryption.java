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

package com.android.identity.mdoc.sessionencryption;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborBuilder;
import com.android.identity.cbor.CborMap;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.MapBuilder;
import com.android.identity.cbor.Tagged;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate;

import org.bouncycastle.util.BigIntegers;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Helper class for implementing session encryption according to ISO/IEC 18013-5:2021
 * section 9.1.1 Session encryption.
 */
public final class SessionEncryption {

    private static final String TAG = "SessionEncryption";

    public static final int ROLE_MDOC = 0;
    public static final int ROLE_MDOC_READER = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {ROLE_MDOC, ROLE_MDOC_READER})
    public @interface Role {
    }

    protected final @Role int mRole;
    private boolean mSessionEstablishmentSent;

    private final EcPrivateKey mESelfKey;

    private final byte[] mSKRemote;
    private final byte[] mSKSelf;
    private int mDecryptedCounter = 1;
    private int mEncryptedCounter = 1;
    private boolean mSendSessionEstablishment = true;

    /**
     * Creates a new {@link SessionEncryption} object.
     *
     * <p>The <code>DeviceEngagement</code> and <code>Handover</code> CBOR referenced in the
     * parameters below must conform to the CDDL in ISO 18013-5.
     *
     * All references to a "remote" device refer to a device with the opposite role. For example,
     * {@link SessionEncryption} objects with the <code>ROLE_MDOC</code> role will encrypt messages
     * with the remote mDL reader as the intended receiver, so the reader is the remote device.
     *
     * @param role The role of the SessionEncryption object
     * @param keyPair The ephemeral key pair of the role (e.g. if <code>ROLE_MDOC_READER</code>,
     *                then use the ephemeral key pair of the reader, and if <code>ROLE_MDOC</code>,
     *                then use the ephemeral key pair of the holder).
     * @param remotePublicKey The public ephemeral key of the remote device.
     * @param encodedSessionTranscript The bytes of the <code>SessionTranscript</code> CBOR.
     */
    public SessionEncryption(@Role int role,
                             @NonNull EcPrivateKey keyPair,
                             @NonNull EcPublicKey remotePublicKey,
                             @NonNull byte[] encodedSessionTranscript) {
        mRole = role;
        mESelfKey = keyPair;

        byte[] sharedSecret = Crypto.keyAgreement(mESelfKey, remotePublicKey);

        byte[] sessionTranscriptBytes = Cbor.encode(new Tagged(24, new Bstr(encodedSessionTranscript)));
        byte[] salt = Crypto.digest(Algorithm.SHA256, sessionTranscriptBytes);
        byte[] info = "SKDevice".getBytes(UTF_8);
        byte[] deviceSK = Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32);

        info = "SKReader".getBytes(UTF_8);
        byte[] readerSK = Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32);

        if (mRole == ROLE_MDOC) {
            mSKSelf = deviceSK;
            mSKRemote = readerSK;
        } else {
            mSKSelf = readerSK;
            mSKRemote = deviceSK;
        }
    }

    /**
     * Configure whether to send <code>SessionEstablishment</code> as the first message. Only a
     * SessionEncryption with the role <code>ROLE_MDOC_READER</code> will send a
     * SessionEstablishment message.
     *
     * <p>If set to <code>false</code> the first message to the mdoc will <em>not</em>
     * contain <code>eReaderKey</code>. This is useful for situations where this key has
     * already been conveyed out-of-band, for example via reverse engagement.
     *
     * <p>The default value for this is <code>true</code>.
     *
     * @param sendSessionEstablishment whether to send <code>SessionEstablishment</code>
     *                                 as the first message.
     * @throws IllegalStateException if role is <code>ROLE_MDOC</code>
     */
    public void setSendSessionEstablishment(boolean sendSessionEstablishment) {
        if (mRole == ROLE_MDOC) {
            throw new IllegalStateException("Only readers should be sending sessionEstablishment" +
                    "messages, but the role is ROLE_MDOC");
        }
        mSendSessionEstablishment = sendSessionEstablishment;
    }

    private @NonNull byte[] encryptMessageHelper(@Nullable byte[] messagePlaintext,
                                                 @NonNull OptionalLong statusCode,
                                                 boolean setInvalidEReaderKey) {
        byte[] messageCiphertext = null;
        if (messagePlaintext != null) {
            // The IV and these constants are specified in ISO/IEC 18013-5:2021 clause 9.1.1.5.
            ByteBuffer iv = ByteBuffer.allocate(12);
            iv.putInt(0, 0x00000000);
            int ivIdentifier = mRole == ROLE_MDOC? 0x00000001 : 0x00000000;
            iv.putInt(4, ivIdentifier);
            iv.putInt(8, mEncryptedCounter);
            messageCiphertext = Crypto.encrypt(Algorithm.A128GCM, mSKSelf, iv.array(), messagePlaintext);
            mEncryptedCounter += 1;
        }

        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        if (!mSessionEstablishmentSent && mSendSessionEstablishment && mRole == ROLE_MDOC_READER) {
            EcPublicKey eReaderKey = mESelfKey.getPublicKey();
            if (setInvalidEReaderKey) {
                eReaderKey = new EcPublicKeyDoubleCoordinate(
                        eReaderKey.getCurve(),
                        ((EcPublicKeyDoubleCoordinate) eReaderKey).getX(),
                        BigIntegers.asUnsignedByteArray(32, BigInteger.valueOf(42)));
            }
            mapBuilder.putTaggedEncodedCbor("eReaderKey", Cbor.encode(eReaderKey.toCoseKey(Map.of()).getDataItem()));
            if (messageCiphertext == null) {
                throw new IllegalStateException("Data cannot be empty in initial message");
            }
        }
        if (messageCiphertext != null) {
            mapBuilder.put("data", messageCiphertext);
        }
        if (statusCode.isPresent()) {
            mapBuilder.put("status", statusCode.getAsLong());
        }
        mapBuilder.end();
        byte[] messageData = Cbor.encode(mapBuilder.end().build());

        mSessionEstablishmentSent = true;

        return messageData;
    }

    /**
     * Encrypt a message intended for the remote device.
     *
     * <p>This method returns <code>SessionEstablishment</code> CBOR for the first call and
     * <code>SessionData</code> CBOR for subsequent calls. These CBOR data structures are
     * defined in ISO 18013-5 9.1.1 Session encryption.
     *
     * @param messagePlaintext if not <code>null</code>, the message to encrypt and include
     *                         in <code>SessionData</code>.
     * @param statusCode if set, the status code to include in <code>SessionData</code>.
     * @return the bytes of the <code>SessionEstablishment</code> or <code>SessionData</code>
     *     CBOR as described above.
     */
    public @NonNull byte[] encryptMessage(@Nullable byte[] messagePlaintext,
                                          @NonNull OptionalLong statusCode) {
        return encryptMessageHelper(messagePlaintext, statusCode, false);
    }

    // Only used for testing, will produce a SessionEstablishment message with an
    // invalid COSE_Key for EReaderKey
    @NonNull
    public byte[] encryptMessageWithInvalidEReaderKey(
            @Nullable byte[] messagePlaintext,
            @NonNull OptionalLong statusCode) {
        return encryptMessageHelper(messagePlaintext, statusCode, true);
    }

    /**
     * Create a SessionData message (as defined in ISO/IEC 18013-5 9.1.1.4 Procedure) with a status
     * code and no data.
     *
     * @param statusCode the intended status code, with value as defined in ISO/IEC 18013-5 Table 20.
     * @return a byte array with the encoded CBOR message
     */
    static public @NonNull byte[] encodeStatus(long statusCode) {
        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        mapBuilder.put("status", statusCode);
        mapBuilder.end();
        return Cbor.encode(mapBuilder.end().build());
    }

    /**
     * Contains the data of a decrypted session message from 18013-5.
     */
    public static class DecryptedMessage {
        private final byte[] mData;
        private final OptionalLong mStatus;

        private DecryptedMessage(@Nullable byte[] data, @NonNull OptionalLong status) {
            mData = data;
            mStatus = status;
        }

        /**
         * Gets the decrypted data.
         *
         * @return the decrypted data or {@code null} if the data wasn't set.
         */
        public @Nullable byte[] getData() {
            return mData;
        }

        /**
         * Gets the status, if any.
         *
         * @return An {@link OptionalLong} which is empty if the status wasn't set.
         */
        public @NonNull OptionalLong getStatus() {
            return mStatus;
        }
    }

    /**
     * Decrypts a message received from the remote device.
     *
     * <p>This method expects the passed-in data to conform to the <code>SessionData</code>
     * DDL as defined in ISO 18013-5 9.1.1 Session encryption.
     *
     * @param messageData the bytes of the <code>SessionData</code> CBOR as described above.
     * @return The decrypted message.
     * @exception IllegalArgumentException if the passed in data does not conform to the CDDL.
     * @exception IllegalStateException if decryption fails.
     */
    public @NonNull DecryptedMessage decryptMessage(
            @NonNull byte[] messageData) {
        DataItem map = Cbor.decode(messageData);

        DataItem dataDataItem = map.getOrNull("data");
        byte[] messageCiphertext = null;
        if (dataDataItem != null) {
            messageCiphertext = dataDataItem.getAsBstr();
        }

        DataItem statusDataItem = map.getOrNull("status");
        OptionalLong status = OptionalLong.empty();
        if (statusDataItem != null) {
            status = OptionalLong.of(statusDataItem.getAsNumber());
        }

        byte[] plainText = null;
        if (messageCiphertext != null) {
            ByteBuffer iv = ByteBuffer.allocate(12);
            iv.putInt(0, 0x00000000);
            int ivIdentifier = mRole == ROLE_MDOC? 0x00000000 : 0x00000001;
            iv.putInt(4, ivIdentifier);
            iv.putInt(8, mDecryptedCounter);
            plainText = Crypto.decrypt(Algorithm.A128GCM, mSKRemote, iv.array(), messageCiphertext);
            mDecryptedCounter += 1;
        }

        return new DecryptedMessage(plainText, status);
    }

    /**
     * Gets the number of messages encrypted with {@link #encryptMessage(byte[], OptionalLong)}.
     *
     * @return Number of messages encrypted.
     */
    public int getNumMessagesEncrypted() {
        return mEncryptedCounter - 1;
    }

    /**
     * Gets the number of messages decrypted with {@link #decryptMessage(byte[])}
     *
     * @return Number of messages decrypted.
     */
    public int getNumMessagesDecrypted() {
        return mDecryptedCounter - 1;
    }
}
