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

package com.android.identity.mdoc.serverretrieval.oidc

import com.android.identity.mdoc.serverretrieval.Jwt
import com.android.identity.util.Timestamp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey

/**
 * Class with the process of the Open ID Connect Server Retrieval as described
 * in ISO-18013-5 par 8.3.3.2.2 from a client perspective.
 *
 * @param oidcClient the client to communicate with the server
 * @param privateKey the private key used to sign a JSON Web Token that is
 * sent to the server
 *
 */
class OidcServerRetrievalProcess(
    private val oidcClient: OidcClient,
    private val privateKey: ECPrivateKey
) {
    /**
     * The actual process in 5 steps of the Open ID Connect Server Retrieval.
     *
     * @param serverRetrievalToken the token received from the Wallet App
     * @param docType the documentType of the requested data elements
     * @param documentRequest a map with namespaces, data elements and a value for
     * 'intent to retain'
     * @return a JSON String with the ID token, containing the mdoc data elements
     * with their values
     */
    fun process(
        serverRetrievalToken: String,
        docType: String,
        documentRequest: Map<String, Map<String, Boolean>>
    ): JsonObject {
        // step 1
        val configuration = Json.Default.decodeFromString<JsonObject>(oidcClient.configuration())

        // step 2
        val scope = documentRequest.map { ns -> ns.value.keys.map { el -> ns.key + ":" + el } }
            .flatten().joinToString(separator = " ")

        val registrationRequest = buildJsonObject {
            put("redirect_uris", buildJsonArray { add("http://127.0.0.1:56464/callback") }) // TODO
            put("scope", scope)
        }
        val registrationResponse = Json.Default.decodeFromString<JsonObject>(
            oidcClient.clientRegistration(registrationRequest.toString())
        )

        // step 3
        val thirtyDays = 30 * 24 * 60 * 60
        val authorizationRequest = mapOf(
            "client_id" to registrationResponse["client_id"]?.jsonPrimitive?.content.toString(),
            "scope" to registrationResponse["scope"]?.jsonPrimitive?.content.toString(),
            "redirect_uri" to registrationResponse["redirect_uri"]?.jsonPrimitive?.content.toString(),
            "response_type" to "code",
            "login_hint" to Jwt.encode(buildJsonObject {
                put("id", serverRetrievalToken)
                put("iat", Timestamp.now().toEpochMilli() / 1000)
                put("exp", (Timestamp.now().toEpochMilli() / 1000) + thirtyDays)
            }, privateKey)
        )

        val authorizationResponse =
            Json.Default.decodeFromString<JsonObject>(oidcClient.authorization(authorizationRequest))

        // step 4
        val tokenRequest = mapOf(
            "grant_type" to "authorization_code",
            "code" to authorizationResponse["Query"]?.jsonObject?.get("code")?.jsonPrimitive?.content.toString(),
            "redirect_uri" to registrationRequest["redirect_uris"]?.jsonArray?.first()?.jsonPrimitive?.content.toString(),
            "client_id" to registrationResponse["client_id"]?.jsonPrimitive?.content.toString(),
            "client_secret" to registrationResponse["client_secret"]?.jsonPrimitive?.content.toString()
        )
        val tokenResponse =
            Json.Default.decodeFromString<JsonObject>(oidcClient.getIdToken(tokenRequest))

        // step 5
        val validateIdTokenResponse =
            Json.Default.decodeFromString<JsonObject>(oidcClient.validateIdToken())

        // TODO validate chain (use TrustManager?)
        val certicateChain =
            Jwt.parseCertificateChain(
                validateIdTokenResponse["keys"]?.jsonArray?.first()?.jsonObject?.get(
                    "x5c"
                )?.jsonArray?.map { it.jsonPrimitive.content }!!
            )

        val certicate = certicateChain.first()

        val publicKey = certicate.publicKey as ECPublicKey

        if (!Jwt.verify(tokenResponse["id_token"]?.jsonPrimitive?.content.toString(), publicKey) ||
            !Jwt.verify(tokenResponse["access_token"]?.jsonPrimitive?.content.toString(), publicKey)
        ) {
            throw Exception("The token response could not be verified")
        }
        return Jwt.decode(tokenResponse["id_token"]?.jsonPrimitive?.content.toString()).payload
    }
}