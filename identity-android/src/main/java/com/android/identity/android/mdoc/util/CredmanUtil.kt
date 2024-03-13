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

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Simple
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
import org.bouncycastle.util.BigIntegers
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
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
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException
import java.util.Arrays


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
            return Cbor.encode(
                CborMap.builder()
                    .put("version", ANDROID_CREDENTIAL_DOCUMENT_VERSION)
                    .putMap("encryptionParameters")
                    .put("pkEm", publicKeyToUncompressed(encapsulatedPublicKey))
                    .end()
                    .put("cipherText", cipherText)
                    .end()
                    .build()
            )
        }

        fun parseCredentialDocument(encodedCredentialDocument: ByteArray
        ): Pair<ByteArray, PublicKey> {
            val map = Cbor.decode(encodedCredentialDocument)
            val version = map["version"].asTstr
            if (!version.equals(ANDROID_CREDENTIAL_DOCUMENT_VERSION)) {
                throw IllegalArgumentException("Unexpected version $version")
            }
            val encryptionParameters = map["encryptionParameters"]
            val pkEm = encryptionParameters["pkEm"].asBstr
            val encapsulatedPublicKey = publicKeyFromUncompressed(pkEm)
            val cipherText = map["cipherText"].asBstr
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
            return Cbor.encode(
                CborArray.builder()
                    .add(Simple.NULL) // DeviceEngagementBytes
                    .add(Simple.NULL) // EReaderKeyBytes
                    .addArray() // AndroidHandover
                    .add(ANDROID_HANDOVER_V1)
                    .add(nonce)
                    .add(packageName.toByteArray())
                    .add(generatePublicKeyHash(publicKey))
                    .end()
                    .end()
                    .build()
            )
        }

        private fun generateOriginInfoBytes(origin: String): ByteArray {
            return Cbor.encode(
                CborMap.builder()
                    .put("cat", 1)
                    .put("type", 1)
                    .putMap("details")
                    .put("baseUrl", origin)
                    .end()
                    .end()
                    .build()
            )
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

            return Cbor.encode(
                CborArray.builder()
                    .add(Simple.NULL) // DeviceEngagementBytes
                    .add(Simple.NULL) // EReaderKeyBytes
                    .addArray() // BrowserHandover
                    .add(BROWSER_HANDOVER_V1)
                    .add(nonce)
                    .add(originInfoBytes)
                    .add(generatePublicKeyHash(publicKey))
                    .end()
                    .end()
                    .build()
            )
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

        /* Encodes an integer according to Section 2.3.5 Field-Element-to-Octet-String Conversion
        *  * of SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
        *  */
        private fun sec1EncodeFieldElementAsOctetString(
            octetStringSize: Int,
            fieldValue: BigInteger?
        ): ByteArray = BigIntegers.asUnsignedByteArray(octetStringSize, fieldValue)

        fun publicKeyToUncompressed(publicKey: PublicKey): ByteArray {
            val ecKey = publicKey as ECPublicKey
            val w: ECPoint = ecKey.w
            val x: ByteArray = sec1EncodeFieldElementAsOctetString(32, w.getAffineX())
            val y: ByteArray = sec1EncodeFieldElementAsOctetString(32, w.getAffineY())
            val baos = ByteArrayOutputStream()
            baos.write(0x04)
            try {
                baos.write(x)
                baos.write(y)
            } catch (e: IOException) {
                throw java.lang.RuntimeException(e)
            }
            return baos.toByteArray()
        }

        fun publicKeyFromUncompressed(uncompressedPublicKey: ByteArray): PublicKey {
            require(uncompressedPublicKey.size == 65) { "Unexpected length " + uncompressedPublicKey.size + ", expected 65" }
            require(uncompressedPublicKey[0].toInt() == 0x04) { "Unexpected byte " + uncompressedPublicKey[0].toInt() + ", expected 0x04" }
            val encodedX: ByteArray = Arrays.copyOfRange(uncompressedPublicKey, 1, 33)
            val encodedY: ByteArray = Arrays.copyOfRange(uncompressedPublicKey, 33, 65)
            val x = BigInteger(1, encodedX)
            val y = BigInteger(1, encodedY)
            return try {
                val params =
                    AlgorithmParameters.getInstance("EC")
                params.init(ECGenParameterSpec("secp256r1"))
                val ecParameters =
                    params.getParameterSpec(
                        ECParameterSpec::class.java
                    )
                val ecPoint = ECPoint(x, y)
                val keySpec =
                    ECPublicKeySpec(ecPoint, ecParameters)
                val kf = KeyFactory.getInstance("EC")
                kf.generatePublic(keySpec) as ECPublicKey
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException("Unexpected error", e)
            } catch (e: InvalidParameterSpecException) {
                throw IllegalStateException("Unexpected error", e)
            } catch (e: InvalidKeySpecException) {
                throw IllegalStateException("Unexpected error", e)
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