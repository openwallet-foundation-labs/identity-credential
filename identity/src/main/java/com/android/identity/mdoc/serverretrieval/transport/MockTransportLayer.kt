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

import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.serverretrieval.TestKeysAndCertificates
import com.android.identity.mdoc.serverretrieval.ServerRetrievalUtil
import com.android.identity.mdoc.serverretrieval.oidc.OidcServer
import com.android.identity.mdoc.serverretrieval.webapi.WebApiServer
import com.android.identity.util.Logger

/**
 * Class with an implementation of the [TransportLayer] for Server Retrieval
 * that connects the client directly to the server logic.
 */
class MockTransportLayer() : TransportLayer {

    private val credentialTypeRepository: CredentialTypeRepository
    private val oidcServer: OidcServer
    private val webApiServer: WebApiServer

    init {
        credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        oidcServer = OidcServer(
            "https://utopiadot.gov", TestKeysAndCertificates.jwtSignerPrivateKey,
            TestKeysAndCertificates.jwtCertificateChain,
            credentialTypeRepository
        )
        webApiServer = WebApiServer(
            TestKeysAndCertificates.jwtSignerPrivateKey,
            TestKeysAndCertificates.jwtCertificateChain,
            credentialTypeRepository
        )
    }

    private val TAG = "MockTransportLayer"

    /**
     * Do a GET request.
     *
     * @param url the requested URL
     * @return a string representing que response
     */
    override fun doGet(url: String): String {
        Logger.d(TAG, "Url: $url")
        val response = when {
            url.contains(".well-known/openid-configuration") -> oidcServer.configuration()
            url.contains("connect/authorize") -> oidcServer.authorization(
                ServerRetrievalUtil.urlToMap(
                    url
                )
            )

            url.contains(".well-known/jwks.json") -> oidcServer.validateIdToken()
            else -> throw Exception("BAD Request")
        }
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
    override fun doPost(url: String, requestBody: String): String {
        Logger.d(TAG, "Url: $url")
        Logger.d(TAG, "Request: $requestBody")
        val response = when {
            url.contains("/identity") -> webApiServer.serverRetrieval(requestBody)
            url.contains("/connect/register") -> oidcServer.clientRegistration(requestBody)
            url.contains("/connect/token") -> oidcServer.getIdToken(
                ServerRetrievalUtil.urlToMap(
                    requestBody
                )
            )

            else -> throw Exception("BAD Request")
        }
        Logger.d(TAG, "Response: $response")
        return response
    }
}