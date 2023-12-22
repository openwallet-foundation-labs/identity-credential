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

package com.android.identity.mdoc.serverretrieval.webapi

import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.mdoc.serverretrieval.Jwt
import com.android.identity.mdoc.serverretrieval.SampleDrivingLicense
import com.android.identity.mdoc.serverretrieval.ServerRetrievalUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey

/**
 * This class contains the server logic of the WebAPI Server
 * Retrieval as described in ISO-18013-5 par 8.3.3.2.1.
 *
 * @param privateKey the key that will be used to sign JSON Web Tokens.
 * @param certificateChain the certificate chain of the public key used to
 * verify the JSON Web Tokens.
 * @param credentialTypeRepository the repository with metadata of the mdoc
 * documents.
 */
class WebApiServer(
    private val privateKey: ECPrivateKey,
    private val certificateChain: List<X509Certificate>,
    private val credentialTypeRepository: CredentialTypeRepository
) {
    /**
     * The WebAPI call to retrieve the mdoc document data.
     *
     * @param serverRequest a JSON string representing the Server Request
     * @return a JSON string containing the Server Response
     */
    fun serverRetrieval(serverRequest: String): String {
        val serverRequestJson = Json.Default.decodeFromString<JsonObject>(serverRequest)
        val docRequests = serverRequestJson["docRequests"] as JsonArray

        val claimsSets = docRequests
            .filter { credentialTypeRepository.getMdocCredentialType(it.jsonObject["docType"]?.jsonPrimitive?.content.toString()) != null }
            .map {
                buildJsonObject {
                    put("doctype", it.jsonObject["docType"]?.jsonPrimitive?.content.toString())
                    val unknownElements = mutableMapOf<String, MutableList<String>>()
                    put(
                        "namespaces",
                        getDataElementsPerNamespace(
                            serverRequestJson["token"]?.jsonPrimitive?.content.toString(),
                            it.jsonObject,
                            unknownElements
                        )
                    )
                    if (unknownElements.isNotEmpty()) {
                        put("errors", buildJsonObject {
                            unknownElements.keys.forEach { ns ->
                                put(ns, buildJsonObject {
                                    unknownElements[ns]?.forEach { el ->
                                        put(el, 0) // Error code 0 - Data not returned
                                    }
                                })
                            }
                        })
                    }
                }
            }
        val unknownDocuments = docRequests
            .filter { credentialTypeRepository.getMdocCredentialType(it.jsonObject["docType"]?.jsonPrimitive?.content.toString()) == null }
            .map { it.jsonObject["docType"]?.jsonPrimitive?.content.toString() }
        return buildJsonObject {
            put("version", "1.0")
            put("documents", buildJsonArray {
                claimsSets.forEach {
                    add(Jwt.encode(it, privateKey, certificateChain))
                }
            })
            if (unknownDocuments.isNotEmpty()) {
                put("documentErrors", buildJsonArray {
                    unknownDocuments.forEach {
                        add(buildJsonObject {
                            put(it, 0) // Error code 0 - Data not returned
                        })
                    }
                })
            }
        }.toString()
    }

    /**
     * Get the values of the requested data elements
     */
    private fun getDataElementsPerNamespace(
        token: String,
        docRequest: JsonObject,
        unknownElements: MutableMap<String, MutableList<String>>
    ): JsonObject {
        val docType = docRequest["docType"]?.jsonPrimitive?.content.toString()
        val mdocCredentialType = credentialTypeRepository.getMdocCredentialType(docType)
            ?: throw Exception("Unknown doctype")
        val nameSpacedData = getNameSpacedDataByToken(token, docType)
        val namespaces = docRequest["nameSpaces"] as JsonObject
        return buildJsonObject {
            namespaces.keys.forEach { ns ->
                put(ns, buildJsonObject {
                    namespaces[ns]?.jsonObject?.keys?.forEach { el ->
                        val jsonElement = ServerRetrievalUtil.getJsonElement(
                            nameSpacedData,
                            mdocCredentialType,
                            ns,
                            el
                        )
                        if (jsonElement != null) {
                            put(el, jsonElement)
                        } else {
                            if (!unknownElements.containsKey(ns)) {
                                unknownElements[ns] = mutableListOf()
                            }
                            unknownElements[ns]?.add(el)
                        }
                    }
                })
            }
        }
    }

    /**
     * Get a saved document. For now a hardcoded driving license
     */
    private fun getNameSpacedDataByToken(token: String, docType: String): NameSpacedData {
        // TODO: for now sample data
        return SampleDrivingLicense.data
    }
}