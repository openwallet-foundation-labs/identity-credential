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

class DataField(private var dataField: List<Byte>) {
    fun getNextChunk(chunkSize: Int): ByteArray {
        if (!hasMoreBytes()) return byteArrayOf()

        val size = if (chunkSize > dataField.size) dataField.size else chunkSize
        val dataChunk = dataField.subList(0, size)
        dataField = dataField.subList(size, dataField.size)

        return dataChunk.toByteArray()
    }

    fun hasMoreBytes() = dataField.isNotEmpty()

    fun size() = dataField.size
}