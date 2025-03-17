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
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.putCborMap

/**
 * Helper class for building `StaticAuthData` [CBOR](http://cbor.io/) with
 * the following CDDL:
 * <pre>
 * StaticAuthData = {
 * "digestIdMapping": DigestIdMapping,
 * "issuerAuth" : IssuerAuth
 * }
 *
 * DigestIdMapping = {
 * NameSpace =&gt; [ + IssuerSignedItemMetadataBytes ]
 * }
 *
 * IssuerSignedItemMetadataBytes = #6.24(bstr .cbor IssuerSignedItemMetadata)
 *
 * IssuerSignedItemMetadata = {
 * "digestID" : uint,                           ; Digest ID for issuer data auth
 * "random" : bstr,                             ; Random value for issuer data auth
 * "elementIdentifier" : DataElementIdentifier, ; Data element identifier
 * "elementValue" : DataElementValueOrNull      ; Placeholder for Data element value
 * }
 *
 * ; Set to null to use value previously provisioned or non-null
 * ; to use a per-MSO value
 * ;
 * DataElementValueOrNull = null // DataElementValue   ; "//" means or in CDDL
 *
 * ; Defined in ISO 18013-5
 * ;
 * NameSpace = String
 * DataElementIdentifier = String
 * DataElementValue = any
 * DigestID = uint
 * IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
</pre> *
 *
 *
 * Note that IssuerSignedItemMetadata is similar to IssuerSignedItem as defined in ISO 18013-5
 * with the exception that the "elementValue" is NULL.
 *
 * @param digestIdMapping A non-empty mapping between a `Namespace` and a list of
 * `IssuerSignedItemMetadataBytes`.
 * @param encodedIssuerAuth A COSE_Sign1 object with a payload of MobileSecurityObjectBytes.
 * @throws IllegalArgumentException if the `digestIDMapping` is empty.
 */
class StaticAuthDataGenerator(
    private var digestIdMapping: Map<String, List<ByteArray>>,
    private val encodedIssuerAuth: ByteArray
) {
    init {
        require(!digestIdMapping.isEmpty()) { "digestIDs must not be empty" }
    }

    /**
     * Builds the `StaticAuthData` CBOR.
     *
     * @return the bytes of `StaticAuthData` CBOR.
     */
    fun generate(): ByteArray = Cbor.encode(
        buildCborMap {
            putCborMap("digestIdMapping") {
                for ((namespace, bytesList) in digestIdMapping) {
                    putCborArray(namespace) {
                        bytesList.forEach { encodedIssuerSignedItemMetadata ->
                            add(RawCbor(encodedIssuerSignedItemMetadata))
                        }
                    }
                }
            }
            put("issuerAuth", RawCbor(encodedIssuerAuth))
        }
    )
}