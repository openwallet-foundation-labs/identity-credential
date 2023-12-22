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

package com.android.identity.mdoc.serverretrieval

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayInputStream
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

/**
 * Class representing a JSON Web Tokens
 *
 * @param header the header of the JSON Web Token
 * @param payload the payload of the JSON Web Token
 * @param signature the signature over the header and the payload
 */
class Jwt private constructor(
    val header: JsonObject,
    val payload: JsonObject,
    val signature: ByteArray
) {
    companion object {

        // currently only SHA256withECDSA is supported/needed
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        /**
         * Verify an encoded JSON Web Token.
         *
         * @param encodedJwt the encoded JSON Web Token.
         * @param publicKey optional public key. If empty the header should
         * contain a certificate chain containing the public key
         * @return a boolean indicating that the JSON Web Token could be verified
         */
        fun verify(encodedJwt: String, publicKey: ECPublicKey? = null): Boolean {
            var pk = publicKey
            val jwt = decode(encodedJwt)
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            if (pk == null) {
                val rawCertificateChain = (jwt.header["x5c"] as JsonArray).map {
                    it.jsonPrimitive.content
                }
                val certificateChain = parseCertificateChain(rawCertificateChain)
                pk = certificateChain.first().publicKey as ECPublicKey
            }
            signatureAlgorithm.initVerify(pk)
            signatureAlgorithm.update(encodedJwt.substringBeforeLast(".").toByteArray())
            val signature = Base64.getUrlDecoder().decode(encodedJwt.substringAfterLast("."))
            return signatureAlgorithm.verify(signature)
        }

        /**
         * Decode an encode JSON Web Token
         */
        fun decode(encodedJwt: String): Jwt {
            val decoded = encodedJwt.split(".").map { Base64.getUrlDecoder().decode(it) }
            val header: JsonObject = Json.Default.decodeFromString(String(decoded[0]))
            val payload: JsonObject = Json.Default.decodeFromString(String(decoded[1]))
            val signature = decoded[2]
            return Jwt(header, payload, signature)
        }

        /**
         * Encode a JSON Object to a JSON Web Token
         *
         * @param payload the JSON Object that will be encoded
         * @param privateKey the key to sign the JSON Web Token
         * @param certificateChain an optional certificate chain that can be
         * added to the JSON Web Token
         */
        fun encode(
            payload: JsonElement,
            privateKey: ECPrivateKey,
            certificateChain: List<X509Certificate> = emptyList()
        ): String {
            // create and encode header
            val headerJson = buildJsonObject {
                put("alg", "ES256")
                put("typ", "JWT")
                if (certificateChain.isNotEmpty()) {
                    putJsonArray("x5c") {
                        certificateChain.map {
                            String(Base64.getEncoder().encode(it.encoded))
                        }.forEach {
                            add(it)
                        }
                    }
                }
            }.toString()
            val headerBase64 = String(Base64.getUrlEncoder().encode(headerJson.toByteArray()))

            // encode payload
            val payloadBase64 =
                String(Base64.getUrlEncoder().encode(payload.toString().toByteArray()))

            // create and encode signature
            val signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureAlgorithm.initSign(privateKey)
            signatureAlgorithm.update("$headerBase64.$payloadBase64".toByteArray())
            val newSignature = signatureAlgorithm.sign()
            val signatureBase64 = String(Base64.getUrlEncoder().encode(newSignature))

            // return the encoded result
            return "$headerBase64.$payloadBase64.$signatureBase64"
        }

        /**
         * Parse a list of Base 64 strings to [X509Certificate]s.
         *
         * @param certificateChain the Base 64 encoded certificates
         * @return the parsed [X509Certificate]s.
         */
        fun parseCertificateChain(certificateChain: List<String>): List<X509Certificate> {
            return certificateChain.map {
                val bytes = Base64.getDecoder().decode(it)
                CertificateFactory.getInstance("X509")
                    .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            }
        }
    }
}