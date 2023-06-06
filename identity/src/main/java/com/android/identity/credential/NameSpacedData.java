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

package com.android.identity.credential;

import androidx.annotation.NonNull;

import com.android.identity.internal.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Key/value pairs, organized by name space.
 *
 * <p>This class implements a data model which consists of a series of name spaces
 * (identified by a string such as <em>org.iso.18013.5.1</em>) where each name space
 * contains a number of key/value pairs where keys are strings and values are
 * <a href="https://datatracker.ietf.org/doc/html/rfc8949">CBOR</a> values.
 *
 * <p>While this happens to be similar to the mDL/MDOC data model used in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>,
 * it's flexible enough to be used to store credential data for any kind of
 * credential.
 *
 * <p>This type is immutable.
 */
public class NameSpacedData {
    private LinkedHashMap<String, LinkedHashMap<String, byte[]>> mMap;

    NameSpacedData() {
        mMap = new LinkedHashMap<>();
    }

    private NameSpacedData(LinkedHashMap<String, LinkedHashMap<String, byte[]>> map) {
        mMap = map;
    }

    /**
     * Creates a new {@link NameSpacedData} from encoded CBOR.
     *
     * @param encodedCbor CBOR encoded in the format described by {@link #encodeAsCbor()}.
     * @return A {@link NameSpacedData}.
     * @throws IllegalArgumentException if the given data is not valid CBOR.
     * @throws IllegalArgumentException if the given data does not confirm to the CDDL in
     *                                  {@link #encodeAsCbor()}.
     */
    static public @NonNull NameSpacedData fromEncodedCbor(@NonNull byte[] encodedCbor) {
        return fromCbor(Util.cborDecode(encodedCbor));
    }

    /**
     * Gets all the name space names.
     *
     * @return list of name space names.
     */
    public @NonNull List<String> getNameSpaceNames() {
        List<String> ret = new ArrayList<>();
        for (String nameSpaceName : mMap.keySet()) {
            ret.add(nameSpaceName);
        }
        return ret;
    }

    /**
     * Gets all data elements in a name space.
     *
     * @param nameSpaceName the name space name.
     * @return list of all data element names in the name space.
     */
    public @NonNull List<String> getDataElementNames(@NonNull String nameSpaceName) {
        LinkedHashMap<String, byte[]> innerMap = mMap.get(nameSpaceName);
        if (innerMap == null) {
            throw new IllegalArgumentException("No such namespace '" + nameSpaceName + "'");
        }
        List<String> ret = new ArrayList<>();
        for (String dataElementName : innerMap.keySet()) {
            ret.add(dataElementName);
        }
        return ret;
    }

    /**
     * Checks if there's a value for a given data element.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return {@code true} if there's a value for the data element, {@code false} otherwise.
     */
    public boolean hasDataElement(@NonNull String nameSpaceName,
                                  @NonNull String dataElementName) {
        LinkedHashMap<String, byte[]> innerMap = mMap.get(nameSpaceName);
        if (innerMap == null) {
            return false;
        }
        return innerMap.get(dataElementName) != null;
    }

    /**
     * Gets the raw CBOR for a data element.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return the bytes of the CBOR.
     * @throws IllegalArgumentException if the given name space doesn't exist.
     * @throws IllegalArgumentException if the given data element doesn't exist.
     */
    public @NonNull byte[] getDataElement(@NonNull String nameSpaceName,
                                          @NonNull String dataElementName) {
        LinkedHashMap<String, byte[]> innerMap = mMap.get(nameSpaceName);
        if (innerMap == null) {
            throw new IllegalArgumentException("No such namespace '" + nameSpaceName + "'");
        }
        byte[] value = innerMap.get(dataElementName);
        if (value == null) {
            throw new IllegalArgumentException("No such data element '" + dataElementName + "'");
        }
        return value;
    }

    /**
     * Like {@link #getDataElement(String, String)} but decodes the CBOR as a string.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return the decoded data.
     * @throws IllegalArgumentException if the given name space doesn't exist.
     * @throws IllegalArgumentException if the given data eoement doesn't exist.
     * @throws IllegalArgumentException if the given data element isn't a string.
     */
    public @NonNull String getDataElementString(@NonNull String nameSpaceName,
                                                @NonNull String dataElementName) {
        return Util.cborDecodeString(getDataElement(nameSpaceName, dataElementName));
    }

    /**
     * Like {@link #getDataElement(String, String)} but decodes the CBOR as a byte string.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return the decoded data.
     * @throws IllegalArgumentException if the given name space doesn't exist.
     * @throws IllegalArgumentException if the given data eoement doesn't exist.
     * @throws IllegalArgumentException if the given data element isn't a byte string.
     */
    public @NonNull byte[] getDataElementByteString(@NonNull String nameSpaceName,
                                                    @NonNull String dataElementName) {
        return Util.cborDecodeByteString(getDataElement(nameSpaceName, dataElementName));
    }

    /**
     * Like {@link #getDataElement(String, String)} but decodes the CBOR as a number.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return the decoded data.
     * @throws IllegalArgumentException if the given name space doesn't exist.
     * @throws IllegalArgumentException if the given data eoement doesn't exist.
     * @throws IllegalArgumentException if the given data element isn't a number.
     */
    public long getDataElementNumber(@NonNull String nameSpaceName,
                                     @NonNull String dataElementName) {
        return Util.cborDecodeLong(getDataElement(nameSpaceName, dataElementName));
    }

    /**
     * Like {@link #getDataElement(String, String)} but decodes the CBOR as a boolean.
     *
     * @param nameSpaceName the name space name.
     * @param dataElementName the data element name.
     * @return the decoded data.
     * @throws IllegalArgumentException if the given name space doesn't exist.
     * @throws IllegalArgumentException if the given data eoement doesn't exist.
     * @throws IllegalArgumentException if the given data element isn't a boolean.
     */
    public boolean getDataElementBoolean(@NonNull String nameSpaceName,
                                         @NonNull String dataElementName) {
        return Util.cborDecodeBoolean(getDataElement(nameSpaceName, dataElementName));
    }

    static NameSpacedData fromCbor(@NonNull DataItem dataItem) {
        LinkedHashMap<String, LinkedHashMap<String, byte[]>> ret = new LinkedHashMap<>();
        if (!(dataItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("dataItem is not a map");
        }
        for (DataItem nameSpaceNameItem : ((co.nstant.in.cbor.model.Map) dataItem).getKeys()) {
            if (!(nameSpaceNameItem instanceof UnicodeString)) {
                throw new IllegalArgumentException("Expected string for namespace name");
            }
            String namespaceName = ((UnicodeString) nameSpaceNameItem).getString();
            LinkedHashMap<String, byte[]> dataElementToValueMap = new LinkedHashMap<>();
            DataItem dataElementItems = Util.cborMapExtractMap(dataItem, namespaceName);
            if (!(dataElementItems instanceof co.nstant.in.cbor.model.Map)) {
                throw new IllegalArgumentException("Expected map");
            }
            for (DataItem dataElementNameItem : ((co.nstant.in.cbor.model.Map) dataElementItems).getKeys()) {
                if (!(dataElementNameItem instanceof UnicodeString)) {
                    throw new IllegalArgumentException("Expected string for data element name");
                }
                String dataElementName = ((UnicodeString) dataElementNameItem).getString();
                DataItem valueItem = ((co.nstant.in.cbor.model.Map) dataElementItems).get(dataElementNameItem);
                if (!(valueItem instanceof ByteString)) {
                    throw new IllegalArgumentException("Expected bytestring for data element value");
                }
                byte[] value = ((ByteString) valueItem).getBytes();
                dataElementToValueMap.put(dataElementName, value);
            }
            ret.put(namespaceName, dataElementToValueMap);
        }
        return new NameSpacedData(ret);
    }

    DataItem toCbor() {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        for (String namespaceName : mMap.keySet()) {
            MapBuilder<MapBuilder<CborBuilder>> innerMapBuilder = mapBuilder.putMap(namespaceName);
            LinkedHashMap<String, byte[]> namespace = mMap.get(namespaceName);
            for (String dataElementName : namespace.keySet()) {
                byte[] dataElementValue = namespace.get(dataElementName);
                ByteString taggedBstr = new ByteString(dataElementValue);
                taggedBstr.setTag(new Tag(24));
                innerMapBuilder.put(new UnicodeString(dataElementName), taggedBstr);
            }
        }
        return builder.build().get(0);
    }

    /**
     * Encodes the given {@link NameSpacedData} as CBOR.
     *
     * <p>The encoding uses the following CDDL:
     * <pre>
     *     NameSpacedCbor = {
     *         + NameSpaceName =&gt; DataElements
     *     }
     *
     *     NameSpaceName = tstr
     *
     *     DataElements = [
     *         + DataElementName =&gt; DataElementValueBytes
     *     ]
     *
     *     DataElementName = tstr
     *     DataElementValue = any
     *     DataElementValueBytes = #6.24(bstr .cbor DataElementValue)
     * </pre>
     *
     * <p>Here's an example of CBOR conforming to the above CDDL printed in diagnostic form:
     * <pre>
     *     {
     *         "org.iso.18013.5.1" : {
     *             "given_name" : 24(&lt;&lt; "Erika" &gt;&gt;),
     *             "family_name" : 24(&lt;&lt; "Mustermann" &gt;&gt;),
     *         },
     *         "org.iso.18013.5.1.aamva" : {
     *             "organ_donor" : 24(&lt;&lt; 1 &gt;&gt;)
     *         }
     *     }
     * </pre>
     *
     * <p>Name spaces and data elements will be in the order they were inserted using
     * at construction time, either using the {@link Builder} or
     * {@link #fromEncodedCbor(byte[])}).
     *
     * @return the bytes of the encoding describe above.
     */
    public @NonNull byte[] encodeAsCbor() {
        return Util.cborEncode(toCbor());
    }

    /**
     * A builder for {@link NameSpacedData}.
     */
    public static class Builder {
        LinkedHashMap<String, LinkedHashMap<String, byte[]>> mMap = new LinkedHashMap<>();

        /**
         * Constructor.
         */
        public Builder() {}

        /**
         * Adds a raw CBOR value to the builder.
         *
         * <p>For performance-reasons the passed in value isn't validated and it's the
         * responsibility of the application to perform this check if for example operating
         * on untrusted data.
         *
         * @param nameSpaceName the name space name.
         * @param dataElementName the data element name.
         * @param value the bytes of the CBOR.
         * @return the builder.
         */
        public @NonNull Builder putEntry(@NonNull String nameSpaceName,
                                         @NonNull String dataElementName,
                                         @NonNull byte[] value) {
            LinkedHashMap<String, byte[]> innerMap = mMap.get(nameSpaceName);
            if (innerMap == null) {
                innerMap = new LinkedHashMap<>();
                mMap.put(nameSpaceName, innerMap);
            }
            innerMap.put(dataElementName, value);
            return this;
        }

        /**
         * Encode the given value as {@code tstr} CBOR and adds it to the builder.
         *
         * @param nameSpaceName the name space name.
         * @param dataElementName the data element name.
         * @param value A string.
         * @return the builder.
         */
        public @NonNull Builder putEntryString(@NonNull String nameSpaceName,
                                               @NonNull String dataElementName,
                                               @NonNull String value) {
            return putEntry(nameSpaceName, dataElementName, Util.cborEncodeString(value));
        }

        /**
         * Encode the given value as {@code bstr} CBOR and adds it to the builder.
         *
         * @param nameSpaceName the name space name.
         * @param dataElementName the data element name.
         * @param value A byte string.
         * @return the builder.
         */
        public @NonNull Builder putEntryByteString(@NonNull String nameSpaceName,
                                                   @NonNull String dataElementName,
                                                   @NonNull byte[] value) {
            return putEntry(nameSpaceName, dataElementName, Util.cborEncodeBytestring(value));
        }

        /**
         * Encode the given value as an integer or unsigned integer and adds it to the builder.
         *
         * @param nameSpaceName the name space name.
         * @param dataElementName the data element name.
         * @param value The value, as a {@code long}.
         * @return the builder.
         */
        public @NonNull Builder putEntryNumber(@NonNull String nameSpaceName,
                                               @NonNull String dataElementName,
                                               long value) {
            return putEntry(nameSpaceName, dataElementName, Util.cborEncodeNumber(value));
        }

        /**
         * Encode the given value as a boolean and adds it to the builder.
         *
         * @param nameSpaceName the name space name.
         * @param dataElementName the data element name.
         * @param value The value, as a {@code boolean}..
         * @return the builder.
         */
        public @NonNull Builder putEntryBoolean(@NonNull String nameSpaceName,
                                                @NonNull String dataElementName,
                                                boolean value) {
            return putEntry(nameSpaceName, dataElementName, Util.cborEncodeBoolean(value));
        }

        /**
         * Builds a {@link NameSpacedData} from the builder
         *
         * @return a {@link NameSpacedData} instance.
         */
        public @NonNull NameSpacedData build() {
            return new NameSpacedData(mMap);
        }
    }
}
