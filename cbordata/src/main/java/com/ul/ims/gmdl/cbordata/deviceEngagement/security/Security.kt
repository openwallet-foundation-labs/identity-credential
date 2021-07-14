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

package com.ul.ims.gmdl.cbordata.deviceEngagement.security

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.builder.ArrayBuilder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import com.ul.ims.gmdl.cbordata.security.CoseKey
import java.io.Serializable

class Security private constructor(
    val coseKey: CoseKey?,
    val cipherIdent: Int?
) : Serializable {
    fun appendToCborStructure(builder: ArrayBuilder<CborBuilder>): ArrayBuilder<CborBuilder> {
        var securityArr = builder.addArray()

        // Add cipherIdent to the structure
        cipherIdent?.let {
            securityArr = securityArr.add(cipherIdent.toLong())
        }

        // Add coseKey to the structure
        coseKey?.let {
            val coseKeyByteString = ByteString(coseKey.encode())
            coseKeyByteString.tag = Tag(24)
            securityArr = securityArr.add(coseKeyByteString)
        }

        return securityArr.end()
    }

    fun encode(): DataItem {
        var securityArr = CborBuilder().addArray()

        // Add cipherIdent to the structure
        cipherIdent?.let {
            securityArr = securityArr.add(cipherIdent.toLong())
        }

        // Add coseKey to the structure
        coseKey?.let {
            val coseKeyByteString = ByteString(coseKey.encode())
            coseKeyByteString.tag = Tag(24)
            securityArr = securityArr.add(coseKeyByteString)
        }

        return securityArr.end().build()[0]
    }

    fun isValid(): Boolean {
        coseKey?.let { ck ->
            cipherIdent?.let { ci ->
                if (ck.curve?.id != null && ci > 0) {
                    return true
                }
            }
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Security) {
            this.coseKey == other.coseKey && this.cipherIdent == other.cipherIdent
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        var result = coseKey?.hashCode() ?: 0
        result = 31 * result + (cipherIdent ?: 0)
        return result
    }

    class Builder {
        private var cipherSuiteIdent: Int? = null
        private var coseKey: CoseKey? = null

        fun setCipherSuiteIdent(cipherSuiteIdent: Int) = apply {
            this.cipherSuiteIdent = cipherSuiteIdent
        }

        fun setCoseKey(coseKey: CoseKey) = apply {
            this.coseKey = coseKey
        }

        fun fromCborStructure(securityArr: Array) = apply {
            val securityDataItems = securityArr.dataItems
            if (securityDataItems.size == 2) {

                val chipherIdent = securityDataItems?.get(0) as? UnsignedInteger
                chipherIdent?.let {
                    cipherSuiteIdent = it.value.toInt()
                }

                val cKeyByteString = securityDataItems?.get(1) as? ByteString
                cKeyByteString?.let { cByteString ->

                    coseKey = CoseKey.Builder()
                        .decode(cByteString.bytes)
                        .build()
                }
            }
        }

        fun build(): Security {
            return Security(
                coseKey,
                cipherSuiteIdent
            )
        }
    }
}
