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

package com.android.identity.mdoc.mso;

import androidx.annotation.NonNull;

import com.android.identity.internal.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;

/**
 * Helper class for parsing the bytes of <code>StaticAuthData</code>
 * <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>.
 */
public class StaticAuthDataParser {
    private byte[] mEncodedStaticAuthData;

    /**
     * Constructs a {@link StaticAuthDataParser}.
     *
     * @param encodedStaticAuthData The bytes of <code>StaticAuthData</code>.
     */
    public StaticAuthDataParser(@NonNull byte[] encodedStaticAuthData) {
        mEncodedStaticAuthData = encodedStaticAuthData;
    }

    /**
     * Parses the StaticAuthData.
     *
     * @return a {@link StaticAuthData} with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    public @NonNull StaticAuthData parse() {
        StaticAuthData staticAuthData = new StaticAuthData();
        staticAuthData.parse(mEncodedStaticAuthData);
        return staticAuthData;
    }

    /**
     * An object used to represent data parsed from <code>StaticAuthData</code>
     * <a href="http://cbor.io/">CBOR</a>
     */
    public static class StaticAuthData {
        private byte[] mIssuerAuth;
        private Map<String, List<byte[]>> mDigestIdMapping;

        StaticAuthData() {}

        /**
         * Gets the IssuerAuth set in the <code>StaticAuthData</code> CBOR.
         *
         * @return The IssuerAuth byte array.
         */
        @NonNull
        public byte[] getIssuerAuth() {
            return mIssuerAuth;
        }

        /**
         * Gets the mapping between <code>Namespace</code>s and a list of
         * <code>IssuerSignedItemMetadataBytes</code> as set in the <code>StaticAuthData</code> CBOR.
         *
         * @return The digestID mapping.
         */
        @NonNull
        public Map<String, List<byte[]>> getDigestIdMapping() { return mDigestIdMapping; }

        private void parseDigestIdMapping(DataItem digestIdMapping) {
            mDigestIdMapping = new HashMap<>();
            for (String namespace : Util.cborMapExtractMapStringKeys(digestIdMapping)) {
                List<DataItem> namespaceList = Util.cborMapExtractArray(digestIdMapping, namespace);
                List<byte[]> innerArray = new ArrayList<>();

                for (DataItem innerKey : namespaceList) {
                    if (!(innerKey instanceof ByteString)) {
                        throw new IllegalArgumentException("Inner key is not a bstr");
                    }
                    if (innerKey.getTag().getValue() != 24) {
                        throw new IllegalArgumentException("Inner key does not have tag 24");
                    }
                    // Strictly not necessary but check that elementValue is NULL. This is to
                    // avoid applications (or issuers) sending the value in issuerSignedMapping
                    // which is part of staticAuthData.
                    DataItem issuerSignedItem = Util.cborExtractTaggedAndEncodedCbor(innerKey);
                    DataItem value = Util.cborMapExtract(issuerSignedItem, "elementValue");
                    if (!(value instanceof SimpleValue)
                            || ((SimpleValue) value).getSimpleValueType() != SimpleValueType.NULL) {
                        String name = Util.cborMapExtractString(issuerSignedItem, "elementIdentifier");
                        throw new IllegalArgumentException("elementValue for nameSpace " + namespace
                                + " elementName " + name + " is not NULL");
                    }
                    innerArray.add(Util.cborEncode(innerKey));
                }

                mDigestIdMapping.put(namespace, innerArray);
            }
        }

        void parse(byte[] encodedStaticAuthData) {
            DataItem staticAuthData = Util.cborDecode(encodedStaticAuthData);

            mIssuerAuth = Util.cborEncode(Util.cborMapExtract(staticAuthData, "issuerAuth"));
            parseDigestIdMapping(Util.cborMapExtract(staticAuthData, "digestIdMapping"));
        }
    }

}
