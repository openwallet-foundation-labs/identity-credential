/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.issuerauthority

import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import java.security.PublicKey

/**
 * This interface will have the functions in order to mock a real Issuer Authority API
 * **/
interface IIssuerAuthority {

    /**
     * Return the User Credential
     * **/
    fun getCredentials() : UserCredential?

    /**
     * Return the Provision Challenge Requested by Google Identity Credential API
     * **/
    fun getProvisionChallenge() : ByteArray

    /**
     * Returns a signed Cose_Sign1 Structure
     * **/
    suspend fun getIssuerSignedData(publicKey: PublicKey): ByteArray?

    /**
     *
     * **/
    suspend fun getIssuerNamespaces(issuerAuth: ByteArray): IssuerNameSpaces?
}