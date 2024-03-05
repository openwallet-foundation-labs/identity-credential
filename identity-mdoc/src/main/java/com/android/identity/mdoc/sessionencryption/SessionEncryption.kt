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
package com.android.identity.mdoc.sessionencryption

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Tagged
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import java.nio.ByteBuffer

/**
 * Helper class for implementing session encryption according to ISO/IEC 18013-5:2021
 * section 9.1.1 Session encryption.
 *
 * The `DeviceEngagement` and `Handover` CBOR referenced in the
 * parameters below must conform to the CDDL in ISO 18013-5.
 *
 * All references to a "remote" device refer to a device with the opposite role. For example,
 * [SessionEncryption] objects with the [Role.MDOC] role will encrypt messages
 * with the remote mdoc reader as the intended receiver, so the reader is the remote device.
 *
 * @param role the role that the object should act as.
 * @param eSelfKey The ephemeral private key e.g. in the [Role.MDOC_READER] role,
 * it's the ephemeral private key for the mdoc reader, and in the [Role.MDOC] role
 * it's the for the mdoc.
 * @param remotePublicKey The public ephemeral key of the other end.
 * @param encodedSessionTranscript The bytes of the `SessionTranscript` CBOR.
 */
class SessionEncryption(
    val role: Role,
    private val eSelfKey: EcPrivateKey,
    remotePublicKey: EcPublicKey,
    encodedSessionTranscript: ByteArray
) {
    /**
     * Enumeration for the two different sides of an encrypted channel.
     */
    enum class Role {
        /** The role of acting as an mdoc. */
        MDOC,

        /** The role of acting as a mdoc reader. */
        MDOC_READER
    }

    private var sessionEstablishmentSent = false
    private val skRemote: ByteArray
    private val skSelf: ByteArray
    private var decryptedCounter = 1
    private var encryptedCounter = 1
    private var sendSessionEstablishment = true

    init {
        val sharedSecret = Crypto.keyAgreement(eSelfKey, remotePublicKey)
        val sessionTranscriptBytes = Cbor.encode(Tagged(24, Bstr(encodedSessionTranscript)))
        val salt = Crypto.digest(Algorithm.SHA256, sessionTranscriptBytes)
        var info = "SKDevice".toByteArray()
        val deviceSK = Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32)
        info = "SKReader".toByteArray()
        val readerSK = Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32)
        if (role == Role.MDOC) {
            skSelf = deviceSK
            skRemote = readerSK
        } else {
            skSelf = readerSK
            skRemote = deviceSK
        }
    }

    /**
     * Configure whether to send `SessionEstablishment` as the first message. Only an
     * object with the role [Role.MDOC_READER] will want to do this.
     *
     * If set to false the first message to the mdoc will *not* contain `eReaderKey`. This is
     * useful for situations where this key has already been conveyed out-of-band, for example
     * via reverse engagement.
     *
     * The default value for this is `true`.
     *
     * @param sendSessionEstablishment whether to send `SessionEstablishment` as the first message.
     * @throws IllegalStateException if role is [Role.MDOC_READER]
     */
    fun setSendSessionEstablishment(sendSessionEstablishment: Boolean) {
        check(role != Role.MDOC) {
            "Only mdoc readers should be sending sessionEstablishment messages " +
                    " but this object was constructed with role MDOC"
        }
        this.sendSessionEstablishment = sendSessionEstablishment
    }

    /**
     * Encrypt a message intended for the remote device.
     *
     *
     * This method returns `SessionEstablishment` CBOR for the first call and
     * `SessionData` CBOR for subsequent calls. These CBOR data structures are
     * defined in ISO 18013-5 9.1.1 Session encryption.
     *
     * @param messagePlaintext if not `null`, the message to encrypt and include
     * in `SessionData`.
     * @param statusCode if set, the status code to include in `SessionData`.
     * @return the bytes of the `SessionEstablishment` or `SessionData`
     * CBOR as described above.
     */
    fun encryptMessage(
        messagePlaintext: ByteArray?,
        statusCode: Long?
    ): ByteArray {
        var messageCiphertext: ByteArray? = null
        if (messagePlaintext != null) {
            // The IV and these constants are specified in ISO/IEC 18013-5:2021 clause 9.1.1.5.
            val iv = ByteBuffer.allocate(12)
            iv.putInt(0, 0x00000000)
            val ivIdentifier = if (role == Role.MDOC) 0x00000001 else 0x00000000
            iv.putInt(4, ivIdentifier)
            iv.putInt(8, encryptedCounter)
            messageCiphertext = Crypto.encrypt(
                Algorithm.A128GCM, skSelf, iv.array(), messagePlaintext
            )
            encryptedCounter += 1
        }
        val mapBuilder = CborMap.builder()
        if (!sessionEstablishmentSent && sendSessionEstablishment && role == Role.MDOC_READER) {
            var eReaderKey = eSelfKey.publicKey
            mapBuilder.putTaggedEncodedCbor(
                "eReaderKey",
                Cbor.encode(eReaderKey.toCoseKey().toDataItem)
            )
            checkNotNull(messageCiphertext) { "Data cannot be empty in initial message" }
        }
        if (messageCiphertext != null) {
            mapBuilder.put("data", messageCiphertext)
        }
        if (statusCode != null) {
            mapBuilder.put("status", statusCode)
        }
        mapBuilder.end()
        val messageData = Cbor.encode(mapBuilder.end().build())
        sessionEstablishmentSent = true
        return messageData
    }

    /**
     * Decrypts a message received from the remote device.
     *
     *
     * This method expects the passed-in data to conform to the `SessionData`
     * DDL as defined in ISO 18013-5 9.1.1 Session encryption.
     *
     * @param messageData the bytes of the `SessionData` CBOR as described above.
     * @return The decrypted message as data and status.
     * @exception IllegalArgumentException if the passed in data does not conform to the CDDL.
     * @exception IllegalStateException if decryption fails.
     */
    fun decryptMessage(
        messageData: ByteArray
    ): Pair<ByteArray?, Long?> {
        val map = Cbor.decode(messageData)
        val dataDataItem = map.getOrNull("data")
        var messageCiphertext: ByteArray? = null
        if (dataDataItem != null) {
            messageCiphertext = dataDataItem.asBstr
        }
        val statusDataItem = map.getOrNull("status")
        val status = statusDataItem?.asNumber
        var plainText: ByteArray? = null
        if (messageCiphertext != null) {
            val iv = ByteBuffer.allocate(12)
            iv.putInt(0, 0x00000000)
            val ivIdentifier = if (role == Role.MDOC) 0x00000000 else 0x00000001
            iv.putInt(4, ivIdentifier)
            iv.putInt(8, decryptedCounter)
            plainText = Crypto.decrypt(Algorithm.A128GCM, skRemote, iv.array(), messageCiphertext)
            decryptedCounter += 1
        }
        return Pair(plainText, status)
    }

    /**
     * The number of messages encrypted with [.encryptMessage].
     */
    val numMessagesEncrypted: Int
        get() = encryptedCounter - 1

    /**
     * The number of messages decrypted with [.decryptMessage].
     */
    val numMessagesDecrypted: Int
        get() = decryptedCounter - 1

    companion object {
        /**
         * Create a SessionData message (as defined in ISO/IEC 18013-5 9.1.1.4 Procedure) with a status
         * code and no data.
         *
         * @param statusCode the intended status code, with value as defined in ISO/IEC 18013-5 Table 20.
         * @return a byte array with the encoded CBOR message
         */
        @JvmStatic
        fun encodeStatus(statusCode: Long): ByteArray {
            val mapBuilder = CborMap.builder()
            mapBuilder.put("status", statusCode)
            mapBuilder.end()
            return Cbor.encode(mapBuilder.end().build())
        }
    }
}
