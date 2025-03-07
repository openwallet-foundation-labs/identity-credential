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

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.Simple
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.mdoc.origininfo.OriginInfoDomain

object CredmanUtil {
    private const val ANDROID_HANDOVER_V1 = "AndroidHandoverv1"
    private const val BROWSER_HANDOVER_V1 = "BrowserHandoverv1"
    private const val ANDROID_CREDENTIAL_DOCUMENT_VERSION = "ANDROID-HPKE-v1"

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
                                   encapsulatedPublicKey: EcPublicKey
    ): ByteArray {
        encapsulatedPublicKey as EcPublicKeyDoubleCoordinate
        return Cbor.encode(
            CborMap.builder()
                .put("version", ANDROID_CREDENTIAL_DOCUMENT_VERSION)
                .putMap("encryptionParameters")
                .put("pkEm", encapsulatedPublicKey.asUncompressedPointEncoding)
                .end()
                .put("cipherText", cipherText)
                .end()
                .build()
        )
    }

    fun parseCredentialDocument(encodedCredentialDocument: ByteArray
    ): Pair<ByteArray, EcPublicKey> {
        val map = Cbor.decode(encodedCredentialDocument)
        val version = map["version"].asTstr
        if (!version.equals(ANDROID_CREDENTIAL_DOCUMENT_VERSION)) {
            throw IllegalArgumentException("Unexpected version $version")
        }
        val encryptionParameters = map["encryptionParameters"]
        val pkEm = encryptionParameters["pkEm"].asBstr
        val encapsulatedPublicKey =
            EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(EcCurve.P256, pkEm)
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
        packageName: String,
        requesterIdHash: ByteArray
    ): ByteArray {
        return Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray() // AndroidHandover
                .add(ANDROID_HANDOVER_V1)
                .add(nonce)
                .add(packageName.toByteArray())
                .add(requesterIdHash)
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
        requesterIdHash: ByteArray
    ): ByteArray {
        // TODO: Instead of hand-rolling this, we should use OriginInfoDomain which
        //   uses `domain` instead of `baseUrl` which is what the latest version of 18013-7
        //   calls for.
        val originInfoBytes = Cbor.encode(
            CborMap.builder()
                .put("cat", 1)
                .put("type", 1)
                .putMap("details")
                .put("baseUrl", origin)
                .end()
                .end()
                .build()
        )
        return Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL) // DeviceEngagementBytes
                .add(Simple.NULL) // EReaderKeyBytes
                .addArray() // BrowserHandover
                .add(BROWSER_HANDOVER_V1)
                .add(nonce)
                .add(originInfoBytes)
                .add(requesterIdHash)
                .end()
                .end()
                .build()
        )
    }
}