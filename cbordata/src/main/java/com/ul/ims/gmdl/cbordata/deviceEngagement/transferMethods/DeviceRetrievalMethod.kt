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

package com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Array
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream

abstract class DeviceRetrievalMethod : AbstractCborStructure() {
    abstract val type: Int
    abstract val version: Int
    abstract val retrievalOptions: RetrievalOptions?

    override fun encode(): ByteArray {
        val array = toDataItem()

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(array)

        return outputStream.toByteArray()
    }

    fun toDataItem(): Array {
        var array = Array()

        array = array.add(toDataItem(type))
        array = array.add(toDataItem(version))
        retrievalOptions?.let {
            array = array.add(it.toDataItem())
        }

        return array
    }

    abstract override fun equals(other: Any?): Boolean
}