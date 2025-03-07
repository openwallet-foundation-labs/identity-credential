/*
 * Copyright 2022 The Android Open Source Project
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
package org.multipaz.mdoc.request

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor.decode
import org.multipaz.cbor.Cbor.encode
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.Cose.coseSign1Sign
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.EcPrivateKey

/**
 * Helper class for building `DeviceRequest` [CBOR](http://cbor.io/)
 * as specified in ISO/IEC 18013-5:2021 section 8.3 Device Retrieval.
 *
 * This class supports requesting data for multiple documents in a single presentation.
 *
 * @param encodedSessionTranscript the bytes of `SessionTranscript`.
 */
class DeviceRequestGenerator(
    val encodedSessionTranscript: ByteArray
) {
    private val docRequestsBuilder = CborArray.builder()

    /**
     * Adds a request for a document and which data elements to request.
     *
     * @param docType the document type.
     * @param itemsToRequest the items to request as a map of namespaces into data
     * element names into the intent-to-retain for each data element.
     * @param requestInfo null or additional information provided. This is
     * a map from keys and the values must be valid CBOR
     * @param readerKey `null` if not signing the request, otherwise a [EcPrivateKey].
     * @param signatureAlgorithm [Algorithm.UNSET] if readerKey is null, otherwise algorithm to use.
     * @param readerKeyCertificateChain null if readerKey is null, otherwise the certificate chain
     * for readerKey.
     * @return the [DeviceRequestGenerator].
     */
    fun addDocumentRequest(
        docType: String,
        itemsToRequest: Map<String, Map<String, Boolean>>,
        requestInfo: Map<String, ByteArray>?,
        readerKey: EcPrivateKey?,
        signatureAlgorithm: Algorithm,
        readerKeyCertificateChain: X509CertChain?
    ): DeviceRequestGenerator = apply {
        // TODO: Add variant that can sign with SecureArea readerKey
        val nsBuilder = CborMap.builder().apply {
            for ((namespaceName, innerMap) in itemsToRequest) {
                putMap(namespaceName).let { elemBuilder ->
                    for ((elemName, intentToRetain) in innerMap) {
                        elemBuilder.put(elemName, intentToRetain)
                    }
                    elemBuilder.end()
                }
            }
        }
        nsBuilder.end()

        val irMapBuilder = CborMap.builder().apply {
            put("docType", docType)
            put("nameSpaces", nsBuilder.end().build())
        }
        requestInfo?.let {
            irMapBuilder.putMap("requestInfo").let { riBuilder ->
                for ((key, value) in requestInfo) {
                    decode(value).also { valueDataItem ->
                        riBuilder.put(key, valueDataItem)
                    }
                }
                riBuilder.end()
            }
        }
        irMapBuilder.end()

        val encodedItemsRequest = encode(irMapBuilder.end().build())
        val itemsRequestBytesDataItem: DataItem = Tagged(24, Bstr(encodedItemsRequest))
        var readerAuth: DataItem? = null
        if (readerKey != null) {
            requireNotNull(readerKeyCertificateChain) { "readerKey is provided but no cert chain" }
            checkNotNull(encodedSessionTranscript) { "sessionTranscript has not been set" }
            val encodedReaderAuthentication = encode(
                CborArray.builder()
                    .add("ReaderAuthentication")
                    .add(RawCbor(encodedSessionTranscript))
                    .add(itemsRequestBytesDataItem)
                    .end()
                    .build()
            )
            val readerAuthenticationBytes =
                encode(Tagged(24, Bstr(encodedReaderAuthentication)))
            val protectedHeaders = mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_ALG),
                    signatureAlgorithm.coseAlgorithmIdentifier.toDataItem()
                )
            )
            val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                    readerKeyCertificateChain.toDataItem()
                )
            )
            readerAuth = coseSign1Sign(
                readerKey,
                readerAuthenticationBytes,
                false,
                signatureAlgorithm,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem()
        }

        CborMap.builder().let { mapBuilder ->
            mapBuilder.put("itemsRequest", itemsRequestBytesDataItem)
            if (readerAuth != null) {
                mapBuilder.put("readerAuth", readerAuth)
            }
            val docRequest = mapBuilder.end().build()
            docRequestsBuilder.add(docRequest)
        }
    }

    /**
     * Builds the `DeviceRequest` CBOR.
     *
     * @return the bytes of `DeviceRequest` CBOR.
     */
    fun generate(): ByteArray = encode(
        CborMap.builder()
            .put("version", "1.0")
            .put("docRequests", docRequestsBuilder.end().build())
            .end()
            .build()
    )
}
