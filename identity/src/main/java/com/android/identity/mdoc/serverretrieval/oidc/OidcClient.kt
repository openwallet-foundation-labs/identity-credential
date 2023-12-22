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

import com.android.identity.mdoc.serverretrieval.ServerRetrievalUtil
import com.android.identity.mdoc.serverretrieval.transport.TransportLayer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * This class contains a client representation of the Open ID Connect Server
 * Retrieval as described in ISO-18013-5 par 8.3.3.2.2.
 *
 * The transport layer will be on HTTP level in a production environment, but can
 * be replaced with a mock transport layer in a test environment
 *
 * @param baseUrl the base URL of the Server
 * @param transportLayer the used transport layer
 */
class OidcClient(
    private val baseUrl: String,
    private val transportLayer: TransportLayer
) {
    private var openIdConfiguration: JsonObject? = null

    /**
     * Step 1 Configuration.
     *
     * @return a JSON string containing the OpenID Provider Configuration Information.
     */
    fun configuration(): String {
        val response = transportLayer.doGet("$baseUrl/.well-known/openid-configuration")
        openIdConfiguration = Json.Default.decodeFromString(response)
        return openIdConfiguration.toString()
    }

    /**
     * Step 2 Client Registration.
     *
     * @param registrationRequest a JSON string containing the Registration Request.
     * @return a JSON string containing the Registration Result.
     */
    fun clientRegistration(registrationRequest: String): String {
        return transportLayer.doPost(getUrl("registration_endpoint"), registrationRequest)
    }

    /**
     * Step 3 Authorization.
     *
     * @param authorizationRequest the parameters of the Authorization Request
     * (will be encoded as an URL query).
     * @return a JSON string containing the Authorization Response.
     */
    fun authorization(authorizationRequest: Map<String, String>): String {
        return transportLayer.doGet(
            "${getUrl("authorization_endpoint")}?${
                ServerRetrievalUtil.mapToUrlQuery(
                    authorizationRequest
                )
            }"
        )
    }

    /**
     * Step 4 Get ID Token.
     *
     * @param tokenRequest the parameters of the Token Request (will be encoded
     * as an URL query).
     * @return as JSON string containing the Token Response.
     */
    fun getIdToken(tokenRequest: Map<String, String>): String {
        return transportLayer.doPost(
            getUrl("token_endpoint"),
            ServerRetrievalUtil.mapToUrlQuery(tokenRequest)
        )
    }

    /**
     * Step 5 Validate ID Token.
     *
     * @return a JSON string containing the certificate chain of keys used for
     * the JSON Web Tokens.
     */
    fun validateIdToken(): String {
        return transportLayer.doGet(getUrl("jwks_uri"))
    }

    /**
     * Get an URL from the Open ID Configuration JSON Object
     */
    private fun getUrl(configKey: String): String {
        if (openIdConfiguration == null) {
            throw Exception("OIDC Server Retrieval Method 'configuration' should be called first")
        }
        return openIdConfiguration!![configKey]?.jsonPrimitive?.content.toString()
    }
}