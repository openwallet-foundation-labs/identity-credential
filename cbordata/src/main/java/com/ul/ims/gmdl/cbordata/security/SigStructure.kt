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

package com.ul.ims.gmdl.cbordata.security

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class SigStructure private constructor(val alg: ASN1ObjectIdentifier?, val payloadData: ByteArray?)
    : AbstractCborStructure(), Serializable {

    companion object {
        val SIG_STRUCTURE_CONTEXT: UnicodeString = UnicodeString("Signature1")
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureArray = builder.addArray()

        structureArray = structureArray.add(SIG_STRUCTURE_CONTEXT)
        structureArray = structureArray.add(ByteString(encodeAlgMap(alg)))
        structureArray = structureArray.add(ByteString(byteArrayOf()))
        structureArray = structureArray.add(ByteString(payloadData))

        builder = structureArray.end()

        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    private fun encodeAlgMap(alg: ASN1ObjectIdentifier?): ByteArray? {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        val mapBuilder = builder.addMap()
        val keys = CoseSign1.coseSign1AlgorithmMap.filterValues { it == alg }.keys
        if (keys.size != 1) {
            throw CborException("Invalid algorithm")
        }
        mapBuilder.put(UnsignedInteger(1), keys.first())
        builder = mapBuilder.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    class Builder {
        private var alg: ASN1ObjectIdentifier? = null
        private var payloadData: ByteArray? = null

        fun decode(data: ByteArray?) = apply {
            try {
                val stream = ByteArrayInputStream(data)
                val dataItems = CborDecoder(stream).decode()
                if (dataItems.size > 0 ) {
                    val structureItems: co.nstant.`in`.cbor.model.Array? = dataItems[0] as? co.nstant.`in`.cbor.model.Array
                    structureItems?.let {
                        if (structureItems.dataItems.size == 4) {
                            validateContext(structureItems.dataItems[0])
                            decodeAlg(structureItems.dataItems[1])
                            validateExternalAad(structureItems.dataItems[2])
                            decodePayloadData(structureItems.dataItems[3])
                        }
                    }
                }
            } catch (ex: CborException) {
                print("\n SigStructure decode exception: $ex")
            }
        }

        private fun decodePayloadData(dataItem: DataItem?) {
            val pData = dataItem as? ByteString
            pData?.let {
                payloadData = pData.bytes
            }
        }

        private fun validateExternalAad(dataItem: DataItem?) {
            val externalAad = dataItem as? ByteString
            externalAad?.let {
                val externalAadBytes = externalAad.bytes ?: throw CborException("external_aad must be a ByteString of a zero size byte array")
                if (!externalAadBytes.contentEquals(byteArrayOf())) {
                    print(ByteUtils.toHexString(externalAad.bytes))
                    throw CborException("external_aad must be a ByteString of a zero size byte array")
                }
            }
        }

        private fun decodeAlg(dataItem: DataItem?) {
            val a = dataItem as? ByteString
            a?.let {
                try {
                    val stream = ByteArrayInputStream(a.bytes)
                    val decode = CborDecoder(stream).decode()
                    if (decode.size != 1) {
                        throw CborException("Invalid algorithm")
                    }
                    val algMap = decode[0] as? Map
                    algMap?.let {
                        alg = CoseSign1.coseSign1AlgorithmMap[algMap.get(UnsignedInteger(1))]
                    }
                } catch (ex: CborException) {
                    throw CborException(ex)
                }
            }
        }

        private fun validateContext(dataItem: DataItem?) {
            val context = dataItem as? UnicodeString
            context?.let {
                if (context != SIG_STRUCTURE_CONTEXT) {
                    throw CborException("Invalid SigStructure context")
                }
            }
        }

        fun setAlg(alg: ASN1ObjectIdentifier?) = apply {
            this.alg = alg
        }

        fun setAlg(alg : ByteArray) = apply {
            this.alg = ASN1ObjectIdentifier.getInstance(alg)
        }

        fun setPayloadData(pData: ByteArray?) = apply {
            this.payloadData = pData
        }

        fun build() : SigStructure {
            return SigStructure(alg, payloadData)
        }
    }
}