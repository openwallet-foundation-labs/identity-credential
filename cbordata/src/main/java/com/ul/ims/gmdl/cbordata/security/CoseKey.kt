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
import co.nstant.`in`.cbor.builder.ArrayBuilder
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.cryptoUtils.HashAlgorithms
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.utils.ECUtils.deriveUncompressedYcoordinateFromCurve
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.security.PublicKey

class CoseKey private constructor(val keyType: Any?, val curve: EC2Curve?) :
    AbstractCborStructure(), Serializable {

    companion object {
        const val LOG_TAG = "CoseKey"
        val KEYTYPE_LABEL = UnsignedInteger(1)
        val CURVEID_LABEL = NegativeInteger(-1)
        val XCOORDINATE_LABEL = NegativeInteger(-2)
        val YCOORDINATE_LABEL = NegativeInteger(-3)
        val PRIVATEKEY_LABEL = NegativeInteger(-4)
        val KEYMEMBER_EC2_KEYS = UnsignedInteger(2)

        //Curve names from Section 13.1 of RFC 8152
        val P256: UnsignedInteger = UnsignedInteger(1)
        val P384: UnsignedInteger = UnsignedInteger(2)
        val P521: UnsignedInteger = UnsignedInteger(3)
        val X25519: UnsignedInteger = UnsignedInteger(4)
        val X448: UnsignedInteger = UnsignedInteger(5)
        val Ed25519: UnsignedInteger = UnsignedInteger(6)
        val Ed448: UnsignedInteger = UnsignedInteger(7)

        val brainpoolP256r1: NegativeInteger = NegativeInteger(-65537)
        val brainpoolP320r1: NegativeInteger = NegativeInteger(-65538)
        val brainpoolP384r1: NegativeInteger = NegativeInteger(-65539)
        val brainpoolP512r1: NegativeInteger = NegativeInteger(-65540)
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        // keyType
        if (keyType is Int || keyType is String)
            structureMap = structureMap.put(KEYTYPE_LABEL, toDataItem(keyType))

        //EC2Curve
        curve?.let {c ->
            val cid = curve.id as? Int
            cid?.let {
                //Curves from Section 13.1 of RFC 8152
                if (cid in 1..7) {
                    structureMap = structureMap.put(CURVEID_LABEL, toDataItem(cid))
                }
                //Curve identifiers from Table 20 ISO 18013-5
                if (cid in -65537 downTo -65540) {
                    structureMap = structureMap.put(CURVEID_LABEL, NegativeInteger(cid.toLong()))
                }
            }
            // xCoordinate
            val xco = curve.xCoordinate
            if (xco != null)
                structureMap = structureMap.put(XCOORDINATE_LABEL, ByteString(xco))
            // yCoordinate
            val yco = curve.yCoordinate
            if (yco != null)
                structureMap = structureMap.put(YCOORDINATE_LABEL, ByteString(yco))
            // privateKey
            val pkey = curve.privateKey
            if (pkey != null) {
                structureMap = structureMap.put(PRIVATEKEY_LABEL, ByteString(pkey))
            }
        }
        builder = structureMap.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    fun encodeTagged(): ByteArray {
        val outputStream = ByteArrayOutputStream()

        val coseKeyByteString = ByteString(encode())
        coseKeyByteString.tag = Tag(24)

        CborEncoder(outputStream).encode(coseKeyByteString)
        return outputStream.toByteArray()
    }

    // The structure of pub key follows (for EC cuv): 0x04 + [32-byte X coordinate] + [32-byte Y coordinate]
    fun getPublicKey(): PublicKey {
        curve?.let { curv ->
            curv.xCoordinate?.let { cx ->
                curv.yCoordinate?.let { cy ->
                    val pubKeyBytes = CryptoUtils.toUncompressedPoint(cx, cy)
                    return CryptoUtils.decodeUncompressedPoint(pubKeyBytes)
                }
            }
        }

        throw RuntimeException("Empty PublicKey")
    }

    /**
     * Calculate the SHA-256 hash of a ByteArray
     *
     * @return bytes of a SHA-256 digest
     *
     * **/
    fun calculatePublickeyHash() : ByteArray? {
        curve?.let {curv ->
            curv.xCoordinate?.let {cx ->
                curv.yCoordinate?.let {cy ->
                    return CryptoUtils.generateHash(HashAlgorithms.SHA_256,
                        CryptoUtils.toUncompressedPoint(cx, cy))
                }
            }
        }

        return null
    }

    fun appendToStructure(builder : MapBuilder<ArrayBuilder<ArrayBuilder<CborBuilder>>>) :
            MapBuilder<ArrayBuilder<ArrayBuilder<CborBuilder>>> {

        var structureMap = builder

        // keyType
        if (keyType is Int || keyType is String)
            structureMap = structureMap.put(KEYTYPE_LABEL, toDataItem(keyType))

        //EC2Curve
        curve?.let {
            // curveId
            val cid = curve.id as? Int
            cid?.let {
                //Curves from Section 13.1 of RFC 8152
                if (cid in 1..7) {
                    structureMap = structureMap.put(CURVEID_LABEL, toDataItem(cid))
                }
                //Curve identifiers from Table 20 ISO 18013-5
                if (cid in -65537 downTo -65540) {
                    structureMap = structureMap.put(CURVEID_LABEL, NegativeInteger(cid.toLong()))
                }
            }
            // xCoordinate
            val xco = curve.xCoordinate
            if (xco != null)
                structureMap = structureMap.put(XCOORDINATE_LABEL, ByteString(xco))
            // yCoordinate
            val yco = curve.yCoordinate
            if (yco != null)
                structureMap = structureMap.put(YCOORDINATE_LABEL, ByteString(yco))
            // privateKey
            val pkey = curve.privateKey
            if (pkey != null) {
                structureMap = structureMap.put(PRIVATEKEY_LABEL, ByteString(pkey))
            }
        }

        return structureMap
    }

    class Builder {
        private var curve: EC2Curve? = null
        private var keyType: Any? = null

        fun decode(map: Map) = apply {
            if (map.keys.size == 1) {
                try {
                    val itemKeyType = map.get(KEYTYPE_LABEL)
                    if (itemKeyType.majorType == MajorType.UNSIGNED_INTEGER || itemKeyType.majorType == MajorType.UNICODE_STRING) {
                        decodeKeyType(itemKeyType)
                        curve = null
                    }
                } catch (ex: CborException) {
                    Log.e(LOG_TAG, ex.message, ex)
                }
            }
            if (map.keys.size > 2) {
                try {
                    val itemKeyType = map.get(KEYTYPE_LABEL)
                    if (itemKeyType.majorType == MajorType.UNSIGNED_INTEGER || itemKeyType.majorType == MajorType.UNICODE_STRING) {
                        decodeKeyType(itemKeyType)
                    }
                    if (curve !is EC2Curve) {
                        curve = null
                    }
                    decodeCurve(map)
                } catch (ex: CborException) {
                    Log.e(LOG_TAG, ex.message, ex)
                }
            }
        }

        fun decode(data: ByteArray) = apply {
            try {
                val bais = ByteArrayInputStream(data)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size == 1) {
                    val structureItems = decoded[0] as? Map
                    structureItems?.let {
                        decode(it)
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        private fun decodeCurve(map: Map) {
            val itemCurveId = map.get(CURVEID_LABEL)
            decodeCurveId(itemCurveId)
            val itemX = map.get(XCOORDINATE_LABEL)
            decodeXcoordinate(itemX)
            val itemY = map.get(YCOORDINATE_LABEL)
            decodeYcoordinate(itemY)
//            val point = ECPoint(curve?.id, curve?.xCoordinate, curve?.yCoordinate)
//            validatePoint()
        }

        private fun decodeYcoordinate(itemY: DataItem?) {
            when(itemY?.majorType) {
                MajorType.BYTE_STRING -> curve?.yCoordinate = (itemY as? ByteString)?.bytes
                MajorType.SPECIAL -> curve?.yCoordinate = decodeCompressedYcoordinate(itemY)
                else -> curve?.yCoordinate = null
            }
        }

        private fun decodeCompressedYcoordinate(itemY: DataItem): ByteArray? {
            (itemY as? SimpleValue)?.let {
                return when(it.simpleValueType) {
                    SimpleValueType.TRUE ->
                        deriveUncompressedYcoordinateFromCurve(curve, true)
                    SimpleValueType.FALSE ->
                        deriveUncompressedYcoordinateFromCurve(curve, false)
                    else -> null
                }
            }

            return null
        }

        private fun decodeXcoordinate(itemX: DataItem?) {
            when(itemX?.majorType) {
                MajorType.BYTE_STRING -> curve?.xCoordinate = (itemX as? ByteString)?.bytes
                else -> curve?.xCoordinate = null
            }
        }

        private fun decodeCurveId(itemCurveId: DataItem?) {
            when(itemCurveId?.majorType) {
                // TODO: Nava to check it
                MajorType.UNSIGNED_INTEGER -> {
                    val keyValue = (itemCurveId as UnsignedInteger).value.toInt()
                    curve?.id = keyValue
                }
                MajorType.NEGATIVE_INTEGER -> {
                    val keyValue = (itemCurveId as NegativeInteger).value.toInt()
                    curve?.id = keyValue
                }
                else -> curve?.id = null
            }
        }

        private fun decodeKeyType(keyType: DataItem) {
            val kTypeInt = keyType as? UnsignedInteger
            kTypeInt?.let {
                this.keyType = kTypeInt.value.toInt()
            }
            if (this.keyType == null) {
                val kTypeString = keyType as? UnicodeString
                kTypeString?.let {
                    this.keyType = kTypeString.string
                }
            }
            curve = when (keyType) {
                KEYMEMBER_EC2_KEYS -> EC2Curve()
                else -> null
            }
        }

        fun build() : CoseKey {
          return CoseKey(keyType, curve)
        }

        fun setKeyType(keyType: Int) = apply {
            this.keyType = keyType
        }

        fun setCurve(curveId: Int, xCoordinate: ByteArray?, yCoordinate: ByteArray?, privateKey: ByteArray?) = apply {
            val c = EC2Curve()
            c.id = curveId
            c.xCoordinate = xCoordinate
            c.yCoordinate = yCoordinate
            c.privateKey = privateKey
            this.curve = c
        }

        fun setKeyType(keyType: String) {
            this.keyType = keyType
        }
    }

    override fun equals(other: Any?): Boolean {
        other?.let {
            if (other is CoseKey) {
                if (other.keyType == keyType) {
                    if (other.curve == curve) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun hashCode(): Int {
        var result = keyType?.hashCode() ?: 0
        result = 31 * result + (curve?.hashCode() ?: 0)
        return result
    }

}