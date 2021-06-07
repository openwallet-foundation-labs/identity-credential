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
import androidx.security.identity.ResultData
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
    val documents: List<Document>,
    val documentErrors: List<kotlin.collections.Map<String, Int>>,
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
        const val KEY_DOCUMENT_ERRORS = "documentErrors"
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
                array.add(toDataItem(document))
            }
            map.put(toDataItem(KEY_DOCUMENTS), array)
        }

        // DocumentErrors
        if (documentErrors.isNotEmpty()) {
            val array = Array()
            documentErrors.forEach { documentError ->
                val documentMap = Map()
                documentError.forEach { itemMap ->
                    documentMap.put(toDataItem(itemMap.key), toDataItem(itemMap.value))
                }
                array.add(documentMap)
            }
            map.put(toDataItem(KEY_DOCUMENT_ERRORS), array)
        }

        //Status
        map.put(toDataItem(KEY_STATUS), toDataItem(status))

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(map)

        return outputStream.toByteArray()
    }

    class Builder {
        private var version = DEFAULT_VERSION
        private var documents = mutableListOf<Document>()
        private var documentErrors = mutableListOf<kotlin.collections.Map<String, Int>>()
        private var status = 0

        private fun setVersion(version: String) = apply {
            this.version = version
        }

        fun setDocuments(documents: MutableList<Document>) = apply {
            this.documents = documents
        }

        fun setDocumentErrors(documentErrors: MutableList<kotlin.collections.Map<String, Int>>) =
            apply {
                this.documentErrors = documentErrors
            }

        private fun setStatus(status: Int) = apply {
            this.status = status
        }

        fun isError() = apply {
            this.status != 0
        }

        fun responseForRequest(
            requestItems: List<String>,
            resultData: ResultData,
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

                val itemsWithValue: MutableList<IssuerSignedItem> = mutableListOf()
                sortedList.forEach { item ->
                    val value = resultData.getEntry(MdlNamespace.namespace, item.elementIdentifier)
                    val status =
                        resultData.getStatus(MdlNamespace.namespace, item.elementIdentifier)

                    // TODO: Build new IssuerSignedItem combining |item| and |value|. This way
                    //  the IssuerSignedItem we store outside the IdentityCredential API
                    //  won't have to include the value (which it does today).
                    itemsWithValue.add(item)
                }

                if (itemsWithValue.isNotEmpty()) {
                    // Create IssuerSigned Obj with the only supported namespace
                    issuerAuth?.let { coseSign1 ->
                        val issuerSigned = IssuerSigned.Builder()
                            .setNameSpaces(MdlNamespace.namespace, itemsWithValue.toTypedArray())
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
                                    val document = Document.Builder()
                                        .setDocType(MdlDoctype.docType)
                                        .setDeviceSigned(deviceSigned)
                                        .setIssuerSigned(issueSign)
                                        .build()
                                    document?.let { doc ->
                                        setDocuments(mutableListOf(doc))
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

                                KEY_DOCUMENT_ERRORS -> {
                                    val docErrorsResponse = struct.get(it) as? Array
                                    docErrorsResponse?.let { docErrors ->
                                        setDocumentErrors(decodeDocErrorsResponse(docErrors))
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
                MutableList<Document> {
            val documents = mutableListOf<Document>()
            docs.dataItems.forEach { d ->
                val docMap = d as? Map
                docMap?.let { dMap ->
                    val document = Document.Builder()
                        .decode(dMap)
                        .build()
                    document?.let { d ->
                        documents.add(d)
                    }
                }
            }
            return documents
        }

        private fun decodeDocErrorsResponse(docErrors: Array):
                MutableList<kotlin.collections.Map<String, Int>> {
            val documentErrors = mutableListOf<kotlin.collections.Map<String, Int>>()
            docErrors.dataItems.forEach { de ->
                val docErrorMap = de as? Map
                docErrorMap?.keys?.forEach { dType ->
                    val docType: UnicodeString? = dType as? UnicodeString
                    docType?.let { dt ->
                        val errorCode = docErrorMap.get(dt) as? UnsignedInteger
                        errorCode?.let { eCode ->
                            documentErrors.add(mapOf(Pair(dt.string, eCode.value.toInt())))
                        }
                    }
                }
            }
            return documentErrors
        }

        fun build(): Response {
            return Response(
                version,
                documents,
                documentErrors,
                status
            )
        }
    }
}