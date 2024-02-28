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

package com.android.identity.mdoc.request;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.cbor.ArrayBuilder;
import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborArray;
import com.android.identity.cbor.CborBuilder;
import com.android.identity.cbor.CborMap;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.MapBuilder;
import com.android.identity.cbor.RawCbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.cose.Cose;
import com.android.identity.cose.CoseLabel;
import com.android.identity.cose.CoseNumberLabel;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.CertificateChain;
import com.android.identity.crypto.EcPrivateKey;

import java.util.Map;

/**
 * Helper class for building <code>DeviceRequest</code> <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
 *
 * <p>This class supports requesting data for multiple documents in a single presentation.
 */
public final class DeviceRequestGenerator {
    private static final String TAG = "DeviceRequestGenerator";

    private final ArrayBuilder<CborBuilder> mDocRequestsBuilder;
    private byte[] mEncodedSessionTranscript;

    /**
     * Constructs a new {@link DeviceRequestGenerator}.
     */
    public DeviceRequestGenerator() {
        mDocRequestsBuilder = CborArray.Companion.builder();
    }

    /**
     * Sets the bytes of the <code>SessionTranscript</code> CBOR.
     *
     * This must be called if any of the document requests use reader authentication.
     *
     * @param encodedSessionTranscript the bytes of <code>SessionTranscript</code>.
     * @return the <code>DeviceRequestGenerator</code>.
     */
    @NonNull
    public DeviceRequestGenerator setSessionTranscript(
            @NonNull byte[] encodedSessionTranscript) {
        mEncodedSessionTranscript = encodedSessionTranscript;
        return this;
    }

    /**
     * Adds a request for a document and which data elements to request.
     *
     * @param docType                   the document type.
     * @param itemsToRequest            the items to request as a map of namespaces into data
     *                                  element
     *                                  names into the intent-to-retain for each data element.
     * @param requestInfo               <code>null</code> or additional information provided.
     *                                  This is
     *                                  a map from keys and the values must be valid
     *                                  <a href="http://cbor.io/">CBOR</a>.
     * @param readerKey                 <code>null</code> if not signing the request, otherwise a
     *                                  {@link EcPrivateKey} to be used for signing the request.
     * @param signatureAlgorithm        {@link Algorithm#UNSET} if <code>readerKey</code> is
     *                                  <code>null</code>, otherwise the signature algorithm to use.
     * @param readerKeyCertificateChain <code>null</code> if <code>readerKey</code> is
     *                                  <code>null</code>, otherwise a chain of X.509
     *                                  certificates for <code>readerKey</code>.
     * @return the <code>DeviceRequestGenerator</code>.
     */
    @NonNull
    public DeviceRequestGenerator addDocumentRequest(@NonNull String docType,
            @NonNull Map<String, Map<String, Boolean>> itemsToRequest,
            @Nullable Map<String, byte[]> requestInfo,
            @Nullable EcPrivateKey readerKey,
            Algorithm signatureAlgorithm,
            @Nullable CertificateChain readerKeyCertificateChain) {

        // TODO: Add variant that can sign with SecureArea readerKey

        MapBuilder<CborBuilder> nsBuilder = CborMap.Companion.builder();

        for (String namespaceName : itemsToRequest.keySet()) {
            Map<String, Boolean> innerMap = itemsToRequest.get(namespaceName);
            MapBuilder<MapBuilder<CborBuilder>> elemBuilder = nsBuilder.putMap(namespaceName);
            for (String elemName : innerMap.keySet()) {
                boolean intentToRetain = innerMap.get(elemName).booleanValue();
                elemBuilder.put(elemName, intentToRetain);
            }
            elemBuilder.end();
        }
        nsBuilder.end();

        MapBuilder<CborBuilder> irMapBuilder = CborMap.Companion.builder();
        irMapBuilder.put("docType", docType);
        irMapBuilder.put("nameSpaces", nsBuilder.end().build());
        if (requestInfo != null) {
            MapBuilder<MapBuilder<CborBuilder>> riBuilder = irMapBuilder.putMap("requestInfo");
            for (String key : requestInfo.keySet()) {
                byte[] value = requestInfo.get(key);
                DataItem valueDataItem = Cbor.decode(value);
                riBuilder.put(key, valueDataItem);
            }
            riBuilder.end();
        }
        irMapBuilder.end();
        byte[] encodedItemsRequest = Cbor.encode(irMapBuilder.end().build());

        DataItem itemsRequestBytesDataItem = new Tagged(24, new Bstr(encodedItemsRequest));

        DataItem readerAuth = null;
        if (readerKey != null) {
            if (readerKeyCertificateChain == null) {
                throw new IllegalArgumentException("readerKey is provided but no cert chain");
            }
            if (mEncodedSessionTranscript == null) {
                throw new IllegalStateException("sessionTranscript has not been set");
            }

            byte[] encodedReaderAuthentication = Cbor.encode(
                    CborArray.Companion.builder()
                    .add("ReaderAuthentication")
                    .add(new RawCbor(mEncodedSessionTranscript))
                    .add(itemsRequestBytesDataItem)
                    .end()
                    .build());

            byte[] readerAuthenticationBytes =
                    Cbor.encode(new Tagged(24, new Bstr(encodedReaderAuthentication)));

            Map<CoseLabel, DataItem> protectedHeaders = Map.of(
                    new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                    DataItemExtensionsKt.getToDataItem(signatureAlgorithm.getCoseAlgorithmIdentifier())
            );
            Map<CoseLabel, DataItem> unprotectedHeaders = Map.of(
                    new CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                    readerKeyCertificateChain.getDataItem()
            );

            readerAuth = Cose.coseSign1Sign(
                    readerKey,
                    readerAuthenticationBytes,
                    false,
                    signatureAlgorithm,
                    protectedHeaders,
                    unprotectedHeaders
            ).getToDataItem();
        }

        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        mapBuilder.put("itemsRequest", itemsRequestBytesDataItem);
        if (readerAuth != null) {
            mapBuilder.put("readerAuth", readerAuth);
        }
        DataItem docRequest = mapBuilder.end().build();

        mDocRequestsBuilder.add(docRequest);
        return this;
    }

    /**
     * Builds the <code>DeviceRequest</code> CBOR.
     *
     * @return the bytes of <code>DeviceRequest</code> CBOR.
     */
    @NonNull
    public byte[] generate() {
        return Cbor.encode(CborMap.Companion.builder()
                .put("version", "1.0")
                .put("docRequests", mDocRequestsBuilder.end().build())
                .end()
                .build());
    }

}
