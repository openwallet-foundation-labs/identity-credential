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

package com.ul.ims.gmdl.bleofflinetransfer

const val MTU_UNKNOWN = -1
const val MAX_MTU = 250
const val DEFAULT_SCAN_PERIOD: Long = 7000
const val READY_FOR_TRANSMISSION: Byte = 0x01
const val TERMINATE_TRANSMISSION: Byte = 0x02
const val DATA_PENDING: Byte = 0x01.toByte()
const val END_OF_DATA: Byte = 0x00.toByte()
const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"