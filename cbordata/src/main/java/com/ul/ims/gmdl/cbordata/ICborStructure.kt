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

package com.ul.ims.gmdl.cbordata

import co.nstant.`in`.cbor.model.DataItem
import java.io.Serializable

interface ICborStructure : Serializable {

    // Encode this obj into a cbor structure
    fun encode(): ByteArray

    // Helper function to display a cbor structure in HEX
    fun encodeToString(): String

    // Helper function used to generate formatted data for junit tests
    fun encodeToStringDebug(): String

    // Convert a kotlin type into a cbor specific type
    fun toDataItem(variable: Any): DataItem
}