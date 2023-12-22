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

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Class with the process of the WebAPI Server Retrieval as described
 * in ISO-18013-5 par 8.3.3.2.1 from a client perspective.
 *
 * @param webApiClient the client to communicate with the server
 *
 */
class WebApiServerRetrievalProcess(
    private val webApiClient: WebApiClient
) {
    /**
     * The actual process of the WebAPI Server Retrieval.
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
        val serverRequest = buildJsonObject {
            put("version", "1.0")
            put("token", serverRetrievalToken)
            put("docRequests", buildJsonArray {
                add(buildJsonObject {
                    put("docType", docType)
                    put("nameSpaces", buildJsonObject {
                        documentRequest.keys.forEach { ns ->
                            put(ns, buildJsonObject {
                                documentRequest[ns]?.forEach { el ->
                                    put(el.key, el.value)
                                }
                            })
                        }
                    })
                })
            })
        }

        return Json.Default.decodeFromString(webApiClient.serverRetrieval(serverRequest.toString()))
    }
}