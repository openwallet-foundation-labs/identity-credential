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

package com.ul.ims.gmdl.offlinetransfer.executorLayer.holder

import androidx.biometric.BiometricPrompt
import com.ul.ims.gmdl.cbordata.doctype.IDoctype
import com.ul.ims.gmdl.cbordata.request.IRequest
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutor

/**
 * Interface with the basic structure for a Holder Executor. A holder should only receive a request
 * and send a response.
 *
 * **/
interface IHolderExecutor : IExecutor {

    /**
     * Supported doctype by the mDL Holder.
     * **/
    val supportedDoctype : IDoctype

    /**
     * Function implemented by the HolderExecutor, in order to process a request send by the
     * VerifierExecutor
     * Must return a Response upon all requests.
     * **/
    fun onRequest(req : IRequest?)

    /**
     * Function responsible to handle what to do when the mDL is unable to decrypt the request msg
     * **/
    fun onDecryptError()

    /**
     * Upon a received request mDL will ask the user consent to share the requested items in
     * the request
     * **/
    fun askForUserConsent(requestItems : List<String>)


    /**
     * User's consent response containing the requested items and if the user consented to share
     * them or not.
     * **/
    suspend fun onUserConsent(userConsentMap: Map<String, Boolean>?)

    /**
     * Crypto Object returned by Google IC API needed by the BiometricPrompt Library in order to
     * authenticate the user.
     * **/
    fun getCryptoObject(): BiometricPrompt.CryptoObject?
}