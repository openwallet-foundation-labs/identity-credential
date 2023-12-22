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

package com.android.identity.mdoc.serverretrieval.transport

import com.android.identity.util.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Class with an implementation of the [TransportLayer] over HTTP
 */
class HttpTransportLayer: TransportLayer {
    private  val TAG = "HttpTransportLayer"

    /**
     * Do a GET request.
     *
     * @param url the requested URL
     * @return a string representing que response
     */
    override fun doGet(url: String): String {
        Logger.d(TAG, "Open ID Connect call. Url: $url")
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        Logger.d(TAG, "Response: $response")
        return response
    }

    /**
     * Do a POST request.
     *
     * @param url the requested URL
     * @param requestBody the request message
     * @return a string representing que response
     */
    override fun doPost(url: String, requestBody: String): String{
        Logger.d(TAG, "Open ID Connect call. Url: $url")
        Logger.d(TAG, "Request Body: $requestBody")
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        Logger.d(TAG, "Response: $response")
        return response
    }
}