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

class Oidc private constructor(
    val version: Int,
    val url: String,
    val token: String
) : AbstractCborStructure() {

    companion object {
        const val LOG_TAG = "Oidc"
        const val HASH_CONST = 31
    }

    override fun equals(other: Any?): Boolean {
        val otherOidc = other as? Oidc
        otherOidc?.let {
            if (this.version == it.version &&
                this.url == it.url &&
                this.token == it.token
            ) {
                return true
            }
        }
        return false
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureArray = builder.addArray()

        // version
        structureArray = structureArray.add(toDataItem(version))

        // baseUrl
        structureArray = structureArray.add(toDataItem(url))

        // token
        structureArray = structureArray.add(toDataItem(token))

        builder = structureArray.end()
        CborEncoder(outputStream).encode(builder.build())

        return outputStream.toByteArray()
    }

    override fun hashCode(): Int {
        var result = version
        result = HASH_CONST * result + url.hashCode()
        result = HASH_CONST * result + token.hashCode()
        return result
    }

    class Builder : AbstractCborStructureBuilder() {
        private var version = 0
        private var url = String()
        private var token = String()

        fun setVersion(version: Int) = apply {
            this.version = version
        }

        fun setUrl(baseUrl: String) = apply {
            this.url = baseUrl
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
                        url = fromDataItem(dataItems[1]) as String
                    }
                    if (dataItems[2].majorType == MajorType.UNICODE_STRING) {
                        token = fromDataItem(dataItems[2]) as String
                    }
                }
            }
        }

        fun build(): Oidc {
            return Oidc(
                version,
                url,
                token
            )
        }
    }
}