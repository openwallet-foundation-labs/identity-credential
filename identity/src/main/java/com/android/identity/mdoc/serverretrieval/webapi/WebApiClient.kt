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

import com.android.identity.mdoc.serverretrieval.transport.TransportLayer



/**
 * This class contains a client representation of the WebAPI Server
 * Retrieval as described in ISO-18013-5 par 8.3.3.2.1.
 *
 * The transport layer will be on HTTP level in a production environment, but can
 * be replaced with a mock transport layer in a test environment
 *
 * @param baseUrl the base URL of the Server
 * @param transportLayer the used transport layer
 */
class WebApiClient(
    private val baseUrl: String,
    private val transportLayer: TransportLayer
) {
    /**
     * The WebAPI call to retrieve the mdoc document data.
     *
     * @param serverRequest a JSON string representing the Server Request
     * @return a JSON string containing the Server Response
     */
    fun serverRetrieval(serverRequest: String): String {
        return transportLayer.doPost("$baseUrl/identity", serverRequest)
    }
}