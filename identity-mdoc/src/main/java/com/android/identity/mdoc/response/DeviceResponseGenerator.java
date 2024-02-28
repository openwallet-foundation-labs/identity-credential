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

package com.android.identity.mdoc.response;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.cbor.ArrayBuilder;
import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborArray;
import com.android.identity.cbor.CborBuilder;
import com.android.identity.cbor.CborMap;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.MapBuilder;
import com.android.identity.cbor.RawCbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.util.Constants;

import java.util.List;
import java.util.Map;

/**
 * Helper class for building <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
 */
public final class DeviceResponseGenerator {
    private static final String TAG = "DeviceResponseGenerator";
    private final ArrayBuilder<CborBuilder> mDocumentsBuilder;
    private final long mStatusCode;

    /**
     * Creates a new {@link DeviceResponseGenerator}.
     *
     * @param statusCode the status code to use which must be one of
     * {@link Constants#DEVICE_RESPONSE_STATUS_OK},
     * {@link Constants#DEVICE_RESPONSE_STATUS_GENERAL_ERROR},
     * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR}, or
     * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR}.
     */
    public DeviceResponseGenerator(long statusCode) {
        mStatusCode = statusCode;
        mDocumentsBuilder = CborArray.Companion.builder();
    }

    /**
     * Adds a new document to the device response.
     *
     * <p>Issuer-signed data is provided in <code>issuerNameSpaces</code> which
     * maps from namespaces into a list of bytes of IssuerSignedItemBytes CBOR as
     * defined in 18013-5 where each contains the digest-id, element name,
     * issuer-generated random value and finally the element value. Each IssuerSignedItemBytes
     * must be encoded so its digest matches with the digest in the
     * <code>MobileSecurityObject</code> in the <code>issuerAuth</code> parameter.
     *
     * <p>The <code>encodedIssuerAuth</code> parameter contains the bytes of the
     * <code>IssuerAuth</code> CBOR as defined in <em>ISO/IEC 18013-5</em>
     * section 9.1.2.4 <em>Signing method and structure for MSO</em>. That is,
     * the payload for this <code>COSE_Sign1</code> must be set to the
     * <code>MobileSecurityObjectBytes</code> and the public key used to
     * sign the payload must be included in a <code>x5chain</code> unprotected
     * header element.
     *
     * <p>For device-signed data, the parameters <code>encodedDeviceNamespaces</code>,
     * <code>encodedDeviceSignature</code>, and <code>encodedDeviceMac</code> are
     * used. Of the latter two, exactly one of them must be non-<code>null</code>.
     * The <code>DeviceNameSpaces</code> CBOR specified in <em>ISO/IEC 18013-5</em>
     * section 8.3.2.1 <em>Device retrieval</em> is to be set in
     * <code>encodedDeviceNamespaces</code>, and either a ECDSA signature or a MAC
     * over the <code>DeviceAuthentication</code> CBOR as defined in section 9.1.3
     * <em>mdoc authentication</em> should be set in <code>encodedDeviceSignature</code>
     * or <code>encodedDeviceMac</code> respectively. Values for all parameters can be
     * obtained from the <code>ResultData</code> class from either the Framework
     * or this library.
     *
     * <p>If present, the <code>errors</code> parameter is a map from namespaces where each
     * value is a map from data elements in said namespace to an error code from
     * ISO/IEC 18013-5:2021 Table 9.
     *
     * @param docType the document type, for example <code>org.iso.18013.5.1.mDL</code>.
     * @param encodedDeviceNamespaces bytes of the <code>DeviceNameSpaces</code> CBOR.
     * @param encodedDeviceSignature bytes of a COSE_Sign1 for authenticating the device data.
     * @param encodedDeviceMac bytes of a COSE_Mac0 for authenticating the device data.
     * @param issuerNameSpaces the map described above.
     * @param errors a map with errors as described above.
     * @param encodedIssuerAuth the bytes of the <code>COSE_Sign1</code> described above.
     * @return the passed-in {@link DeviceResponseGenerator}.
     */
    public @NonNull DeviceResponseGenerator addDocument(@NonNull String docType,
            @NonNull byte[] encodedDeviceNamespaces,
            @Nullable byte[] encodedDeviceSignature,
            @Nullable byte[] encodedDeviceMac,
            @NonNull Map<String, List<byte[]>> issuerNameSpaces,
            @Nullable Map<String, Map<String, Long>> errors,
            @NonNull byte[] encodedIssuerAuth) {

        MapBuilder<CborBuilder> insOuter = CborMap.Companion.builder();
        for (String ns : issuerNameSpaces.keySet()) {
            ArrayBuilder<MapBuilder<CborBuilder>> insInner = insOuter.putArray(ns);
            for (byte[] encodedIssuerSignedItemBytes : issuerNameSpaces.get(ns)) {
                insInner.add(new RawCbor(encodedIssuerSignedItemBytes));
            }
            insInner.end();
        }

        DataItem issuerSigned = CborMap.Companion.builder()
                .put("nameSpaces", insOuter.end().build())
                .put("issuerAuth", new RawCbor(encodedIssuerAuth))
                .end()
                .build();

        String deviceAuthType = "";
        byte[] deviceAuth = null;
        if (encodedDeviceSignature != null && encodedDeviceMac != null) {
            throw new IllegalArgumentException("Cannot specify both Signature and MAC");
        } else if (encodedDeviceSignature != null) {
            deviceAuthType = "deviceSignature";
            deviceAuth = encodedDeviceSignature;
        } else if (encodedDeviceMac != null) {
            deviceAuthType = "deviceMac";
            deviceAuth = encodedDeviceMac;
        } else {
            throw new IllegalArgumentException("No authentication mechanism used");
        }

        DataItem deviceSigned = CborMap.Companion.builder()
                .put("nameSpaces", new Tagged(24, new Bstr(encodedDeviceNamespaces)))
                .putMap("deviceAuth")
                .put(deviceAuthType, new RawCbor(deviceAuth))
                .end()
                .end()
                .build();

        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        mapBuilder.put("docType", docType);
        mapBuilder.put("issuerSigned", issuerSigned);
        mapBuilder.put("deviceSigned", deviceSigned);
        if (errors != null) {
            MapBuilder<CborBuilder> errorsOuterMapBuilder = CborMap.Companion.builder();
            for (String namespaceName : errors.keySet()) {
                MapBuilder<MapBuilder<CborBuilder>> errorsInnerMapBuilder =
                        errorsOuterMapBuilder.putMap(namespaceName);
                Map<String, Long> innerMap = errors.get(namespaceName);
                for (String dataElementName : innerMap.keySet()) {
                    long value = innerMap.get(dataElementName);
                    errorsInnerMapBuilder.put(dataElementName, value);
                }
            }
            mapBuilder.put("errors", errorsOuterMapBuilder.end().build());
        }
        mDocumentsBuilder.add(mapBuilder.end().build());
        return this;
    }

    /**
     * Adds a new document to the device response.
     *
     * This can be used with the output {@link DocumentGenerator} for MDOC presentations.
     *
     * @param encodedDocument the bytes of {@code Document} CBOR as defined in ISO/IEC
     *                        18013-5 section 8.3.2.1.2.2.
     * @return the generator.
     */
    public @NonNull DeviceResponseGenerator addDocument(@NonNull byte[] encodedDocument) {
        mDocumentsBuilder.add(Cbor.decode(encodedDocument));
        return this;
    }

    /**
     * Builds the <code>DeviceResponse</code> CBOR.
     *
     * @return the bytes of <code>DeviceResponse</code> CBOR.
     */
    public @NonNull byte[] generate() {
        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        mapBuilder.put("version", "1.0");
        mapBuilder.put("documents", mDocumentsBuilder.end().build());
        // TODO: The documentErrors map entry should only be present if there is a non-zero
        //  number of elements in the array. Right now we don't have a way for the application
        //  to convey document errors but when we add that API we'll need to do something so
        //  it is included here.
        mapBuilder.put("status", mStatusCode);
        mapBuilder.end();

        return Cbor.encode(mapBuilder.end().build());
    }
}
