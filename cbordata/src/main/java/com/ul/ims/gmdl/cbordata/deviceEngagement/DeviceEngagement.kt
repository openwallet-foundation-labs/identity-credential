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

package com.ul.ims.gmdl.cbordata.deviceEngagement

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.deviceEngagement.options.Oidc
import com.ul.ims.gmdl.cbordata.deviceEngagement.options.WebAPI
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.CipherSuiteIdentifiers
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.CurveIdentifiers
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleDeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.DeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.NfcDeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.WiFiAwareDeviceRetrievalMethod
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.utils.Base64Utils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class DeviceEngagement private constructor(
    val version: String?,
    val security: Security?,
    val deviceRetrievalMethod: List<DeviceRetrievalMethod>?,
    val options: kotlin.collections.Map<String, Any>?,
    val proprietary: kotlin.collections.Map<String, String>?,
    private val decodedFrom: ByteArray?
) : AbstractCborStructure(), Serializable {

    companion object {
        const val TRANSFER_TYPE_NFC = 1
        const val TRANSFER_TYPE_BLE = 2
        const val TRANSFER_TYPE_WIFI_AWARE = 3
        const val OPTIONS_WEBAPI_KEY = "WebAPI"
        const val OPTIONS_OIDC_KEY = "OIDC"
        const val OPTIONS_COMPACT_KEY = "compact"
        const val ERROR_VALUE = "error"
        val LOG_TAG: String = DeviceEngagement::class.java.simpleName
    }

    /**
     * Validate if the Cbor Obj contains the mandatory fields.
     * **/
    fun isValid(): Boolean {
        version?.let { v ->
            if (v.isNotEmpty()) {
                security?.let { s ->
                    if (s.isValid()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Return the BLE Transfer Method if present or null otherwise.
     * */
    fun getBLETransferMethod(): BleDeviceRetrievalMethod? {
        var bleTransferMethod: BleDeviceRetrievalMethod? = null
        deviceRetrievalMethod?.let {
            for (i in deviceRetrievalMethod) {
                val bTMethod = i as? BleDeviceRetrievalMethod
                if (bTMethod != null)
                    bleTransferMethod = bTMethod
            }
        }
        return bleTransferMethod
    }

    /**
     * Return the NFC Transfer Method if present or null otherwise.
     * **/
    fun getNfcTransferMethod(): NfcDeviceRetrievalMethod? {
        var nfcTransferMethod: NfcDeviceRetrievalMethod? = null
        deviceRetrievalMethod?.let {
            for (i in deviceRetrievalMethod) {
                val nTMethod = i as? NfcDeviceRetrievalMethod
                if (nTMethod != null)
                    nfcTransferMethod = nTMethod
            }
        }
        return nfcTransferMethod
    }

    /**
     * Return the Wifi Aware Transfer Method if present or null otherwise.
     * **/
    fun getWiFiAwareTransferMethod(): WiFiAwareDeviceRetrievalMethod? {
        var wiFiAwareTransferMethod: WiFiAwareDeviceRetrievalMethod? = null
        deviceRetrievalMethod?.let {
            for (i in deviceRetrievalMethod) {
                val wiFiTMethod = i as? WiFiAwareDeviceRetrievalMethod
                if (wiFiTMethod != null)
                    wiFiAwareTransferMethod = wiFiTMethod
            }
        }
        return wiFiAwareTransferMethod
    }

    /**
     * Return the Token present in the Options variable or null if it does not exist.
     * **/
    fun getToken(): String? {
        options?.let {
            if (OPTIONS_WEBAPI_KEY in options) {
                val webApi = options[OPTIONS_WEBAPI_KEY] as? WebAPI
                return webApi?.token
            }
        }
        return null
    }

    /**
     * Encode this Object into a CBor bytearray. The structure follows the definition in the standard.
     * **/
    override fun encode(): ByteArray {
        if (decodedFrom?.isNotEmpty() == true) return decodedFrom

        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()
        var structureMap = builder.addMap()

        // Version
        version?.let {
            structureMap = structureMap.put(toDataItem(0), toDataItem(it))
        }

        // Security
        security?.let {
            structureMap = structureMap.put(toDataItem(1), it.encode())
        }

        // Transfer Methods
        deviceRetrievalMethod?.let {
            var transferMethodsBuilder = CborBuilder().addArray()
            if (deviceRetrievalMethod.isNotEmpty()) {
                for (i in deviceRetrievalMethod) {
                    transferMethodsBuilder = transferMethodsBuilder.add(toDataItem(i))
                }
            }
            structureMap = structureMap.put(toDataItem(2), transferMethodsBuilder.end().build()[0])
        }

        // Options
        options?.let {
            var optionsMapBuilder = CborBuilder().addMap()
            if (options.isNotEmpty()) {
                options.forEach { (_, value) ->
                    when (value) {
                        is WebAPI -> {
                            var arr = optionsMapBuilder.putArray(OPTIONS_WEBAPI_KEY)
                            arr = arr.add(value.version.toLong())
                            arr = arr.add(value.baseUrl)
                            arr = arr.add(value.token)
                            optionsMapBuilder = arr.end()

                        }
                        is Oidc -> {
                            var arr = optionsMapBuilder.putArray(OPTIONS_OIDC_KEY)
                            arr = arr.add(value.version.toLong())
                            arr = arr.add(value.url)
                            arr = arr.add(value.token)
                            optionsMapBuilder = arr.end()
                        }
                        is Boolean -> {
                            optionsMapBuilder = optionsMapBuilder.put(OPTIONS_COMPACT_KEY, value)
                        }
                    }
                }

            }
            structureMap = structureMap.put(toDataItem(3), optionsMapBuilder.end().build()[0])
        }

        //*DocType May be used but we can leave it empty
//        val docTypeBuilder = structureArray.addArray()
//        structureArray = docTypeBuilder.end()

        //Proprietary (optional element)
        proprietary?.let {
            if (proprietary.isNotEmpty()) {
                var proprietaryMapBuilder = CborBuilder().addMap()
                proprietary.forEach { (key, value) ->
                    proprietaryMapBuilder = proprietaryMapBuilder.put(key, value)
                }
                structureMap =
                    structureMap.put(toDataItem(4), proprietaryMapBuilder.end().build()[0])
            }
        }

        builder = structureMap.end()

        CborEncoder(outputStream).encode(builder.build())

        return outputStream.toByteArray()
    }

    fun encodeTagged(): ByteArray {
        val outputStream = ByteArrayOutputStream()

        val byteString = ByteString(encode())
        byteString.tag = Tag(24)

        CborEncoder(outputStream).encode(byteString)
        return outputStream.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceEngagement

        if (version != other.version) return false
        // TODO: Transfer Methods and Security must be checked as well
        if (options != other.options) return false
        if (proprietary != other.proprietary) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        // TODO: Security must be taken into account to calculate the hashcode
        result = 31 * result + deviceRetrievalMethod.hashCode()
        result = 31 * result + options.hashCode()
        result = 31 * result + proprietary.hashCode()
        return result
    }

    class Builder {
        private var version: String? = null
        private var security: Security? = null
        private var deviceRetrievalMethods: MutableList<DeviceRetrievalMethod>? = null
        private var options: MutableMap<String, Any>? = null
        private var proprietary: kotlin.collections.Map<String, String>? = null
        private var decodedFrom: ByteArray? = null

        fun version(version: String?) = apply { this.version = version }

        fun security(security: Security?) = apply { this.security = security }

        fun transferMethods(deviceRetrievalMethod: DeviceRetrievalMethod) = apply {
            if (deviceRetrievalMethods == null) {
                deviceRetrievalMethods = mutableListOf()
            }
            deviceRetrievalMethods?.add(deviceRetrievalMethod)
        }

        fun options(options: kotlin.collections.Map<String, Any>?) = apply {
            this.options = options?.toMutableMap()
        }

        fun proprietary(proprietary: kotlin.collections.Map<String, String>) = apply {
            this.proprietary = proprietary
        }

        fun token(token: String) = apply {
            if (options == null) {
                options = mutableMapOf()
            }
            token.let {
                val webAPI = WebAPI.Builder()
                    .setVersion(1)
                    .setToken(token)
                    .build()
                options?.put(OPTIONS_WEBAPI_KEY, webAPI)
            }
        }

        fun decodeFromBase64(deString: String) = apply {
            try {
                val bytes = Base64Utils.decode(deString)
                decode(bytes)
            } catch (ex: IllegalArgumentException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun decode(bytes: ByteArray) = apply {
            decodedFrom = bytes
            try {
                val bais = ByteArrayInputStream(bytes)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size > 0) {
                    val structureItems: Map? = decoded[0] as? Map
                    structureItems?.let { struct ->
                        struct.keys.forEach {
                            val key = it as? UnsignedInteger
                            val value = struct.get(it)
                            when (key?.value) {
                                //version
                                0.toBigInteger() -> {
                                    version = decodeVersion(value)
                                }
                                //security
                                1.toBigInteger() -> {
                                    security = decodeSecurity(value)
                                }
                                //transfer methods
                                2.toBigInteger() -> {
                                    deviceRetrievalMethods = decodeTransferMethods(value)
                                }

                                //options
                                3.toBigInteger() -> {
                                    options = decodeOptions(value)
                                }

                                //proprietary
                                4.toBigInteger() -> {
                                    proprietary = decodeProprietary(value)
                                }

                            }
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun build() = DeviceEngagement(
            version,
            security,
            deviceRetrievalMethods,
            options,
            proprietary,
            decodedFrom
        )

        private fun decodeVersion(item: DataItem?): String? {
            item?.let {
                val version = item as UnicodeString
                return version.string
            }
            return null
        }

        private fun decodeSecurity(item: DataItem?): Security? {
            item?.let {
                return Security.Builder()
                    .fromCborStructure(it as Array)
                    .build()
            }
            return null
        }

        private fun validateCipherSuiteId(csi: UnsignedInteger?) {
            val csid =
                CipherSuiteIdentifiers.cipherSuiteValue.keys.filter { it == csi?.value?.toInt() }
            if (csid.isEmpty()) {
                // TODO: Fix implementation as we're not throwing errors anymore
            } else
                print("$LOG_TAG : ${CipherSuiteIdentifiers.cipherSuiteValue[csid[0]]}\n")
        }

        private fun validateCurveId(ci: UnsignedInteger?) {
            val cid = CurveIdentifiers.curveIds.keys.filter { it == ci?.value?.toInt() }
            if (cid.isEmpty()) {
                // TODO: Fix implementation as we're not throwing errors anymore
            } else
                print("$LOG_TAG : ${CurveIdentifiers.curveIds[cid[0]]}\n")
        }

        private fun decodeTransferMethods(item: DataItem?): MutableList<DeviceRetrievalMethod>? {
            item?.let {
                val tMethods = it as Array
                val tMethodsArray = tMethods.dataItems
                this.deviceRetrievalMethods = mutableListOf()
                for (i in tMethodsArray) {
                    val tMethod = i as? Array
                    tMethod?.let {
                        val arrayOfEachTransferMethod = tMethod.dataItems
                        val decodedTransferMethod = decodeTransferMethod(arrayOfEachTransferMethod)
                        if (decodedTransferMethod != null)
                            this.deviceRetrievalMethods?.add(decodedTransferMethod)
                    }
                }
                return deviceRetrievalMethods
            }
            return null
        }

        private fun decodeTransferMethod(arrayOfEachTransferMethod: List<DataItem>?): DeviceRetrievalMethod? {
            if (arrayOfEachTransferMethod == null) {
                return null
            }
            val type = (arrayOfEachTransferMethod[0] as? UnsignedInteger)?.value?.toInt()
            val version = (arrayOfEachTransferMethod[1] as? UnsignedInteger)?.value?.toInt()
            val mapOptions = arrayOfEachTransferMethod[2] as? Map
            if (type == null || version == null) {
                return null
            }
            if (arrayOfEachTransferMethod.size > 2) {
                when (type) {
                    TRANSFER_TYPE_NFC -> {
                        val nfcOption = NfcDeviceRetrievalMethod.NfcOptions.decode(mapOptions)
                        return NfcDeviceRetrievalMethod(type, version, nfcOption)
                    }
                    TRANSFER_TYPE_BLE -> {
                        val bleOptions = BleDeviceRetrievalMethod.BleOptions.decode(mapOptions)
                        return BleDeviceRetrievalMethod(type, version, bleOptions)

                    }
                    TRANSFER_TYPE_WIFI_AWARE -> {
                        val wifiOptions = WiFiAwareDeviceRetrievalMethod.WifiOptions.decode(mapOptions)
                        return WiFiAwareDeviceRetrievalMethod(type, version, wifiOptions)
                    }
                }
            }
            return null
        }

        private fun decodeOptions(item: DataItem?): MutableMap<String, Any> {
            val optionsMap = mutableMapOf<String, Any>()
            item?.let {
                val options = it as Map
                options.keys?.forEach { k ->
                    val key: UnicodeString = k as UnicodeString
                    var value: Any? = null
                    when (key.string) {
                        OPTIONS_WEBAPI_KEY -> {
                            value = WebAPI.Builder()
                                .fromArray(options.get(k) as? Array)
                                .build()
                        }
                        OPTIONS_OIDC_KEY -> {
                            value = Oidc.Builder()
                                .fromArray(options.get(k) as? Array)
                                .build()
                        }
                        OPTIONS_COMPACT_KEY -> {
                            val simpleValue = options.get(k) as? SimpleValue
                            value = when (simpleValue?.simpleValueType) {
                                SimpleValue.TRUE -> true
                                else -> false
                            }
                        }
                    }
                    value?.let { itValue ->
                        optionsMap[key.string] = itValue
                    }
                }
            }
            return optionsMap
        }

        private fun decodeProprietary(item: DataItem?): MutableMap<String, String> {
            val proprietaryMap = mutableMapOf<String, String>()
            item?.let {
                val proprietary = it as Map
                proprietary.keys?.forEach {
                    val key: UnicodeString? = it as? UnicodeString
                    val value: UnicodeString? = proprietary.get(it) as? UnicodeString
                    key?.let { itKey ->
                        value?.let { itValue ->
                            proprietaryMap.put(itKey.string, itValue.string)
                        }
                    }
                }
            }
            return proprietaryMap
        }
    }
}