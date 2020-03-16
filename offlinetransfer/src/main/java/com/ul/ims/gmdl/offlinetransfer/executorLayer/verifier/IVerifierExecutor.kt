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

package com.ul.ims.gmdl.offlinetransfer.executorLayer.verifier

import androidx.lifecycle.MutableLiveData
import com.ul.ims.gmdl.cbordata.request.IRequest
import com.ul.ims.gmdl.cbordata.response.IResponse
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutor
import com.ul.ims.gmdl.offlinetransfer.utils.Resource

/**
 * Interface with the basic structure for a Verifier Executor. A Verifier should send a request for
 * data to the Holder and handle the response received.
 *
 * **/
interface IVerifierExecutor : IExecutor {

    /**
     * Livedata where the App is going to observe in order to receive updates about the Offline
     * transfer status and unwrap the data when the transfer is completed.
     * **/
    var data: MutableLiveData<Resource<Any>>?

    /**
     * Function that must return the initial Request that should be sent by the Reader to the
     * Holder. For now it's going to be hardcoded.
     * **/
    fun getInitialRequest() : IRequest

    /**
     * Function implemented by the VerifierExecutor, when a response is received.
     * Here we must verify if the response has a correct structure and delegate to the App
     * the display the data or any errors contained in the response.
     * **/
    fun onResponse(res : IResponse?)
}