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

package com.ul.ims.gmdl.bleofflinetransfer.config

import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import java.util.*

class BleTransportConfigurations(private val bleServiceMode: BleServiceMode, serviceUuid: UUID) {

    companion object {
        const val MDL_PSM_STATE = "00000001-A123-48CE-896B-4C76973373E6"
        const val MDL_PSM_CLIENT_2_SERVER = "00000002-A123-48CE-896B-4C76973373E6"
        const val MDL_PSM_SERVER_2_CLIENT = "00000003-A123-48CE-896B-4C76973373E6"
        const val MDL_PSM_IDENT = "00000004-A123-48CE-896B-4C76973373E6"
        const val MDL_PSM_SERVICE = "6fa90bce-a8ef-48b0-b6b3-842b6e80f317"

        const val MDL_CCM_STATE = "0000005-A123-48CE-896B-4C76973373E6"
        const val MDL_CCM_CLIENT_2_SERVER = "00000006-A123-48CE-896B-4C76973373E6"
        const val MDL_CCM_SERVER_2_CLIENT = "00000007-A123-48CE-896B-4C76973373E6"
        const val MDL_CCM_IDENT = "00000008-A123-48CE-896B-4C76973373E6"
        const val MDL_CCM_SERVICE = "5c8256b5-225f-45e6-a102-f9307a4d30c4"
    }

    private val mdlPeripheralServerMode =
        ServiceCharacteristics(
            UUID.fromString(MDL_PSM_SERVICE),
            UUID.fromString(MDL_PSM_STATE),
            UUID.fromString(MDL_PSM_CLIENT_2_SERVER),
            UUID.fromString(MDL_PSM_SERVER_2_CLIENT),
            UUID.fromString(MDL_PSM_IDENT)
        )

    private val mdlCentralClientMode =
        ServiceCharacteristics(
            serviceUuid,
            UUID.fromString(MDL_CCM_STATE),
            UUID.fromString(MDL_CCM_CLIENT_2_SERVER),
            UUID.fromString(MDL_CCM_SERVER_2_CLIENT),
            UUID.fromString(MDL_CCM_IDENT)
        )

    fun getBleServiceCharacteristics() : ServiceCharacteristics {
        return when(bleServiceMode) {
            BleServiceMode.PERIPHERAL_SERVER_MODE -> mdlPeripheralServerMode
            BleServiceMode.CENTRAL_CLIENT_MODE -> mdlCentralClientMode
            BleServiceMode.UNKNOWN -> throw RuntimeException("Unknown BLE service mode")
        }
    }
}