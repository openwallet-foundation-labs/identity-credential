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

package com.ul.ims.gmdl.cbordata.generic

import android.icu.util.GregorianCalendar
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.ICborStructure
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.DeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.drivingPrivileges.DrivingPrivilege
import com.ul.ims.gmdl.cbordata.drivingPrivileges.DrivingPrivileges
import com.ul.ims.gmdl.cbordata.response.*
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.CURVEID_LABEL
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.KEYTYPE_LABEL
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.PRIVATEKEY_LABEL
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.XCOORDINATE_LABEL
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.YCOORDINATE_LABEL
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.Handover
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.DataFormatException

abstract class AbstractCborStructure : ICborStructure {
    // Encode this obj into a cbor structure
    abstract override fun encode(): ByteArray

    // Helper function to display a cbor structure in HEX
    override fun encodeToString(): String {
        val encoded = encode()
        val sb = StringBuilder(encoded.size * 2)

        for (b in encoded) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // Helper function used to generate formatted data for junit tests
    override fun encodeToStringDebug(): String {
        val encoded = encode()
        val sb = StringBuilder(encoded.size * 2)

        val iterator = encoded.iterator().withIndex()
        var newLineCounter = 0
        iterator.forEach { b ->
            sb.append("0x")
            sb.append(String.format("%02x", b.value))
            sb.append(".toByte()")

            if (iterator.hasNext()) {
                newLineCounter++
                sb.append(", ")

                if (newLineCounter == 5) {
                    sb.append("\n")
                    newLineCounter = 0
                }
            }
        }

        return sb.toString()
    }

    // Convert a kotlin type into a cbor specific type
    override fun toDataItem(variable: Any): DataItem {
        return when (variable) {
            is Boolean -> encodeBoolean(variable)
            is Int -> encodeInteger(variable)
            is String -> UnicodeString(variable)
            is UUID -> UnicodeString(variable.toString())
            is Byte -> ByteString(byteArrayOf(variable))
            is ByteArray -> ByteString(variable)
            is DrivingPrivilege -> variable.toDataItem()
            is CoseKey -> encodeCoseKey(variable)
            is DeviceRetrievalMethod -> variable.toDataItem()
            is GregorianCalendar -> CborUtils.dateTimeToUnicodeString(variable)
            is DrivingPrivileges -> variable.toCborDataItem()
            is DeviceEngagement -> encodeDeviceEngagement(variable)
            is DeviceNameSpaces -> encodeDeviceNameSpaces(variable)
            is CoseSign1 -> encodeCoseSign1(variable)
            is CoseMac0 -> encodeCoseMac0(variable)
            is IssuerSignedItem -> variable.toDataItem()
            is Document -> variable.toDataItem()
            is IssuerSigned -> variable.toDataItem()
            is DeviceSigned -> variable.toDataItem()
            is DeviceAuth -> variable.toDataItem()
            is Handover -> variable.toDataItem()
            else -> throw DataFormatException("Data Type not supported $variable")
        }
    }

    private fun encodeBoolean(variable: Boolean): DataItem {
        return if (variable) {
            SimpleValue.TRUE
        } else {
            SimpleValue.FALSE
        }
    }

    private fun encodeInteger(variable: Int): DataItem {
        if (variable < 0) {
            return NegativeInteger(variable.toLong())
        }
        return UnsignedInteger(variable.toLong())
    }

    private fun encodeCoseMac0(variable: CoseMac0): DataItem {
        return variable.appendToNestedStructure()
    }

    private fun encodeCoseSign1(variable: CoseSign1): DataItem {
        return variable.addToNestedStructure()
    }

    private fun encodeDeviceNameSpaces(dn: DeviceNameSpaces): DataItem {
        // Empty map for now
        val map = Map()

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(map)

        return ByteString(outputStream.toByteArray())
    }

    private fun encodeDeviceEngagement(deviceEngagement: DeviceEngagement): DataItem {
        TODO("not implemented, to be implemented by Vini") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO: Move this to CoseKey1 class
    private fun encodeCoseKey(variable: CoseKey): DataItem {
        var map = Map()
        if (variable.keyType is Int || variable.keyType is String)
            map = map.put(KEYTYPE_LABEL, toDataItem(variable.keyType))
        variable.curve?.let {
            // curveId
            val cid = variable.curve.id as? Int
            cid?.let {
                //Curves from Section 13.1 of RFC 8152
                if (cid in 1..7) {
                    map = map.put(CURVEID_LABEL, toDataItem(cid))
                }
                //Curve identifiers from Table 20 ISO 18013-5
                if (cid in -65537 downTo -65540) {
                    map = map.put(CURVEID_LABEL, NegativeInteger(cid.toLong()))
                }
            }
            // xCoordinate
            val xco = variable.curve.xCoordinate
            if (xco != null)
                map = map.put(XCOORDINATE_LABEL, ByteString(xco))
            // yCoordinate
            val yco = variable.curve.yCoordinate
            if (yco != null)
                map = map.put(YCOORDINATE_LABEL, ByteString(yco))
            // privateKey
            val pkey = variable.curve.privateKey
            if (pkey != null) {
                map = map.put(PRIVATEKEY_LABEL, ByteString(pkey))
            }
        }
        return map
    }

    private fun toSimpleValueType(boolean: Boolean): SimpleValueType {
        if (boolean)
            return SimpleValueType.TRUE
        return if (!boolean)
            SimpleValueType.FALSE
        else SimpleValueType.NULL
    }
}