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

package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Helper class for building <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
 */
public final class DeviceResponseGenerator {

    private final ArrayBuilder<CborBuilder> mDocumentsBuilder;
    @Constants.DeviceResponseStatus private final long mStatusCode;

    /**
     * Creates a new {@link DeviceResponseGenerator}.
     *
     * @param statusCode the status code to use which must be one of
     * {@link Constants#DEVICE_RESPONSE_STATUS_OK},
     * {@link Constants#DEVICE_RESPONSE_STATUS_GENERAL_ERROR},
     * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR}, or
     * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR}.
     */
    public DeviceResponseGenerator(@Constants.DeviceResponseStatus long statusCode) {
        mStatusCode = statusCode;
        mDocumentsBuilder = new CborBuilder().addArray();
    }

    /**
     * Adds a new document to the device response.
     *
     * <p>Issuer-signed data is provided in <code>issuerSignedData</code> which
     * maps from namespaces into a list of bytes of IssuerSignedItem CBOR as
     * defined in 18013-5 where each contains the digest-id, element name,
     * issuer-generated random value and finally the element value. Each IssuerSignedItem
     * must be encoded so the digest of them in a #6.24 bstr matches with the digests in
     * the <code>MobileSecurityObject</code> in the <code>issuerAuth</code> parameter.
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
     * @param issuerSignedData the map described above.
     * @param errors a map with errors as described above.
     * @param encodedIssuerAuth the bytes of the <code>COSE_Sign1</code> described above.
     * @return the passed-in {@link DeviceResponseGenerator}.
     */
    public @NonNull DeviceResponseGenerator addDocument(@NonNull String docType,
            @NonNull byte[] encodedDeviceNamespaces,
            @Nullable byte[] encodedDeviceSignature,
            @Nullable byte[] encodedDeviceMac,
            @NonNull Map<String, List<byte[]>> issuerSignedData,
            @Nullable Map<String, Map<String, Long>> errors,
            @NonNull byte[] encodedIssuerAuth) {

        CborBuilder issuerNameSpacesBuilder = new CborBuilder();
        MapBuilder<CborBuilder> insOuter = issuerNameSpacesBuilder.addMap();
        for (String ns : issuerSignedData.keySet()) {
            ArrayBuilder<MapBuilder<CborBuilder>> insInner = insOuter.putArray(ns);
            for (byte[] encodedIssuerSignedItem : issuerSignedData.get(ns)) {
                // We'll do the #6.24 wrapping here.
                insInner.add(Util.cborBuildTaggedByteString(encodedIssuerSignedItem));
            }
            insInner.end();
        }
        insOuter.end();

        DataItem issuerSigned = new CborBuilder()
                .addMap()
                .put(new UnicodeString("nameSpaces"), issuerNameSpacesBuilder.build().get(0))
                .put(new UnicodeString("issuerAuth"), Util.cborDecode(encodedIssuerAuth))
                .end()
                .build().get(0);

        String deviceAuthType = "";
        DataItem deviceAuthDataItem = null;
        if (encodedDeviceSignature != null && encodedDeviceMac != null) {
            throw new IllegalArgumentException("Cannot specify both Signature and MAC");
        } else if (encodedDeviceSignature != null) {
            deviceAuthType = "deviceSignature";
            deviceAuthDataItem = Util.cborDecode(encodedDeviceSignature);
        } else if (encodedDeviceMac != null) {
            deviceAuthType = "deviceMac";
            deviceAuthDataItem = Util.cborDecode(encodedDeviceMac);
        } else {
            throw new IllegalArgumentException("No authentication mechanism used");
        }

        DataItem deviceSigned = new CborBuilder()
                .addMap()
                .put(new UnicodeString("nameSpaces"),
                        Util.cborBuildTaggedByteString(encodedDeviceNamespaces))
                .putMap("deviceAuth")
                .put(new UnicodeString(deviceAuthType), deviceAuthDataItem)
                .end()
                .end()
                .build().get(0);

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        mapBuilder.put("docType", docType);
        mapBuilder.put(new UnicodeString("issuerSigned"), issuerSigned);
        mapBuilder.put(new UnicodeString("deviceSigned"), deviceSigned);
        if (errors != null) {
            CborBuilder errorsBuilder = new CborBuilder();
            MapBuilder<CborBuilder> errorsOuterMapBuilder = errorsBuilder.addMap();
            for (String namespaceName : errors.keySet()) {
                MapBuilder<MapBuilder<CborBuilder>> errorsInnerMapBuilder =
                        errorsOuterMapBuilder.putMap(namespaceName);
                Map<String, Long> innerMap = errors.get(namespaceName);
                for (String dataElementName : innerMap.keySet()) {
                    long value = innerMap.get(dataElementName).longValue();
                    errorsInnerMapBuilder.put(dataElementName, value);
                }
            }
            mapBuilder.put(new UnicodeString("errors"), errorsBuilder.build().get(0));
        }
        mDocumentsBuilder.add(builder.build().get(0));
        return this;
    }

    /**
     * Like {@link #addDocument(String, byte[], byte[], byte[], Map, Map, byte[])} but takes a
     * {@link CredentialDataResult} instead and merges the results into the "elementValue"
     * entry of each IssuerSignedItem value.
     *
     * <p>Note: The <code>issuerSignedData</code> and <code>encodedIssuerAuth</code> are
     * parameters usually obtained via {@link Utility#decodeStaticAuthData(byte[])}.
     *
     * @param docType              The type of the document to send.
     * @param credentialDataResult The device- and issuer-signed data elements to include.
     * @param issuerSignedMapping A mapping from namespaces to an array of IssuerSignedItem
     *                            CBOR for the namespace. The "elementValue" value in each
     *                            IssuerSignedItem CBOR must be set to the NULL value.
     * @param encodedIssuerAuth   the bytes of <code>COSE_Sign1</code> signed by the issuing
     *                            authority and where the payload is set to
     *                            <code>MobileSecurityObjectBytes</code>.
     * @return                    the generator.
     */
    public @NonNull DeviceResponseGenerator addDocument(@NonNull String docType,
            @NonNull CredentialDataResult credentialDataResult,
            @NonNull Map<String, List<byte[]>> issuerSignedMapping,
            @Nullable Map<String, Map<String, Long>> errors,
            @NonNull byte[] encodedIssuerAuth) {

        Map<String, List<byte[]>> issuerSignedMappingWithData =
                Utility.mergeIssuerSigned(issuerSignedMapping,
                        credentialDataResult.getIssuerSignedEntries());

        addDocument(docType,
                credentialDataResult.getDeviceNameSpaces(),
                credentialDataResult.getDeviceSignature(),
                credentialDataResult.getDeviceMac(),
                issuerSignedMappingWithData,
                errors,
                encodedIssuerAuth);

        return this;
    }


    /**
     * Builds the <code>DeviceResponse</code> CBOR.
     *
     * @return the bytes of <code>DeviceResponse</code> CBOR.
     */
    public @NonNull byte[] generate() {
        CborBuilder deviceResponseBuilder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = deviceResponseBuilder.addMap();
        mapBuilder.put("version", "1.0");
        mapBuilder.put(new UnicodeString("documents"), mDocumentsBuilder.end().build().get(0));
        // TODO: The documentErrors map entry should only be present if there is a non-zero
        //  number of elements in the array. Right now we don't have a way for the application
        //  to convey document errors but when we add that API we'll need to do something so
        //  it is included here.
        mapBuilder.put("status", mStatusCode);
        mapBuilder.end();

        return Util.cborEncode(deviceResponseBuilder.build().get(0));
    }
}
