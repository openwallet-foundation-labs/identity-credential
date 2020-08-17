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

package com.ul.ims.gmdl.nfcofflinetransfer.model

import java.io.ByteArrayOutputStream


class ApduResponse private constructor(
    val dataField: ByteArray?,
    val sw1sw2: ByteArray
) {

    fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        dataField?.let {
            outputStream.write(it)
        }
        outputStream.write(sw1sw2)

        return outputStream.toByteArray()
    }

    fun getSw2() = sw1sw2[1]

    class Builder {
        private var dataField: ByteArray? = null
        private var sw1sw2: ByteArray = byteArrayOf(0x00, 0x00)

        fun decode(response: ByteArray) = apply {
            if (response.size > 2) {
                dataField = response.copyOfRange(0, response.size - 2)
            }
            sw1sw2 = response.copyOfRange(response.size - 2, response.size)
        }

        fun setResponse(dataField: ByteArray?, sw1sw2: ByteArray) = apply {
            this.dataField = dataField
            this.sw1sw2 = sw1sw2
        }

        fun build(): ApduResponse {
            return ApduResponse(
                dataField,
                sw1sw2
            )
        }
    }
}