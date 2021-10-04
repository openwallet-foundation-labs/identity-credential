/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Helper class for parsing the bytes of <code>DeviceRequest</code>
 * <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
 */
public class DeviceRequestParser {
    static final String TAG = "DeviceRequestParser";

    private byte[] mEncodedDeviceRequest;
    private byte[] mEncodedSessionTranscript;

    /**
     * Constructs a {@link DeviceRequestParser}.
     */
    public DeviceRequestParser() {
    }

    /**
     * Sets the bytes of the <code>DeviceRequest</code> CBOR.
     *
     * @param encodedDeviceRequest the bytes of the <code>DeviceRequest</code> CBOR.
     * @return the <code>DeviceRequestParser</code>.
     */
    public @NonNull DeviceRequestParser setDeviceRequest(@NonNull byte[] encodedDeviceRequest) {
        mEncodedDeviceRequest = encodedDeviceRequest;
        return this;
    }

    /**
     * Sets the bytes of the <code>SessionTranscript</code> CBOR.
     *
     * @param encodedSessionTranscript the bytes of <code>SessionTranscript</code>.
     * @return the <code>DeviceRequestParser</code>.
     */
    public @NonNull DeviceRequestParser setSessionTranscript(
            @NonNull byte[] encodedSessionTranscript) {
        mEncodedSessionTranscript = encodedSessionTranscript;
        return this;
    }

    /**
     * Parses the device request.
     *
     * @return a {@link DeviceRequestParser.DeviceRequest} with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    public @NonNull DeviceRequest parse() {
        if (mEncodedDeviceRequest == null) {
            throw new IllegalStateException("deviceRequest has not been set");
        }
        if (mEncodedSessionTranscript == null) {
            throw new IllegalStateException("sessionTranscript has not been set");
        }
        DataItem sessionTranscript = Util.cborDecode(mEncodedSessionTranscript);
        DeviceRequestParser.DeviceRequest request = new DeviceRequestParser.DeviceRequest();
        request.parse(mEncodedDeviceRequest, sessionTranscript);
        return request;
    }

    /**
     * An object used to represent data parsed from <code>DeviceRequest</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
     */
    public static class DeviceRequest {
        static final String TAG = "DeviceRequest";
        private String mVersion;
        private final Collection<DocumentRequest> mDocumentRequests = new ArrayList<>();

        /**
         * Gets the version string set in the <code>DeviceRequest</code> CBOR.
         *
         * @return the version string e.g. "1.0".
         */
        public @NonNull String getVersion() {
            return mVersion;
        }

        /**
         * Gets the document requests in the <code>DeviceRequest</code> CBOR.
         *
         * @return a collection of {@link DocumentRequest} objects.
         */
        public @NonNull Collection<DocumentRequest> getDocRequests() {
            return mDocumentRequests;
        }

        DeviceRequest() {
        }

        void parse(byte[] encodedDeviceRequest,
                DataItem sessionTranscript) {

            DataItem request = Util.cborDecode(encodedDeviceRequest);
            if (!(request instanceof Map)) {
                throw new IllegalArgumentException("CBOR is not a map");
            }

            mVersion = Util.cborMapExtractString(request, "version");
            if (mVersion.compareTo("1.0") < 0) {
                throw new IllegalArgumentException("Given version '" + mVersion + "' not >= '1.0'");
            }

            Collection<X509Certificate> readerCertChain = null;
            if (Util.cborMapHasKey(request, "docRequests")) {
                List<DataItem> docRequestsDataItems = Util.cborMapExtractArray(request,
                        "docRequests");
                for (DataItem docRequestDataItem : docRequestsDataItems) {
                    DataItem itemsRequestBytesDataItem = Util.cborMapExtract(docRequestDataItem,
                            "itemsRequest");
                    if (!(itemsRequestBytesDataItem instanceof ByteString)
                            || !itemsRequestBytesDataItem.hasTag()
                            || itemsRequestBytesDataItem.getTag().getValue() != 24) {
                        throw new IllegalArgumentException(
                                "itemsRequest value is not a tagged bytestring");
                    }
                    byte[] encodedItemsRequest =
                            ((ByteString) itemsRequestBytesDataItem).getBytes();
                    DataItem itemsRequest = Util.cborDecode(encodedItemsRequest);

                    DataItem readerAuth = ((Map) docRequestDataItem).get(new UnicodeString(
                            "readerAuth"));
                    byte[] encodedReaderAuth = null;
                    if (readerAuth != null) {
                        encodedReaderAuth = Util.cborEncode(readerAuth);

                        readerCertChain = Util.coseSign1GetX5Chain(readerAuth);
                        if (readerCertChain.size() < 1) {
                            throw new IllegalArgumentException(
                                    "No x5chain element in reader signature");
                        }
                        PublicKey readerKey = readerCertChain.iterator().next().getPublicKey();

                        byte[] encodedReaderAuthentication = Util.cborEncode(new CborBuilder()
                                .addArray()
                                .add("ReaderAuthentication")
                                .add(sessionTranscript)
                                .add(itemsRequestBytesDataItem)
                                .end()
                                .build().get(0));

                        byte[] readerAuthenticationBytes =
                                Util.cborEncode(
                                        Util.cborBuildTaggedByteString(
                                                encodedReaderAuthentication));

                        if (!Util.coseSign1CheckSignature(readerAuth,
                                readerAuthenticationBytes,  // detached content
                                readerKey)) {
                            throw new IllegalArgumentException("Error verifying readerAuth");
                        }
                    }

                    DataItem requestInfoDataItem = ((Map) itemsRequest).get(
                            new UnicodeString("requestInfo"));
                    java.util.Map<String, byte[]> requestInfo = null;
                    if (requestInfoDataItem != null) {
                        requestInfo = new HashMap<>();
                        for (String key : Util.cborMapExtractMapStringKeys(requestInfoDataItem)) {
                            byte[] encodedValue = Util.cborEncode(
                                    Util.cborMapExtract(requestInfoDataItem, key));
                            requestInfo.put(key, encodedValue);
                        }
                    }

                    String docType = Util.cborMapExtractString(itemsRequest, "docType");
                    DocumentRequest.Builder builder = new DocumentRequest.Builder(docType,
                            encodedItemsRequest, requestInfo, encodedReaderAuth, readerCertChain);

                    // parse nameSpaces
                    DataItem nameSpaces = Util.cborMapExtractMap(itemsRequest, "nameSpaces");
                    parseNamespaces(nameSpaces, builder);


                    mDocumentRequests.add(builder.build());
                }
            }
        }

        private void parseNamespaces(DataItem nameSpaces, DocumentRequest.Builder builder) {
            Collection<String> nameSpacesKeys = Util.cborMapExtractMapStringKeys(nameSpaces);
            for (String nameSpace : nameSpacesKeys) {
                DataItem itemsMap = Util.cborMapExtractMap(nameSpaces, nameSpace);
                Collection<String> itemKeys = Util.cborMapExtractMapStringKeys(itemsMap);
                for (String itemKey : itemKeys) {
                    boolean intentToRetain = Util.cborMapExtractBoolean(itemsMap, itemKey);
                    builder.addEntry(nameSpace, itemKey, intentToRetain);
                }
            }
        }

        @NonNull
        public Collection<DocumentRequest> getDocumentRequests() {
            Collection<DocumentRequest> docRequests = new ArrayList<>();
            for (DocumentRequest dr : mDocumentRequests) {
                docRequests.add(dr);
            }
            return docRequests;
        }
    }

    /**
     * An object used to represent data parsed from the <code>DocRequest</code>
     * <a href="http://cbor.io/">CBOR</a> (part of <code>DeviceRequest</code>)
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
     */
    public static class DocumentRequest {
        DocumentRequest(@NonNull String docType, @NonNull byte[] encodedItemsRequest,
                @Nullable java.util.Map<String, byte[]> requestInfo,
                @Nullable byte[] encodedReaderAuth,
                @Nullable Collection<X509Certificate> readerCertChain) {
            mDocType = docType;
            mRequestInfo = requestInfo;
            mEncodedItemsRequest = encodedItemsRequest;
            mEncodedReaderAuth = encodedReaderAuth;
            mReaderCertificateChain = readerCertChain;
        }

        String mDocType = null;
        byte[] mEncodedItemsRequest = null;
        java.util.Map<String, byte[]> mRequestInfo = null;
        byte[] mEncodedReaderAuth = null;
        java.util.Map<String, java.util.Map<String, Boolean>> mRequestMap = new LinkedHashMap<>();
        Collection<X509Certificate> mReaderCertificateChain = null;

        /**
         * Returns the document type (commonly referred to as <code>docType</code>) in the request.
         *
         * @return the document type.
         */
        public @NonNull String getDocType() {
            return mDocType;
        }

        /**
         * Gets the requestInfo associated with the document request.
         *
         * <p>This is a map from strings into encoded CBOR.
         *
         * @return the request info map or <code>null</code> if it isn't in the document request.
         */
        @SuppressWarnings("NullableCollection")
        public @Nullable java.util.Map<String, byte[]> getRequestInfo() {
            return mRequestInfo;
        }

        /**
         * Gets the bytes of the <code>ItemsRequest</code> CBOR.
         *
         * @return the bytes of the <code>ItemsRequest</code> CBOR.
         */
        public @NonNull byte[] getItemsRequest() {
            return mEncodedItemsRequest;
        }

        /**
         * Gets the bytes of the <code>ReaderAuth</code> CBOR.
         *
         * @return the bytes of <code>ReaderAuth</code> or <code>null</code> if the reader didn't
         *   sign the document request.
         */
        public @Nullable byte[] getReaderAuth() {
            return mEncodedReaderAuth;
        }

        /**
         * Returns the X509 certificate chain for the reader which signed the data in the
         * document request.
         *
         * @return A X.509 certificate chain or <code>null</code> if the reader didn't sign
         *   the request.
         */
        @SuppressWarnings("NullableCollection")
        public @Nullable Collection<X509Certificate> getReaderCertificateChain() {
            return mReaderCertificateChain;
        }

        /**
         * Gets the names of namespaces that the reader requested.
         *
         * @return Collection of names of namespaces in the request.
         */
        public @NonNull Collection<String> getNamespaces() {
            return Collections.unmodifiableCollection(mRequestMap.keySet());
        }

        /**
         * Gets the names of data elements in the given namespace.
         *
         * @param namespaceName the name of the namespace.
         * @return A collection of data element names or <code>null</code> if the namespace
         *   wasn't requested.
         * @exception IllegalArgumentException if the given namespace wasn't requested.
         */
        public @NonNull Collection<String> getEntryNames(@NonNull String namespaceName) {
            java.util.Map<String, Boolean> innerMap = mRequestMap.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace wasn't requested");
            }
            return Collections.unmodifiableCollection(innerMap.keySet());
        }

        /**
         * Gets the intent-to-retain value set by the reader for a data element in the request.
         *
         * @param namespaceName the name of the namespace.
         * @param entryName the name of the data element
         * @return whether the reader intents to retain the value.
         * @exception IllegalArgumentException if the given data element or namespace wasn't
         *   requested.
         */
        public boolean getIntentToRetain(@NonNull String namespaceName, @NonNull String entryName) {
            java.util.Map<String, Boolean> innerMap = mRequestMap.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace wasn't requested");
            }
            Boolean value = innerMap.get(entryName);
            if (value == null) {
                throw new IllegalArgumentException("Data element wasn't requested");
            }
            return value.booleanValue();
        }

        static class Builder {
            private final DeviceRequestParser.DocumentRequest mResult;

            Builder(@NonNull String docType, @NonNull byte[] encodedItemsRequest,
                    java.util.Map<String, byte[]> requestInfo,
                    @Nullable byte[] encodedReaderAuth,
                    @Nullable Collection<X509Certificate> readerCertChain) {
                this.mResult = new DeviceRequestParser.DocumentRequest(docType,
                        encodedItemsRequest, requestInfo, encodedReaderAuth, readerCertChain);
            }

            Builder addEntry(String namespaceName, String entryName, boolean intentToRetain) {
                java.util.Map<String, Boolean> innerMap = mResult.mRequestMap.get(namespaceName);
                if (innerMap == null) {
                    innerMap = new LinkedHashMap<>();
                    mResult.mRequestMap.put(namespaceName, innerMap);
                }
                innerMap.put(entryName, intentToRetain);
                return this;
            }

            DocumentRequest build() {
                return mResult;
            }
        }

    }
}
