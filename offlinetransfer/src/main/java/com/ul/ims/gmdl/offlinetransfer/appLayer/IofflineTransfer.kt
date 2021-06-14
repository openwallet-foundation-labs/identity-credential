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

package com.ul.ims.gmdl.offlinetransfer.appLayer

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.MutableLiveData
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.request.DataElements
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.Handover
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.utils.Resource

interface IofflineTransfer : ITransportEventListener {

    /**
     * Livedata which will update the transfer progress/status and wrap the data
     * **/
    var data : MutableLiveData<Resource<Any>>

    /**
     * Holder needs to set the datasource in order to lookup for the response data
     * **/
    fun setupHolder(credentialName : String,
                    deviceEngagement : ByteArray,
                    isAuthRequired : Boolean,
                    issuerAuthority: IIssuerAuthority,
                    handover: Handover
    )

    /**
     * Setup Verifier
     * **/
    fun setupVerifier(
        coseKey: CoseKey,
        requestItems: DataElements,
        deviceEngagement: DeviceEngagement,
        handover: Handover
    )

    /**
     * Close the transport channel
     * **/
    fun tearDown()


    /**
     * Callback to notify the HolderExecutor about the outcome of the consent dialog displayed
     * to the mDL Holder.
     * If we pass null as parameter it means that the user cancelled the consent dialog.
     * **/
    suspend fun onUserConsent(userConsentMap: Map<String, Boolean>?)

    /**
     * Crypto Object returned by Google IC API needed by the BiometricPrompt Library in order to
     * authenticate the user.
     * **/
    fun getCryptoObject() : BiometricPrompt.CryptoObject?
}