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

package com.ul.ims.gmdl.cbordata.response

import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.request.Request
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class Response private constructor(
    val version: String,
    val documents: List<kotlin.collections.Map<String, ResponseData>>,
    val status: Int
) : AbstractCborStructure(), IResponse, Serializable {

    override fun isError(): Boolean {
        return status != 0
    }

    companion object {
        const val LOG_TAG = "Response"
        const val DEFAULT_VERSION = "1.0"
        const val KEY_VERSION = "version"
        const val KEY_DOCUMENTS = "documents"
        const val KEY_STATUS = "status"
    }

    override fun encode(): ByteArray {
        val map = Map()

        //Version
        map.put(toDataItem(KEY_VERSION), toDataItem(version))

        //Documents
        if (documents.isNotEmpty()) {
            val array = Array()
            documents.forEach { document ->
                val documentMap = Map()
                document.forEach { itemMap ->
                    documentMap.put(toDataItem(itemMap.key), toDataItem(itemMap.value))
                }
                array.add(documentMap)
            }
            map.put(toDataItem(KEY_DOCUMENTS), array)
        }

        //Status
        map.put(toDataItem(KEY_STATUS), toDataItem(status))

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).nonCanonical().encode(map)

        return outputStream.toByteArray()
    }

    class Builder {
        private var version = DEFAULT_VERSION
        private var documents = mutableListOf<kotlin.collections.Map<String, ResponseData>>()
        private var status = 0

        fun setVersion(version: String) = apply {
            this.version = version
        }

        fun setDocuments(documents: MutableList<kotlin.collections.Map<String, ResponseData>>) =
            apply {
                this.documents = documents
        }

        fun setStatus(status: Int) = apply {
            this.status = status
        }

        fun isError() = apply {
            this.status != 0
        }

        fun responseForRequest(
            requestItems: List<String>,
            deviceAuth: DeviceAuth?, issuerAuth: CoseSign1?,
            issuerNamespaces: IssuerNameSpaces
        ) = apply {

            // Create List of IssuerSignedItems based on the request items
            // Get the Issuer Signed Items from the Google API
            val issuerSignedItems = issuerNamespaces.
                nameSpaces[MdlNamespace.namespace]
            issuerSignedItems?.let {isiList ->

                // Create a new list to avoid ConcurrentModificationException
                val newIsiList: MutableList<IssuerSignedItem> = mutableListOf()

                isiList.forEach {
                    if (requestItems.contains(it.elementIdentifier)) {
                        newIsiList.add(it)
                    }
                }

                val sortedList = newIsiList.sortedBy { it.digestId }
                    .toMutableList()

                if (sortedList.isNotEmpty()) {
                    // Create IssuerSigned Obj with the only supported namespace
                    issuerAuth?.let { coseSign1 ->
                        val issuerSigned = IssuerSigned.Builder()
                            .setNameSpaces(MdlNamespace.namespace, sortedList.toTypedArray())
                            .setIssuerAuth(coseSign1)
                            .build()

                        // DeviceSigned Structure
                        deviceAuth?.let { devAuth ->
                            val deviceSigned = DeviceSigned.Builder()
                                .setDeviceNameSpaces(DeviceNameSpaces.Builder().build())
                                .setDeviceAuth(devAuth)
                                .build()

                            issuerSigned.let { issueSign ->
                                // Create DeviceSigned Obj
                                deviceSigned.let {
                                    val responseData = ResponseData.Builder()
                                        .setDeviceSigned(deviceSigned)
                                        .setIssuerSigned(issueSign)
                                        .build()

                                    // Set the Response Document
                                    responseData?.let {
                                        setDocuments(
                                            mutableListOf(
                                                mapOf(
                                                    Pair(
                                                        MdlDoctype.docType,
                                                        responseData
                                                    )
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun decode(bytes: ByteArray) = apply {
            try {
                val bais = ByteArrayInputStream(bytes)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size > 0) {
                    val structureItems: Map? = decoded[0] as? Map
                    structureItems?.let { struct ->
                        struct.keys.forEach {
                            val key: UnicodeString? = it as? UnicodeString
                            when (key?.string) {
                                KEY_VERSION -> {
                                    val valueVer = struct.get(it) as? UnicodeString
                                    valueVer?.let { ver ->
                                        setVersion(ver.string)
                                    }
                                }

                                KEY_DOCUMENTS -> {
                                    val docsResponse = struct.get(it) as? Array
                                    docsResponse?.let { docs ->
                                        setDocuments(decodeDocsResponse(docs))
                                    }
                                }

                                KEY_STATUS -> {
                                    val status = struct.get(it) as? UnsignedInteger
                                    status?.let { st ->
                                        setStatus(st.value.toInt())
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(Request.LOG_TAG, "${ex.message}")
            }
        }

        private fun decodeDocsResponse(docs: Array):
                MutableList<kotlin.collections.Map<String, ResponseData>> {
            val documents = mutableListOf<kotlin.collections.Map<String, ResponseData>>()
            docs.dataItems.forEach { d ->
                val docMap = d as? Map
                docMap?.keys?.forEach { dType ->
                    val docType: UnicodeString? = dType as? UnicodeString
                    docType?.let { dt ->
                        val respDataMap = docMap.get(dt) as? Map
                        respDataMap?.let { resData ->
                            val responseData = ResponseData.Builder()
                                .decode(resData)
                                .build()
                            responseData?.let { r ->
                                documents.add(mapOf(Pair(dt.string, r)))
                            }
                        }
                    }
                }
            }
            return documents
        }

        fun build(): Response {
            return Response(
                version,
                documents,
                status
            )
        }
    }
}