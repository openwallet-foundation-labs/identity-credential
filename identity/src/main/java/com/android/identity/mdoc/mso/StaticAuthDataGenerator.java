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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Helper class for building <code>StaticAuthData</code> <a href="http://cbor.io/">CBOR</a> with
 * the following CDDL:
 * <pre>
 *     StaticAuthData = {
 *         "digestIdMapping": DigestIdMapping,
 *         "issuerAuth" : IssuerAuth
 *     }
 *
 *     DigestIdMapping = {
 *         NameSpace =&gt; [ + IssuerSignedItemMetadataBytes ]
 *     }
 *
 *     IssuerSignedItemMetadataBytes = #6.24(bstr .cbor IssuerSignedItemMetadata)
 *
 *     IssuerSignedItemMetadata = {
 *       "digestID" : uint,                           ; Digest ID for issuer data auth
 *       "random" : bstr,                             ; Random value for issuer data auth
 *       "elementIdentifier" : DataElementIdentifier, ; Data element identifier
 *       "elementValue" : DataElementValueOrNull      ; Placeholder for Data element value
 *     }
 *
 *     ; Set to null to use value previously provisioned or non-null
 *     ; to use a per-MSO value
 *     ;
 *     DataElementValueOrNull = null // DataElementValue   ; "//" means or in CDDL
 *
 *     ; Defined in ISO 18013-5
 *     ;
 *     NameSpace = String
 *     DataElementIdentifier = String
 *     DataElementValue = any
 *     DigestID = uint
 *     IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
 * </pre>
 *
 * <p>Note that IssuerSignedItemMetadata is similar to IssuerSignedItem as defined in ISO 18013-5
 * with the exception that the "elementValue" is NULL.
 */
public class StaticAuthDataGenerator {

    private Map<String, List<byte[]>> mDigestIDMapping = new HashMap<>();
    private byte[] mEncodedIssuerAuth;

    /**
     * Constructs a new {@link StaticAuthDataGenerator}.
     *
     * @param digestIDMapping A non-empty mapping between a <code>Namespace</code> and a list of
     *                        <code>IssuerSignedItemMetadataBytes</code>.
     * @param encodedIssuerAuth A COSE_Sign1 object with a payload of MobileSecurityObjectBytes.
     * @exception IllegalArgumentException if the <code>digestIDMapping</code> is empty.
     */
    public StaticAuthDataGenerator(@NonNull Map<String, List<byte[]>> digestIDMapping,
                                   @NonNull byte[] encodedIssuerAuth) {
        if (digestIDMapping.isEmpty()) {
            throw new IllegalArgumentException("digestIDs must not be empty");
        }
        mDigestIDMapping = digestIDMapping;
        mEncodedIssuerAuth = encodedIssuerAuth;
    }

    /**
     * Builds the <code>StaticAuthData</code> CBOR.
     *
     * @return the bytes of <code>StaticAuthData</code> CBOR.
     */
    @NonNull
    public byte[] generate() {
        CborBuilder digestIdBuilder = new CborBuilder();
        MapBuilder<CborBuilder> outerBuilder = digestIdBuilder.addMap();
        for (String namespace : mDigestIDMapping.keySet()) {
            ArrayBuilder<MapBuilder<CborBuilder>> innerBuilder = outerBuilder.putArray(namespace);

            for (byte[] encodedIssuerSignedItemMetadata : mDigestIDMapping.get(namespace)) {
                innerBuilder.add(Util.cborDecode(encodedIssuerSignedItemMetadata));
            }
        }
        DataItem digestIdMappingItem = digestIdBuilder.build().get(0);

        byte[] staticAuthData = Util.cborEncode(new CborBuilder()
                .addMap()
                .put(new UnicodeString("digestIdMapping"), digestIdMappingItem)
                .put(new UnicodeString("issuerAuth"), Util.cborDecode(mEncodedIssuerAuth))
                .end()
                .build().get(0));
        return staticAuthData;
    }

}
