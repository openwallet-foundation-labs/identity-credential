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

package com.ul.ims.gmdl.offlinetransfer.transportLayer

enum class EventType(val description : String) {
    SETUP("Setup"),
    GATT_CONNECTED("Gatt Connected"),
    GATT_DISCONNECTED("Gatt Disconnected"),
    SERVICE_CONNECTED("Service Connected"),
    VERIFIER_VERIFIED("Verifier Verified"),
    RECEIVED("Received"),
    SCAN_STARTED("Scan Started"),
    SCAN_STOPPED("Scan Stopped"),
    CAN_CONNECT("Can Connect"),
    NO_DEVICE_FOUND("No device found"),
    GATT_SERVICE_STARTED("Gatt Service started"),
    GATT_SHUTDOWN("Gatt Shutdown"),
    STATE_READY_FOR_TRANSMISSION("Ready for transmission"),
    TRANSFER_IN_PROGRESS("Transfer in progress"),
    TRANSFER_COMPLETE("Transfer Complete"),
    STATE_TERMINATE_TRANSMISSION("Terminate Transmission"),
    ERROR("Error"),
    BT_OFF("BT is Off")
}