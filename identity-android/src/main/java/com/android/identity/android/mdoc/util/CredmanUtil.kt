/*
 *  Copyright 2023 Google LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.identity.android.mdoc.util

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.SimpleValue
import com.android.identity.internal.Util
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.proto.HpkeAead
import com.google.crypto.tink.proto.HpkeKdf
import com.google.crypto.tink.proto.HpkeKem
import com.google.crypto.tink.proto.HpkeParams
import com.google.crypto.tink.proto.HpkePrivateKey
import com.google.crypto.tink.proto.HpkePublicKey
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.ByteString
import com.google.crypto.tink.subtle.EllipticCurves
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException


class CredmanUtil(private val publicKey: PublicKey, private val privateKey: PrivateKey? = null) {

    private lateinit var publicKeysetHandle: KeysetHandle
    private var privateKeysetHandle: KeysetHandle? = null

    companion object {
        private const val ANDROID_HANDOVER_V1 = "AndroidHandoverv1"
        private const val BROWSER_HANDOVER_V1 = "BrowserHandoverv1"
        private const val ANDROID_CREDENTIAL_DOCUMENT_VERSION = "ANDROID-HPKE-v1"
        private const val PRIMARY_KEY_ID = 1
        private const val ENCAPSULATED_KEY_LENGTH = 65

        private const val EC_ALGORITHM = "EC"
        private const val EC_CURVE = "secp256r1"

        // CredentialDocument = {
        //    "version": tstr,                                // Set to "ANDROID-HPKE-v1"
        //    "encryptionParameters": EncryptionParameters,
        //    "cipherText": bstr                              // The encrypted data
        // }
        //
        // EncryptionParameters = {
        //   "pkEm" :  bstr,                                  // An ephemeral key
        // }
        //
        // TODO: probably need to stuff some stuff into `EncryptionParameters` to bind to
        //  the session
        //
        fun generateCredentialDocument(cipherText: ByteArray,
                                       encapsulatedPublicKey: PublicKey
        ): ByteArray {
            val baos = ByteArrayOutputStream()
            CborEncoder(baos).encode(
                CborBuilder()
                    .addMap()
                    .put("version", ANDROID_CREDENTIAL_DOCUMENT_VERSION)
                    .putMap("encryptionParameters")
                    .put("pkEm", Util.publicKeyToUncompressed(encapsulatedPublicKey))
                    .end()
                    .put("cipherText", cipherText)
                    .end()
                    .build()
            )
            return baos.toByteArray()
        }

        fun parseCredentialDocument(encodedCredentialDocument: ByteArray
        ): Pair<ByteArray, PublicKey> {
            val credentialDocument = Util.cborDecode(encodedCredentialDocument)
            val version = Util.cborMapExtractString(credentialDocument, "version")
            if (!version.equals(ANDROID_CREDENTIAL_DOCUMENT_VERSION)) {
                throw IllegalArgumentException("Unexpected version $version")
            }
            val encryptionParameters = Util.cborMapExtractMap(credentialDocument, "encryptionParameters")
            val pkEm = Util.cborMapExtractByteString(encryptionParameters, "pkEm")
            val encapsulatedPublicKey = Util.publicKeyFromUncompressed(pkEm)
            val cipherText = Util.cborMapExtractByteString(credentialDocument,"cipherText")
            return Pair(cipherText, encapsulatedPublicKey)
        }

        //    SessionTranscript = [
        //      null, // DeviceEngagementBytes not available
        //      null, // EReaderKeyBytes not available
        //      AndroidHandover // defined below
        //    ]
        //
        //    AndroidHandover = [
        //      "AndroidHandoverv1", // Version number
        //      nonce, // nonce that comes from request
        //      appId, // RP package name
        //      pkRHash, // The SHA256 hash of the recipient public key.
        //    ]
        fun generateAndroidSessionTranscript(
            nonce: ByteArray,
            publicKey: PublicKey,
            packageName: String
        ): ByteArray {
            val baos = ByteArrayOutputStream()
            CborEncoder(baos).encode(
                CborBuilder()
                    .addArray() // SessionTranscript
                    .add(SimpleValue.NULL) // DeviceEngagementBytes
                    .add(SimpleValue.NULL) // EReaderKeyBytes
                    .addArray() // AndroidHandover
                    .add(ANDROID_HANDOVER_V1)
                    .add(nonce)
                    .add(packageName.toByteArray())
                    .add(generatePublicKeyHash(publicKey))
                    .end()
                    .end()
                    .build()
            )
            return baos.toByteArray()
        }

        private fun generateOriginInfoBytes(origin: String): ByteArray {
            val baos = ByteArrayOutputStream()
            CborEncoder(baos).encode(
                CborBuilder()
                    .addMap()
                    .put("cat", 1)
                    .put("type", 1)
                    .putMap("details")
                    .put("baseUrl", origin)
                    .end()
                    .end()
                    .build()
            )

            return baos.toByteArray()
        }

        //    SessionTranscript = [
        //      null, // DeviceEngagementBytes not available
        //      null, // EReaderKeyBytes not available
        //      AndroidHandover // defined below
        //    ]
        //
        //    From https://github.com/WICG/mobile-document-request-api
        //
        //    BrowserHandover = [
        //      "BrowserHandoverv1",
        //      nonce,
        //      OriginInfoBytes, // origin of the request as defined in ISO/IEC 18013-7
        //      RequesterIdentity, // ? (omitting)
        //      pkRHash
        //    ]
        fun generateBrowserSessionTranscript(
            nonce: ByteArray,
            origin: String,
            publicKey: PublicKey
        ): ByteArray {
            val originInfoBytes = generateOriginInfoBytes(origin)

            val baos = ByteArrayOutputStream()
            CborEncoder(baos).encode(
                CborBuilder()
                    .addArray() // SessionTranscript
                    .add(SimpleValue.NULL) // DeviceEngagementBytes
                    .add(SimpleValue.NULL) // EReaderKeyBytes
                    .addArray() // BrowserHandover
                    .add(BROWSER_HANDOVER_V1)
                    .add(nonce)
                    .add(originInfoBytes)
                    .add(generatePublicKeyHash(publicKey))
                    .end()
                    .end()
                    .build()
            )
            return baos.toByteArray()
        }

        private fun generatePublicKeyHash(publicKey: PublicKey): ByteArray {
            publicKey as ECPublicKey
            val encodedKey = EllipticCurves.pointEncode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                publicKey.w
            )

            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(encodedKey)
        }

        private fun encodePublicKey(publicKey: ECPublicKey): ByteArray = EllipticCurves.pointEncode(
            EllipticCurves.CurveType.NIST_P256,
            EllipticCurves.PointFormatType.UNCOMPRESSED,
            publicKey.w
        )

        private fun decodePublicKey(encoded: ByteArray): ECPublicKey {
            val w = EllipticCurves.pointDecode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                encoded
            )

            var paramSpec: ECParameterSpec? = null
            paramSpec = try {
                val algorithmParameters =
                    AlgorithmParameters.getInstance(EC_ALGORITHM)
                algorithmParameters.init(ECGenParameterSpec(EC_CURVE))
                algorithmParameters.getParameterSpec(ECParameterSpec::class.java)
            } catch (e: InvalidParameterSpecException) {
                throw RuntimeException(e)
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }

            return try {
                val factory =
                    KeyFactory.getInstance(EC_ALGORITHM)
                factory.generatePublic(ECPublicKeySpec(w, paramSpec)) as ECPublicKey
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            } catch (e: InvalidKeySpecException) {
                throw RuntimeException(e)
            }
        }
    }

    constructor(keyPair: KeyPair) : this(keyPair.public, keyPair.private)

    init {
        TinkConfig.register()
        HybridConfig.register()
        initializeKeysetHandles()
    }

    private fun initializeKeysetHandles() {
        val params = HpkeParams.newBuilder()
            .setAead(HpkeAead.AES_128_GCM)
            .setKdf(HpkeKdf.HKDF_SHA256)
            .setKem(HpkeKem.DHKEM_P256_HKDF_SHA256)
            .build()

        publicKey as ECPublicKey
        val encodedKey = EllipticCurves.pointEncode(
            EllipticCurves.CurveType.NIST_P256,
            EllipticCurves.PointFormatType.UNCOMPRESSED,
            publicKey.w
        )

        val hpkePublicKey = HpkePublicKey.newBuilder()
            .setVersion(0)
            .setPublicKey(ByteString.copyFrom(encodedKey))
            .setParams(params)
            .build()

        val publicKeyData = KeyData.newBuilder()
            .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PUBLIC)
            .setTypeUrl("type.googleapis.com/google.crypto.tink.HpkePublicKey")
            .setValue(hpkePublicKey.toByteString())
            .build()

        val publicKeysetKey = Keyset.Key.newBuilder()
            .setKeyId(PRIMARY_KEY_ID)
            .setKeyData(publicKeyData)
            .setOutputPrefixType(OutputPrefixType.RAW)
            .setStatus(KeyStatusType.ENABLED)
            .build()

        val publicKeyset = Keyset.newBuilder()
            .setPrimaryKeyId(PRIMARY_KEY_ID)
            .addKey(publicKeysetKey)
            .build()

        publicKeysetHandle = TinkProtoKeysetFormat.parseKeyset(
            publicKeyset.toByteArray(),
            InsecureSecretKeyAccess.get()
        )

        if (privateKey != null) {
            privateKey as ECPrivateKey
            val hpkePrivateKey = HpkePrivateKey.newBuilder()
                .setPublicKey(hpkePublicKey)
                .setPrivateKey(ByteString.copyFrom(privateKey.s.toByteArray()))
                .build()

            val privateKeyData = KeyData.newBuilder()
                .setKeyMaterialType(KeyData.KeyMaterialType.ASYMMETRIC_PRIVATE)
                .setTypeUrl("type.googleapis.com/google.crypto.tink.HpkePrivateKey")
                .setValue(hpkePrivateKey.toByteString())
                .build()

            val privateKeysetKey = Keyset.Key.newBuilder()
                .setKeyId(PRIMARY_KEY_ID)
                .setKeyData(privateKeyData)
                .setOutputPrefixType(OutputPrefixType.RAW)
                .setStatus(KeyStatusType.ENABLED)
                .build()
            val privateKeyset = Keyset.newBuilder()
                .setPrimaryKeyId(PRIMARY_KEY_ID)
                .addKey(privateKeysetKey)
                .build()
            privateKeysetHandle = TinkProtoKeysetFormat.parseKeyset(
                privateKeyset.toByteArray(),
                InsecureSecretKeyAccess.get()
            )
        }
    }

    fun encrypt(plainText: ByteArray, aad: ByteArray): Pair<ByteArray, ECPublicKey> {
        val encryptor = publicKeysetHandle.getPrimitive(HybridEncrypt::class.java)

        val output = encryptor.encrypt(plainText, aad)

        val encapsulatedKeyBytes = output.sliceArray(0 until ENCAPSULATED_KEY_LENGTH)
        val encapsulatedKey = decodePublicKey(encapsulatedKeyBytes)

        val encryptedData = output.sliceArray(ENCAPSULATED_KEY_LENGTH until output.size)

        return Pair(encryptedData, encapsulatedKey)
    }

    fun decrypt(cipherText: ByteArray, encapsulatedKey: ECPublicKey, aad: ByteArray): ByteArray {
        check(privateKeysetHandle != null)

        val decryptor = privateKeysetHandle!!.getPrimitive(HybridDecrypt::class.java)
        return decryptor.decrypt(encodePublicKey(encapsulatedKey) + cipherText, aad)
    }


}