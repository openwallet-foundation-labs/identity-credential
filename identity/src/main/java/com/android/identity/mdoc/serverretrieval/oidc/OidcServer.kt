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

import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.mdoc.serverretrieval.Jwt
import com.android.identity.mdoc.serverretrieval.SampleDrivingLicense
import com.android.identity.mdoc.serverretrieval.ServerRetrievalUtil
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Timestamp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URL
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.util.Base64
import java.util.UUID

/**
 * This class contains the server logic for the Open ID Connect Server Retrieval
 * as described in ISO-18013-5 par 8.3.3.2.2.
 *
 *
 * @param baseUrl the base URL of the Server.
 * @param privateKey the key that will be used to sign JSON Web Tokens.
 * @param certificateChain the certificate chain of the public key used to
 * verify the JSON Web Tokens.
 * @param credentialTypeRepository the repository with metadata of the mdoc
 * documents.
 */
class OidcServer(
    private val baseUrl: String,
    private val privateKey: ECPrivateKey,
    private val certificateChain: List<X509Certificate>,
    private val credentialTypeRepository: CredentialTypeRepository
) {
    // for now: only support for driving licenses
    private val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"

    private val supportedElements =
        credentialTypeRepository.getMdocCredentialType(MDL_DOCTYPE)?.namespaces?.map { ns ->
            ns.value.dataElements.values.map { "${ns.key}:${it.attribute.identifier}" }
        }?.flatten()!!

    private val storageEngine: StorageEngine = EphemeralStorageEngine()

    /**
     * Step 1 Configuration.
     *
     * @return a JSON string containing the OpenID Provider Configuration Information.
     */
    fun configuration(): String {
        return buildJsonObject {
            put("issuer", baseUrl)
            put("jwks_uri", "$baseUrl/.well-known/jwks.json")
            put("authorization_endpoint", "$baseUrl/connect/authorize")
            put("token_endpoint", "$baseUrl/connect/token")
            put("userinfo_endpoint", "$baseUrl/connect/userinfo")
            put("end_session_endpoint", "$baseUrl/connect/end_session")
            put("revocation_endpoint", "$baseUrl/connect/revocation")
            put("introspection_endpoint", "$baseUrl/connect/introspec")
            put("device_authorization_endpoint", "$baseUrl/connect/deviceauthorization")
            put("registration_endpoint", "$baseUrl/connect/register")
            put("frontchannel_logout_supported", true)
            put("frontchannel_logout_session_supported", true)
            put("backchannel_logout_supported", true)
            put("backchannel_logout_session_supported", true)
            put("scopes_supported", buildJsonArray { supportedElements.forEach { add(it) } })
            put("claims_supported", buildJsonArray { supportedElements.forEach { add(it) } })
            put("grant_types_supported", buildJsonArray {
                add("authorization_code")
                add("client_credentials")
                add("refresh_token")
                add("implicit")
                add("urn:ietf:params:oauth:grant-type:device_code")
            })
            put("response_types_supported", buildJsonArray {
                add("code")
                add("token")
                add("id_token")
                add("id_token token")
                add("code id_token")
                add("code token")
                add("code id_token token")
            })
            put("response_modes_supported", buildJsonArray {
                add("form_post")
                add("query")
                add("fragment")
            })
            put("token_endpoint_auth_methods_supported", buildJsonArray {
                add("client_secret_basic")
                add("client_secret_post")
            })
            put("subject_types_supported", buildJsonArray { add("public") })
            put("id_token_signing_alg_values_supported", buildJsonArray { add("ES256") })
            put("code_challenge_methods_supported", buildJsonArray {
                add("plain")
                add("S256")
            })
        }.toString()
    }

    /**
     * Step 2 Client Registration.
     *
     * @param registrationRequest a JSON string containing the Registration Request.
     * @return a JSON string containing the Registration Result.
     */
    fun clientRegistration(registrationRequest: String): String {
        val registrationRequestJson = Json.Default.decodeFromString<JsonObject>(registrationRequest)
        val clientId = UUID.randomUUID().toString()
        val response = buildJsonObject {
            put("client_id", clientId)
            put("client_id_issued_at", Timestamp.now().toEpochMilli() / 1000)
            put("client_secret", UUID.randomUUID().toString())
            put("client_secret_expires_at", 0)
            put("grant_types", buildJsonArray { add("authorization_code") })
            put("client_name", UUID.randomUUID().toString())
            put("client_uri", null)
            put("logo_uri", null)
            put("redirect_uris", registrationRequestJson["redirect_uris"]!!)
            put("scope", registrationRequestJson["scope"]!!)
        }
        cacheData(clientId, registrationRequestJson["scope"]?.jsonPrimitive?.content.toString())
        return response.toString()
    }

    /**
     * Step 3 Authorization.
     *
     * @param authorizationRequest the parameters of the Authorization Request
     * (will be encoded as an URL query).
     * @return a JSON string containing the Authorization Response.
     */
    fun authorization(authorizationRequest: Map<String, String>): String {
        val authorizationId = UUID.randomUUID().toString()
        val clientId = authorizationRequest["client_id"]!!
        val loginHint = authorizationRequest["login_hint"]!!

        // TODO: how to verify this JWT? It is not specified which key should be used...
        val serverRetrievalToken =
            Jwt.decode(loginHint).payload["id"]?.jsonPrimitive?.content.toString()
        val cachedData = getCachedData(clientId) ?: throw Exception("Client was not registered")

        // TODO: should the scope in the registration request be the same as in the authorization request?
        // for now: update the cache with the scope in the authorization request
        cacheData(clientId, authorizationRequest["scope"]!!, authorizationId, serverRetrievalToken)

        val code = Jwt.encode(buildJsonObject {
            put("client_id", authorizationRequest["client_id"])
            put("redirect_uri", authorizationRequest["redirect_uri"])
            put("auth_id", authorizationId)
            put("iat", Timestamp.now().toEpochMilli() / 1000)
            put("exp", (Timestamp.now().toEpochMilli() / 1000) + 600)
            put("sub", authorizationId)
        }, privateKey)

        val headers = buildJsonObject {
            put("Cache-Control", buildJsonArray { add("no-cache") })
            put("Connection", buildJsonArray { add("Keep-Alive") })
            put("Accept", buildJsonArray { add("*/*") })
            put("Accept-Encoding", buildJsonArray { add("gzip, deflate") })
            put("Cookie", buildJsonArray { add("idsrv") }) // TODO
            put("Host", buildJsonArray { add(URL(baseUrl).host) })
            put("Referer", buildJsonArray {
                add(
                    baseUrl + "/connect/authorize/callback" + ServerRetrievalUtil.mapToUrlQuery(
                        mapOf(
                            "client_id" to clientId,
                            "scope" to authorizationRequest["scope"]!!,
                            "redirect_uri" to "com.company.isomdocreader://login", // TODO
                            "response_type" to "code",
                            "login_hint" to authorizationRequest["login_hint"]!!
                        )
                    )
                )
            })
            put("X-Original-Proto", buildJsonArray { add("http") })
            put("X-Original-For", buildJsonArray { add("127.0.0.1:58873") }) // TODO
        }

        return buildJsonObject {
            put("Query", buildJsonObject {
                put("code", code)
                put("scope", authorizationRequest["scope"])
            })
            put("Headers", headers)
        }.toString()
    }

    /**
     * Step 4 Get ID Token.
     *
     * @param tokenRequest the parameters of the Token Request (will be encoded
     * as an URL query).
     * @return as JSON string containing the Token Response.
     */
    fun getIdToken(tokenRequest: Map<String, String>): String {
        val cachedData = getCachedData(tokenRequest["client_id"]!!)
            ?: throw Exception("Client was not registered")
        val rawAuthorizationCode =
            tokenRequest["code"] ?: throw Exception("Client was not authorized")
        val authorizationCode = Jwt.decode(rawAuthorizationCode).payload
        val authorizationId = authorizationCode["auth_id"]?.jsonPrimitive?.content.toString()
        if (authorizationId != cachedData["authorizationId"]?.jsonPrimitive?.content.toString()) {
            throw Exception("Client was not authorized")
        }
        return buildJsonObject {
            put("id_token", Jwt.encode(buildJsonObject {
                put("iss", baseUrl)
                put("iat", Timestamp.now().toEpochMilli() / 1000)
                put("exp", (Timestamp.now().toEpochMilli() / 1000) + 300)
                put("aud", "$baseUrl/resources")
                put("sub", "1")
                put("doctype", MDL_DOCTYPE)
                getDataElementsPerNamespace(cachedData).forEach {
                    put(it.key, it.value)
                }
            }, privateKey))
            put("access_token", Jwt.encode(buildJsonObject {
                put("client_id", tokenRequest["client_id"])
                put("iss", baseUrl)
                put("iat", Timestamp.now().toEpochMilli() / 1000)
                put("exp", (Timestamp.now().toEpochMilli() / 1000) + 300)
                put("aud", "$baseUrl/resources")
                put("sub", "1")
            }, privateKey))
            put("expires_in", 300)
            put("token_type", "Bearer")
        }.toString()
    }

    /**
     * Step 5 Validate ID Token.
     *
     * @return a JSON string containing the certificate chain of keys used for
     * the JSON Web Tokens.
     */
    fun validateIdToken(): String {
        return buildJsonObject {
            put("keys", buildJsonArray {
                add(buildJsonObject {
                    put("kty", "EC")
                    put("use", "sig")
                    put("alg", "ES256")
                    put("x5c", buildJsonArray {
                        certificateChain.map {
                            String(
                                Base64.getEncoder().encode(it.encoded)
                            )
                        }.forEach { add(it) }
                    })
                })
            })
        }.toString()
    }

    /**
     * Get the values of the requested data elements
     *
     * @param cachedData session data of the client
     * @return a map of data elements with their values
     */
    private fun getDataElementsPerNamespace(cachedData: JsonObject): Map<String, JsonElement> {
        val result: MutableMap<String, JsonElement> = mutableMapOf()
        val nameSpacedData =
            getNameSpacedDataByToken(cachedData["serverRetrievalToken"]?.jsonPrimitive?.content.toString())
        val elements = cachedData["scope"]?.jsonPrimitive?.content?.split(" ")
            ?.filter { it.contains(":") }!!
        val mdocCredentialType = credentialTypeRepository.getMdocCredentialType(MDL_DOCTYPE)!!
        elements.forEach { element ->
            val namespaceAndElement = element.split(":")
            val jsonElement = ServerRetrievalUtil.getJsonElement(
                nameSpacedData,
                mdocCredentialType,
                namespaceAndElement[0],
                namespaceAndElement[1]
            )
            // Not specified what to do in case of missing elements
            if (jsonElement != null) {
                result[element] = jsonElement
            }
        }
        return result
    }

    /**
     * Get a saved document. For now a hardcoded driving license
     */
    private fun getNameSpacedDataByToken(token: String): NameSpacedData {
        // TODO: for now sample data
        return SampleDrivingLicense.data
    }

    /**
     * Cache data to connect the different steps of the Server Retrieval process
     */
    private fun cacheData(
        clientId: String,
        scope: String,
        authorizationId: String? = null,
        serverRetrievalToken: String? = null,
    ) {
        val data = buildJsonObject {
            put("clientId", clientId)
            put("scope", scope)
            put("authorizationId", authorizationId)
            put("serverRetrievalToken", serverRetrievalToken)
        }
        storageEngine.put(clientId, data.toString().toByteArray())
    }

    /**
     * Read the cached data by client ID
     */
    private fun getCachedData(clientId: String): JsonObject? {
        val bytes = storageEngine.get(clientId) ?: return null
        return Json.Default.decodeFromString(String(bytes))
    }

}