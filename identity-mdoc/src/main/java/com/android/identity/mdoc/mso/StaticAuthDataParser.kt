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
package com.android.identity.mdoc.mso

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem

/**
 * Helper class for parsing the bytes of `StaticAuthData`
 * [CBOR](http://cbor.io/)
 * as specified in ISO/IEC 18013-5:2021 section 9.1.2 Issuer data authentication.
 *
 * @param encodedStaticAuthData the bytes of `StaticAuthData` CBOR.
 */
class StaticAuthDataParser(private val encodedStaticAuthData: ByteArray) {

    /**
     * Parses the StaticAuthData.
     *
     * @return a [StaticAuthData] with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    fun parse(): StaticAuthData {
        val staticAuthData = StaticAuthData()
        staticAuthData.parse(encodedStaticAuthData)
        return staticAuthData
    }

    /**
     * An object used to represent data parsed from `StaticAuthData`
     * [CBOR](http://cbor.io/)
     */
    class StaticAuthData internal constructor() {
        /**
         * The IssuerAuth set in the `StaticAuthData` CBOR.
         */
        lateinit var issuerAuth: ByteArray

        private val _digestIdMapping: MutableMap<String, List<ByteArray>> = mutableMapOf()

        /**
         * The mapping between `Namespace`s and a list of
         * `IssuerSignedItemMetadataBytes` as set in the `StaticAuthData` CBOR.
         */
        val digestIdMapping: Map<String, List<ByteArray>>
            get() = _digestIdMapping

        private fun parseDigestIdMapping(digestIdMapping: DataItem) {
            for (namespaceDataItem in digestIdMapping.asMap.keys) {
                val namespace = namespaceDataItem.asTstr
                val namespaceList = digestIdMapping[namespaceDataItem].asArray
                val innerArray: MutableList<ByteArray> = ArrayList()
                for (innerKey in namespaceList) {
                    innerArray.add(Cbor.encode(innerKey))
                }
                _digestIdMapping[namespace] = innerArray
            }
        }

        internal fun parse(encodedStaticAuthData: ByteArray) {
            val staticAuthData = Cbor.decode(encodedStaticAuthData)
            issuerAuth = Cbor.encode(staticAuthData["issuerAuth"])
            parseDigestIdMapping(staticAuthData["digestIdMapping"])
        }
    }
}
