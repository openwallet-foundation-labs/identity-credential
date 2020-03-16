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

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.cryptoUtils.HashAlgorithms
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructureBuilder
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.*

class CoseSign1 private constructor(val alg: ASN1ObjectIdentifier?,
                                    val dsCertificateBytes: ByteArray?,
                                    val payloadData: ByteArray?,
                                    val signature: ByteArray?,
                                    val toBeSignedSigStructure: ByteArray?
) : AbstractCborStructure() {

    override fun encode(): ByteArray {
        // protected header
        // map key 1 : -7
        var protectedHeaderBuilder = CborBuilder()
        val protectedHeaderMap = protectedHeaderBuilder.addMap()
        protectedHeaderMap.put(UnsignedInteger(1.toLong()), NegativeInteger((-7).toLong()))
        protectedHeaderBuilder = protectedHeaderMap.end()
        val headerOutputStream = ByteArrayOutputStream()
        CborEncoder(headerOutputStream).encode(protectedHeaderBuilder.build())
        val protectedHeaderBytes = headerOutputStream.toByteArray()

        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()

        var structureArr = builder.addArray()

        // protected header
        structureArr.add(ByteString(protectedHeaderBytes))

        // unprotected header
        // map key 33 : ByteString
        val unprotectedHeaderMap = structureArr.addMap()
        if (dsCertificateBytes != null) {
            unprotectedHeaderMap.put(UnsignedInteger(33.toLong()), ByteString(dsCertificateBytes))
        }
        structureArr = unprotectedHeaderMap.end()
        // payload
        structureArr.add(ByteString(payloadData))

        // signature
        structureArr.add(ByteString(signature))

        builder = structureArr.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    fun addToNestedStructure() : Array {

        val arr = Array()

        // protected header
        var protectedHeaderBuilder = CborBuilder()
        val protectedHeaderMap = protectedHeaderBuilder.addMap()
        protectedHeaderMap.put(UnsignedInteger(1.toLong()), NegativeInteger((-7).toLong()))
        protectedHeaderBuilder = protectedHeaderMap.end()
        val headerOutputStream = ByteArrayOutputStream()
        CborEncoder(headerOutputStream).encode(protectedHeaderBuilder.build())
        val protectedHeaderBytes = headerOutputStream.toByteArray()

        arr.add(ByteString(protectedHeaderBytes))

        // unprotected header
        val unprotectedHeaderMap = Map()
        if (dsCertificateBytes != null) {
            unprotectedHeaderMap.put(UnsignedInteger(33.toLong()), ByteString(dsCertificateBytes))
        }
        arr.add(unprotectedHeaderMap)

        // payload
        arr.add(ByteString(payloadData))

        // signature
        arr.add(ByteString(signature))

        return arr
    }

    companion object {
        val DS_CERT_LABEL: UnsignedInteger = UnsignedInteger(33)
        val coseSign1AlgorithmMap = hashMapOf(
            NegativeInteger(-7) to X9ObjectIdentifiers.ecdsa_with_SHA256,
            NegativeInteger(-35) to X9ObjectIdentifiers.ecdsa_with_SHA384,
            NegativeInteger(-36) to X9ObjectIdentifiers.ecdsa_with_SHA512
        )
        const val PADDING_BYTE = 0x00.toByte()
        const val BITSTRING_TAG = 0x02.toByte()
        const val SEQUENCE_TAG = 0x30.toByte()
        const val LOG_TAG = "CoseSign1"
    }

    class Builder : AbstractCborStructureBuilder() {
        private var toBeSignedSigStructure: ByteArray? = null
        private var algo: ASN1ObjectIdentifier? = null
        private var dsCertificateBytes: ByteArray? = null
        private var payloadData: ByteArray? = null
        private var signature: ByteArray? = null
        private var algorithm : ByteArray? = null

        fun setPayload(payloadData: ByteArray) = apply {
            this.payloadData = payloadData
        }

        fun decode(arr : Array) = apply {
            if (arr.dataItems?.size == 4) {
                var dataItem = arr.dataItems[0]
                if (dataItem.majorType == MajorType.BYTE_STRING) {
                    val value = dataItem as ByteString
                    algorithm = value.bytes
                    decodeAlg(value)
                }

                dataItem = arr.dataItems[1]
                if (dataItem.majorType == MajorType.MAP) {
                    decodeDsCert(arr.dataItems[1] as Map)
                }

                dataItem = arr.dataItems[2]
                if (dataItem.majorType == MajorType.BYTE_STRING) {
                    val value = dataItem as ByteString
                    payloadData = value.bytes
                }

                dataItem = arr.dataItems[3]
                if (dataItem.majorType == MajorType.BYTE_STRING) {
                    val value = dataItem as ByteString
                    signature = value.bytes
                }

                createSigStructure(algo, payloadData)
            }
        }

        fun decode(coseSign1Data: ByteArray) = apply {
            try {
                val stream = ByteArrayInputStream(coseSign1Data)
                val dataItems = CborDecoder(stream).decode()
                if (dataItems.size > 0 ) {
                    val structureItems: Array? = dataItems[0] as? Array
                    structureItems?.let {
                        val dItems = structureItems.dataItems
                        dItems?.let {
                            if (dItems.size == 4) {
                                val algorithm = dItems[0] as? ByteString
                                algorithm?.let {
                                    decodeAlg(algorithm)
                                }

                                val dsCert = dItems[1] as? Map
                                dsCert?.let {
                                    decodeDsCert(dsCert)
                                }

                                val payload = dItems[2] as? ByteString
                                payloadData = payload?.bytes

                                val mSignature = dItems[3] as? ByteString
                                signature = mSignature?.bytes

                                createSigStructure(algo, payloadData)
                            }
                        }
                    }
                }
            } catch (ex: CborException) {
               Log.e(LOG_TAG, ex.message, ex)
            }
        }

        private fun createDerSignature(signature: ByteArray?): ByteArray? {
            if (signature == null || signature.size % 2 != 0) {
                return null
            }
            var r = Arrays.copyOfRange(signature, 0, signature.size/2)
            if (BigInteger.valueOf(r[0].toLong()).testBit(7)) {
                r = byteArrayOf(PADDING_BYTE) + r
            }
            val tagLengthForR = byteArrayOf(BITSTRING_TAG, r.size.toByte())
            r = tagLengthForR + r
            var s = Arrays.copyOfRange(signature, signature.size/2, signature.size)
            if (BigInteger.valueOf(s[0].toLong()).testBit(7)) {
                s = byteArrayOf(PADDING_BYTE) + s
            }
            val tagLengthForS = byteArrayOf(BITSTRING_TAG, s.size.toByte())
            s = tagLengthForS + s
            val rPlusS = r + s
            val tagLengthForSignature = byteArrayOf(SEQUENCE_TAG, rPlusS.size.toByte())
            return tagLengthForSignature + rPlusS
        }


        private fun createSigStructure(algo: ASN1ObjectIdentifier?, msoData: ByteArray?) {
            val sigStructureBuilder = SigStructure.Builder()
            sigStructureBuilder.setAlg(algo)
            sigStructureBuilder.setPayloadData(msoData)

            toBeSignedSigStructure = sigStructureBuilder.build().encode()
        }

        private fun decodeDsCert(dsCert: Map) {
            this.dsCertificateBytes = (dsCert.get(DS_CERT_LABEL) as? ByteString)?.bytes
        }

        private fun decodeAlg(algorithm: ByteString) {
            try {
                val algItems = CborDecoder.decode(algorithm.bytes)
                algItems?.let {
                    val algMap = asMap(algItems,0)
                    this.algo = coseSign1AlgorithmMap[(algMap.get(UnsignedInteger(1)))]
                }
            } catch (ex: CborException) {
                print(ex)
            }
        }

        fun setDsCertificateBytes(dsCertificateBytes: ByteArray) = apply {
            this.dsCertificateBytes = dsCertificateBytes
        }

        fun setAlgo(algo: HashAlgorithms) = apply {
            when (algo) {
                HashAlgorithms.SHA_256 -> {
                    this.algo = X9ObjectIdentifiers.ecdsa_with_SHA256
                }

                HashAlgorithms.SHA_384 -> {
                    this.algo = X9ObjectIdentifiers.ecdsa_with_SHA384
                }

                HashAlgorithms.SHA_512 -> {
                    this.algo = X9ObjectIdentifiers.ecdsa_with_SHA512
                }
            }
        }

        fun setToBeSignedSigStructure(toBeSignedSigStructure : ByteArray) = apply {
            this.toBeSignedSigStructure = toBeSignedSigStructure
        }

        fun setSignature(signature : ByteArray) = apply {
            this.signature = signature
        }

        fun build() : CoseSign1 {
            return CoseSign1(
                algo,
                dsCertificateBytes,
                payloadData,
                signature,
                toBeSignedSigStructure)
        }
    }
}