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

package com.ul.ims.gmdl.cbordata.deviceEngagement.options

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.MajorType
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructureBuilder
import java.io.ByteArrayOutputStream

data class WebAPI private constructor(
    val version: Int,
    val baseUrl: String,
    val token: String
) : AbstractCborStructure() {

    companion object {
        const val DEFAULT_BASE_URL = ""
        const val LOG_TAG = "WebApi"
        const val HASH_CONST = 31
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()

        var builder = CborBuilder()
        var structureArray = builder.addArray()

        // version
        structureArray = structureArray.add(toDataItem(version))

        // baseUrl
        structureArray = structureArray.add(toDataItem(baseUrl))

        // token
        structureArray = structureArray.add(toDataItem(token))

        builder = structureArray.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        val otherWebApi = other as? WebAPI
        otherWebApi?.let {
            if (this.version == it.version &&
                this.baseUrl == it.baseUrl &&
                this.token == it.token
            ) {
                return true
            }
        }
        return false
    }

    override fun hashCode(): Int {
        var result = version
        result = HASH_CONST * result + baseUrl.hashCode()
        result = HASH_CONST * result + token.hashCode()
        return result
    }

    class Builder : AbstractCborStructureBuilder() {
        private var version = 0
        private var baseUrl = DEFAULT_BASE_URL
        private var token = String()

        fun getToken(): String {
            return token
        }

        fun setVersion(version: Int) = apply {
            this.version = version
        }

        fun setBaseUrl(baseUrl: String) = apply {
            this.baseUrl = baseUrl
        }

        fun setToken(token: String) = apply {
            this.token = token
        }

        fun fromArray(arr: Array?) = apply {
            arr?.let {
                val dataItems = arr.dataItems
                if (dataItems.size == 3) {
                    if (dataItems[0].majorType == MajorType.UNSIGNED_INTEGER) {
                        version = fromDataItem(dataItems[0]) as Int
                    }

                    if (dataItems[1].majorType == MajorType.UNICODE_STRING) {
                        baseUrl = fromDataItem(dataItems[1]) as String
                    }

                    if (dataItems[2].majorType == MajorType.UNICODE_STRING) {
                        token = fromDataItem(dataItems[2]) as String
                    }
                }
            }
        }

        fun build(): WebAPI {
            return WebAPI(
                version,
                baseUrl,
                token
            )
        }
    }
}