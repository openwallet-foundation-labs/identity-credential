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

package com.android.identity.wwwreader;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;

import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

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
        mDocRequestsBuilder = new CborBuilder().addArray();
    }

    /**
     * Sets the bytes of the <code>SessionTranscript</code> CBOR.
     *
     * This must be called if any of the document requests use reader authentication.
     *
     * @param encodedSessionTranscript the bytes of <code>SessionTranscript</code>.
     * @return the <code>DeviceRequestGenerator</code>.
     */
    public DeviceRequestGenerator setSessionTranscript(byte[] encodedSessionTranscript) {
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
     * @param readerKeySignature        <code>null</code> if not signing the request, otherwise a
     *                                  {@link Signature} to be used for signing the request.
     * @param readerKeyCertificateChain <code>null</code> if <code>readerKeySignature</code> is
     *                                  <code>null</code>, otherwise a chain of X.509
     *                                  certificates for <code>readerKey</code>.
     * @return the <code>DeviceRequestGenerator</code>.
     */
    public DeviceRequestGenerator addDocumentRequest(String docType,
        Map<String, Map<String, Boolean>> itemsToRequest,
        Map<String, byte[]> requestInfo,
        Signature readerKeySignature,
        Collection<X509Certificate> readerKeyCertificateChain) {

        CborBuilder nameSpacesBuilder = new CborBuilder();
        MapBuilder<CborBuilder> nsBuilder = nameSpacesBuilder.addMap();

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

        CborBuilder itemsRequestBuilder = new CborBuilder();
        MapBuilder<CborBuilder> irMapBuilder = itemsRequestBuilder.addMap();
        irMapBuilder.put("docType", docType);
        irMapBuilder.put(new UnicodeString("nameSpaces"), nameSpacesBuilder.build().get(0));
        if (requestInfo != null) {
            MapBuilder<MapBuilder<CborBuilder>> riBuilder = irMapBuilder.putMap("requestInfo");
            for (String key : requestInfo.keySet()) {
                byte[] value = requestInfo.get(key);
                DataItem valueDataItem = Util.cborDecode(value);
                riBuilder.put(new UnicodeString(key), valueDataItem);
            }
            riBuilder.end();
        }
        irMapBuilder.end();
        byte[] encodedItemsRequest = Util.cborEncode(itemsRequestBuilder.build().get(0));

        DataItem itemsRequestBytesDataItem = Util.cborBuildTaggedByteString(encodedItemsRequest);

        DataItem readerAuth = null;
        if (readerKeySignature != null) {
            if (readerKeyCertificateChain == null) {
                throw new IllegalArgumentException("readerKey is provided but no cert chain");
            }
            if (mEncodedSessionTranscript == null) {
                throw new IllegalStateException("sessionTranscript has not been set");
            }

            byte[] encodedReaderAuthentication = Util.cborEncode(new CborBuilder()
                    .addArray()
                    .add("ReaderAuthentication")
                    .add(Util.cborDecode(mEncodedSessionTranscript))
                    .add(itemsRequestBytesDataItem)
                    .end()
                    .build().get(0));

            byte[] readerAuthenticationBytes =
                    Util.cborEncode(
                            Util.cborBuildTaggedByteString(
                                    encodedReaderAuthentication));

            readerAuth = Util.coseSign1Sign(readerKeySignature,
                    null,
                    readerAuthenticationBytes,
                    readerKeyCertificateChain);
        }

        CborBuilder docRequestBuilder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = docRequestBuilder.addMap();
        mapBuilder.put(new UnicodeString("itemsRequest"), itemsRequestBytesDataItem);
        if (readerAuth != null) {
            mapBuilder.put(new UnicodeString("readerAuth"), readerAuth);
        }
        DataItem docRequest = docRequestBuilder.build().get(0);

        mDocRequestsBuilder.add(docRequest);
        return this;
    }

    /**
     * Builds the <code>DeviceRequest</code> CBOR.
     *
     * @return the bytes of <code>DeviceRequest</code> CBOR.
     */
    public byte[] generate() {
        return Util.cborEncode(new CborBuilder()
                .addMap()
                .put("version", "1.0")
                .put(new UnicodeString("docRequests"), mDocRequestsBuilder.end().build().get(0))
                .end()
                .build().get(0));
    }

}