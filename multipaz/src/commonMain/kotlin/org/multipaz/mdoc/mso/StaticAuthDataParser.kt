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
package org.multipaz.mdoc.mso

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem

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
    fun parse(): StaticAuthData = StaticAuthData().apply {
        parse(encodedStaticAuthData)
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

        private val _digestIdMapping = mutableMapOf<String, List<ByteArray>>()

        /**
         * The mapping between `Namespace`s and a list of
         * `IssuerSignedItemMetadataBytes` as set in the `StaticAuthData` CBOR.
         */
        val digestIdMapping: Map<String, List<ByteArray>>
            get() = _digestIdMapping

        private fun parseDigestIdMapping(digestIdMapping: DataItem) {
            for ((namespaceDataItemKey, namespaceDataItemValue) in digestIdMapping.asMap) {
                val namespace = namespaceDataItemKey.asTstr
                namespaceDataItemValue.asArray.let { namespaceList ->
                    namespaceList.map { innerKey -> Cbor.encode(innerKey) }.also { innerList ->
                        _digestIdMapping[namespace] = innerList
                    }
                }
            }
        }

        internal fun parse(encodedStaticAuthData: ByteArray) =
            Cbor.decode(encodedStaticAuthData).run {
                issuerAuth = Cbor.encode(this["issuerAuth"])
                if (this.hasKey("digestIdMapping")) {
                    parseDigestIdMapping(this["digestIdMapping"])
                } else if (this.hasKey("nameSpaces")) {
                    parseDigestIdMapping(this["nameSpaces"])
                }
            }

    }
}
