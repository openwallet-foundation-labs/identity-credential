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

package com.ul.ims.gmdl.cbordata.security.mdlauthentication

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.ByteString
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream

class MacStructure private constructor(
    private val alg: ByteArray?,
    private val deviceAuthenticationData: ByteArray?
) : AbstractCborStructure() {
    companion object {
        const val LOG_TAG = "MacStructure"
        private const val MAC_STRUCTURE_CONTEXT = "MAC0"
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureArray = builder.addArray()

        structureArray = structureArray.add(MAC_STRUCTURE_CONTEXT)
        structureArray = structureArray.add(ByteString(alg))
        structureArray = structureArray.add(ByteString(byteArrayOf()))
        structureArray = structureArray.add(ByteString(deviceAuthenticationData))

        builder = structureArray.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    class Builder {
        private var alg : ByteArray? = null
        private var payload : ByteArray? = null

        fun decode() = apply {
        }

        fun setAlg(algBytes: ByteArray?) = apply {
            this.alg = algBytes
        }

        fun setPayload(payload : ByteArray?) = apply {
            this.payload = payload
        }

        fun build() : MacStructure {
            return MacStructure(alg, payload)
        }
    }
}
