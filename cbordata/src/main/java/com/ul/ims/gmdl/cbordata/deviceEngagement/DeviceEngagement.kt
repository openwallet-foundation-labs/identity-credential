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
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod.Companion.CENTRAL_CLIENT_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod.Companion.CENTRAL_CLIENT_UUID_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod.Companion.PERIPHERAL_MAC_ADDRESS_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod.Companion.PERIPHERAL_SERVER_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod.Companion.PERIPHERAL_UUID_KEY
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.ITransferMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.NfcTransferMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.WiFiAwareTransferMethod
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.utils.Base64Utils
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class DeviceEngagement private constructor(
    val version: String?,
    val security: Security?,
    val transferMethods: List<ITransferMethod>?,
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
    fun getBLETransferMethod(): BleTransferMethod? {
        var bleTransferMethod: BleTransferMethod? = null
        transferMethods?.let {
            for (i in transferMethods) {
                val bTMethod = i as? BleTransferMethod
                if (bTMethod != null)
                    bleTransferMethod = bTMethod
            }
        }
        return bleTransferMethod
    }

    /**
     * Return the NFC Transfer Method if present or null otherwise.
     * **/
    fun getNfcTransferMethod(): NfcTransferMethod? {
        var nfcTransferMethod: NfcTransferMethod? = null
        transferMethods?.let {
            for (i in transferMethods) {
                val nTMethod = i as? NfcTransferMethod
                if (nTMethod != null)
                    nfcTransferMethod = nTMethod
            }
        }
        return nfcTransferMethod
    }

    /**
     * Return the Wifi Aware Transfer Method if present or null otherwise.
     * **/
    fun getWiFiAwareTransferMethod(): WiFiAwareTransferMethod? {
        var wiFiAwareTransferMethod: WiFiAwareTransferMethod? = null
        transferMethods?.let {
            for (i in transferMethods) {
                val wiFiTMethod = i as? WiFiAwareTransferMethod
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
        transferMethods?.let {
            var transferMethodsBuilder = CborBuilder().addArray()
            if (transferMethods.isNotEmpty()) {
                for (i in transferMethods) {
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
                    structureMap.put(toDataItem(5), proprietaryMapBuilder.end().build()[0])
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
        result = 31 * result + transferMethods.hashCode()
        result = 31 * result + options.hashCode()
        result = 31 * result + proprietary.hashCode()
        return result
    }

    class Builder {
        private var version: String? = null
        private var security: Security? = null
        private var transferMethods: MutableList<ITransferMethod>? = null
        private var options: MutableMap<String, Any>? = null
        private var proprietary: kotlin.collections.Map<String, String>? = null
        private var decodedFrom: ByteArray? = null

        fun version(version: String?) = apply { this.version = version }

        fun security(security: Security?) = apply { this.security = security }

        fun transferMethods(transferMethod: ITransferMethod) = apply {
            if (transferMethods == null) {
                transferMethods = mutableListOf()
            }
            transferMethods?.add(transferMethod)
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
                                    transferMethods = decodeTransferMethods(value)
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
            transferMethods,
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

        private fun decodeTransferMethods(item: DataItem?): MutableList<ITransferMethod>? {
            item?.let {
                val tMethods = it as Array
                val tMethodsArray = tMethods.dataItems
                this.transferMethods = mutableListOf()
                for (i in tMethodsArray) {
                    val tMethod = i as? Array
                    tMethod?.let {
                        val arrayOfEachTransferMethod = tMethod.dataItems
                        val decodedTransferMethod = decodeTransferMethod(arrayOfEachTransferMethod)
                        if (decodedTransferMethod != null)
                            this.transferMethods?.add(decodedTransferMethod)
                    }
                }
                return transferMethods
            }
            return null
        }

        private fun decodeTransferMethod(arrayOfEachTransferMethod: List<DataItem>?): ITransferMethod? {
            if (arrayOfEachTransferMethod == null) {
                return null
            }
            val type = (arrayOfEachTransferMethod[0] as? UnsignedInteger)?.value?.toInt()
            val version = (arrayOfEachTransferMethod[1] as? UnsignedInteger)?.value?.toInt()
            if (type == null || version == null) {
                return null
            }
            if (arrayOfEachTransferMethod.size > 2) {
                when (type) {
                    TRANSFER_TYPE_NFC -> {
                        val maxApduLength = (arrayOfEachTransferMethod[2] as? UnsignedInteger)?.value?.toInt()
                        if (maxApduLength != null)
                            return NfcTransferMethod(type, version, maxApduLength)
                    }
                    TRANSFER_TYPE_BLE -> {
                        val bleId = arrayOfEachTransferMethod[2] as? Map
                        bleId?.let {
                            val peripheralServer: Boolean? =
                                decodeBoolean(bleId.get(PERIPHERAL_SERVER_KEY))
                            val centralClient: Boolean? =
                                decodeBoolean(bleId.get(CENTRAL_CLIENT_KEY))
                            val psUuid = (bleId.get(PERIPHERAL_UUID_KEY) as? ByteString)
                            val ccUuid = (bleId.get(CENTRAL_CLIENT_UUID_KEY) as? ByteString)
                            var peripheralUUID: UUID? = null
                            psUuid?.let {
                                peripheralUUID = uuidFromBytes(it.bytes)
                            }
                            var centralUUID: UUID? = null
                            ccUuid?.let {
                                centralUUID = uuidFromBytes(it.bytes)
                            }
                            val macString = bleId.get(PERIPHERAL_MAC_ADDRESS_KEY) as? UnicodeString
                            var mac: String? = null
                            macString?.let {
                                mac = macString.string
                            }
                            val bleIdentification = BleTransferMethod.BleIdentification(
                                peripheralServer,
                                centralClient,
                                peripheralUUID,
                                centralUUID,
                                mac
                            )
                            return BleTransferMethod(type, version, bleIdentification)
                        }
                    }
                    TRANSFER_TYPE_WIFI_AWARE -> {
                        return WiFiAwareTransferMethod(type, version)
                    }
                }
            }
            return null
        }

        private fun uuidFromBytes(bytes: ByteArray): UUID {
            check(bytes.size == 16) { "Expected 16 bytes, found " + bytes.size }
            val data: ByteBuffer = ByteBuffer.wrap(bytes, 0, 16)
            data.order(ByteOrder.BIG_ENDIAN)
            return UUID(data.getLong(0), data.getLong(8))
        }

        private fun decodeBoolean(get: DataItem?): Boolean? {
            get?.let {
                if (get.majorType != MajorType.SPECIAL) {
                    return null
                }
                val simpleValue = get as? SimpleValue
                simpleValue?.let {
                    return when (simpleValue.simpleValueType) {
                        SimpleValueType.FALSE -> false
                        SimpleValueType.TRUE -> true
                        else -> null
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