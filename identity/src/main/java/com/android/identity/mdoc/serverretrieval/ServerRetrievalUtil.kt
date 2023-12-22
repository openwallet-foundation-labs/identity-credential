/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.identity.mdoc.serverretrieval

import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.SimpleValueType
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.internal.Util
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Utility class with helper functions for Server Retrieval
 */
object ServerRetrievalUtil {

    /**
     * Convert a map to an URL Query
     *
     * @param map a map with the parameters to encode
     * @return the URL Query
     */
    fun mapToUrlQuery(map: Map<String, String>): String {
        val stringBuffer = StringBuffer()
        var first = true
        map.keys.forEach {
            if (!first) {
                stringBuffer.append("&")
            }
            first = false
            stringBuffer.append(it)
            stringBuffer.append("=")
            stringBuffer.append(URLEncoder.encode(map[it], StandardCharsets.UTF_8.name()))
        }
        return stringBuffer.toString()
    }

    /**
     * Convert a URL Query to a map.
     *
     * @param url the url containing the URL Query
     * @return a map with the parameters to encode
     */
    fun urlToMap(url: String): Map<String, String> {
        val queryString = url.split("?").last()
        val parametersAndValues = queryString.split("&")
        val result = parametersAndValues.map {
            val parameterAndValue = it.split("=")
            Pair(
                parameterAndValue[0],
                URLDecoder.decode(parameterAndValue[1], StandardCharsets.UTF_8.name())
            )
        }.toMap()
        return result
    }

    /**
     * Convert a value from the [NameSpacedData] to a [JsonElement]
     *
     * @param nameSpacedData the object containing the data
     * @param credentialType the metadata of the mdoc document
     * @param namespace the namespace of the data element
     * @param element the requested data element
     * @return a [JsonElement] representing the data
     */
    fun getJsonElement(
        nameSpacedData: NameSpacedData,
        credentialType: MdocCredentialType,
        namespace: String,
        element: String
    ): JsonElement? {
        return when (credentialType.namespaces[namespace]?.dataElements?.get(element)?.attribute?.type) {
            null -> null // not existing element
            is CredentialAttributeType.BOOLEAN -> JsonPrimitive(
                nameSpacedData.getDataElementBoolean(
                    namespace, element
                )
            )

            is CredentialAttributeType.PICTURE -> JsonPrimitive(
                String(
                    Base64.getEncoder()
                        .encode(nameSpacedData.getDataElementByteString(namespace, element))
                )
            )

            is CredentialAttributeType.NUMBER, is CredentialAttributeType.IntegerOptions -> JsonPrimitive(
                nameSpacedData.getDataElementNumber(
                    namespace, element
                )
            )

            is CredentialAttributeType.COMPLEX_TYPE -> convertCborToJson(
                nameSpacedData.getDataElement(
                    namespace, element
                )
            )

            else -> JsonPrimitive(nameSpacedData.getDataElementString(namespace, element))
        }
    }

    private fun convertCborToJson(cbor: ByteArray): JsonElement {
        return convertDataItemToJson(Util.cborDecode(cbor))
    }

    private fun convertDataItemToJson(dataItem: DataItem): JsonElement {
        return when (dataItem) {
            is co.nstant.`in`.cbor.model.Array -> {
                buildJsonArray {
                    dataItem.dataItems.forEach {
                        add(convertDataItemToJson(it))
                    }
                }
            }

            is co.nstant.`in`.cbor.model.Map -> {
                buildJsonObject {
                    dataItem.keys.forEach {
                        put(it.toString(), convertDataItemToJson(dataItem.get(it)))
                    }
                }
            }

            is co.nstant.`in`.cbor.model.Number -> JsonPrimitive(Util.checkedLongValue(dataItem))

            is co.nstant.`in`.cbor.model.SimpleValue -> JsonPrimitive(dataItem.simpleValueType == SimpleValueType.TRUE)

            else -> JsonPrimitive(dataItem.toString())
        }
    }
}