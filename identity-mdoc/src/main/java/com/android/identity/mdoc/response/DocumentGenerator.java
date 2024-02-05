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
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.MapBuilder;
import com.android.identity.cbor.RawCbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.cose.Cose;
import com.android.identity.cose.CoseNumberLabel;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.crypto.Algorithm;
import com.android.identity.securearea.KeyLockedException;
import com.android.identity.securearea.KeyUnlockData;
import com.android.identity.securearea.SecureArea;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Helper class for building <code>Document</code> <a href="http://cbor.io/">CBOR</a>
 * as specified in ISO/IEC 18013-5:2021 section 8.3.
 */
public class DocumentGenerator {
    private static final String TAG = "DocumentGenerator";

    private final String mDocType;
    private final byte[] mEncodedIssuerAuth;
    private final byte[] mEncodedSessionTranscript;
    private Map<String, Map<String, Long>> mErrors;
    private Map<String, List<byte[]>> mIssuerNamespaces;
    private DataItem mDeviceSigned;

    /**
     * Creates a new {@link DocumentGenerator}.
     *
     * <p>At least one of {@link #setDeviceNamespacesSignature(NameSpacedData, SecureArea, String, KeyUnlockData, Algorithm)}
     * or {@link #setDeviceNamespacesMac(NameSpacedData, SecureArea, String, KeyUnlockData, EcPublicKey)}
     * must be called before {@link #generate()} is called.
     *
     * <p>Issuer-signed data can be set using {@link #setIssuerNamespaces(Map)}.
     *
     * <p>Errors can be set using {@link #setErrors(Map)}.
     *
     * @param docType the document type.
     * @param encodedIssuerAuth bytes of {@code IssuerAuth} CBOR, as per ISO/IEC 18013-5:2021
     *                          section 9.1.2.4.
     * @param encodedSessionTranscript bytes of {@code SessionTranscript} CBOR as per
     *                                 ISO/IEC 18013-5:2021 section 9.1.5.1.
     */
    public DocumentGenerator(@NonNull String docType,
                             @NonNull byte[] encodedIssuerAuth,
                             @NonNull byte[] encodedSessionTranscript) {
        mDocType = docType;
        mEncodedIssuerAuth = encodedIssuerAuth;
        mEncodedSessionTranscript = encodedSessionTranscript;
    }

    /**
     * Sets document errors.
     *
     * <p>The <code>errors</code> parameter is a map from namespaces where each value is a map from
     * data elements in said namespace to an error code from ISO/IEC 18013-5:2021 Table 9.
     *
     * @param errors the map described above.
     * @return the generator.
     */
    public @NonNull
    DocumentGenerator setErrors(@NonNull Map<String, Map<String, Long>> errors) {
        mErrors = errors;
        return this;
    }

    /**
     * Sets issuer-signed data elements to return.
     *
     * <p>Since a document response may not contain issuer signed data elements, this is
     * optional to call.
     *
     * @param issuerNameSpaces a map from name spaces into a list of {@code IssuerSignedItemBytes}.
     * @return the generator.
     */
    public @NonNull
    DocumentGenerator setIssuerNamespaces(Map<String, List<byte[]>> issuerNameSpaces) {
        mIssuerNamespaces = issuerNameSpaces;
        return this;
    }

    private @NonNull
    DocumentGenerator setDeviceNamespaces(@NonNull NameSpacedData dataElements,
                                          @NonNull SecureArea secureArea,
                                          @NonNull String keyAlias,
                                          @Nullable KeyUnlockData keyUnlockData,
                                          Algorithm signatureAlgorithm,
                                          @Nullable EcPublicKey eReaderKey)
            throws KeyLockedException {

        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        for (String nameSpaceName : dataElements.getNameSpaceNames()) {
            MapBuilder<MapBuilder<CborBuilder>> nsBuilder = mapBuilder.putMap(nameSpaceName);
            for (String dataElementName : dataElements.getDataElementNames(nameSpaceName)) {
                nsBuilder.put(dataElementName, new RawCbor(dataElements.getDataElement(nameSpaceName, dataElementName)));
            }
        }
        mapBuilder.end();
        byte[] encodedDeviceNameSpaces = Cbor.encode(mapBuilder.end().build());

        byte[] deviceAuthentication = Cbor.encode(CborArray.Companion.builder()
                .add("DeviceAuthentication")
                .add(new RawCbor(mEncodedSessionTranscript))
                .add(mDocType)
                .addTaggedEncodedCbor(encodedDeviceNameSpaces)
                .end()
                .build());

        byte[] deviceAuthenticationBytes =
                Cbor.encode(new Tagged(24, new Bstr(deviceAuthentication)));

        byte[] encodedDeviceSignature = null;
        byte[] encodedDeviceMac = null;
        if (signatureAlgorithm != Algorithm.UNSET) {
            encodedDeviceSignature = Cbor.encode(
                    Cose.coseSign1Sign(
                            secureArea,
                            keyAlias,
                            deviceAuthenticationBytes,
                            true,
                            signatureAlgorithm,
                            Map.of(
                                    new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                                    DataItemExtensionsKt.getDataItem(signatureAlgorithm.getCoseAlgorithmIdentifier())
                            ),
                            Map.of(),
                            keyUnlockData).getDataItem()
            );
        } else {
            byte[] sharedSecret = secureArea.keyAgreement(keyAlias,
                    eReaderKey,
                    keyUnlockData);
            byte[] sessionTranscriptBytes =
                    Cbor.encode(new Tagged(24, new Bstr(mEncodedSessionTranscript)));
            byte[] salt = Crypto.digest(Algorithm.SHA256, sessionTranscriptBytes);
            byte[] info = "EMacKey".getBytes(StandardCharsets.UTF_8);
            byte[] eMacKey = Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32);
            encodedDeviceMac = Cbor.encode(
                    Cose.coseMac0(
                            Algorithm.HMAC_SHA256,
                            eMacKey,
                            deviceAuthenticationBytes,
                            false,
                            Map.of(
                                    new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                                    DataItemExtensionsKt.getDataItem(Algorithm.HMAC_SHA256.getCoseAlgorithmIdentifier())
                            ),
                            Map.of()).getDataItem()
            );
        }

        String deviceAuthType;
        DataItem deviceAuthDataItem;
        if (encodedDeviceSignature != null) {
            deviceAuthType = "deviceSignature";
            deviceAuthDataItem = Cbor.decode(encodedDeviceSignature);
        } else {
            deviceAuthType = "deviceMac";
            deviceAuthDataItem = Cbor.decode(encodedDeviceMac);
        }

        mDeviceSigned = CborMap.Companion.builder()
                .putTaggedEncodedCbor("nameSpaces", encodedDeviceNameSpaces)
                .putMap("deviceAuth")
                .put(deviceAuthType, deviceAuthDataItem)
                .end()
                .end()
                .build();
        return this;
    }

    /**
     * Sets device-signed data elements to return.
     *
     * <p>This variant produces an EC signature as per ISO/IEC 18013-5:2021 section 9.1.3.6
     * mdoc ECDSA / EdDSA Authentication.
     *
     * @param dataElements the data elements to return in {@code DeviceSigned}.
     * @param secureArea the {@link SecureArea} for the authentication key to sign with.
     * @param keyAlias the alias for the authentication key to sign with.
     * @param keyUnlockData unlock data for the authentication key, or {@code null}.
     * @param signatureAlgorithm the signature algorithm to use.
     * @return the generator.
     * @throws KeyLockedException if the authentication key is locked.
     */
    public @NonNull
    DocumentGenerator setDeviceNamespacesSignature(@NonNull NameSpacedData dataElements,
                                                   @NonNull SecureArea secureArea,
                                                   @NonNull String keyAlias,
                                                   @Nullable KeyUnlockData keyUnlockData,
                                                   Algorithm signatureAlgorithm)
            throws KeyLockedException {
        return setDeviceNamespaces(dataElements,
                secureArea,
                keyAlias,
                keyUnlockData,
                signatureAlgorithm,
                null);
    }

    /**
     * Sets device-signed data elements to return.
     *
     * <p>This variant produces a MAC as per ISO/IEC 18013-5:2021 section 9.1.3.5
     * mdoc MAC Authentication.
     *
     * @param dataElements the data elements to return in {@code DeviceSigned}.
     * @param secureArea the {@link SecureArea} for the authentication key to sign with.
     * @param keyAlias the alias for the authentication key to sign with.
     * @param keyUnlockData unlock data for the authentication key, or {@code null}.
     * @param eReaderKey the ephemeral public key used by the remote reader.
     * @return the generator.
     * @throws KeyLockedException if the authentication key is locked.
     */
    public @NonNull
    DocumentGenerator setDeviceNamespacesMac(@NonNull NameSpacedData dataElements,
                                             @NonNull SecureArea secureArea,
                                             @NonNull String keyAlias,
                                             @Nullable KeyUnlockData keyUnlockData,
                                             @NonNull EcPublicKey eReaderKey)
            throws KeyLockedException {
        return setDeviceNamespaces(dataElements,
                secureArea,
                keyAlias,
                keyUnlockData,
                Algorithm.UNSET,
                eReaderKey);
    }

    /**
     * Generates CBOR.
     *
     * This generates the bytes of the {@code Document} CBOR according to ISO/IEC 18013-5:2021
     * section 8.3.2.1.2.2.
     *
     * @return the bytes described above.
     * @throws IllegalStateException if one of {@link #setDeviceNamespacesSignature(NameSpacedData, SecureArea, String, KeyUnlockData, Algorithm)}
     *   or {@link #setDeviceNamespacesMac(NameSpacedData, SecureArea, String, KeyUnlockData, EcPublicKey)} hasn't been called on the generator.
     */
    public @NonNull
    byte[] generate() {
        if (mDeviceSigned == null) {
            throw new IllegalStateException("DeviceSigned isn't set");
        }

        MapBuilder<CborBuilder> issuerSignedMapBuilder = CborMap.Companion.builder();
        if (mIssuerNamespaces != null) {
            MapBuilder<CborBuilder> insOuter = CborMap.Companion.builder();
            for (String ns : mIssuerNamespaces.keySet()) {
                ArrayBuilder<MapBuilder<CborBuilder>> insInner = insOuter.putArray(ns);
                for (byte[] encodedIssuerSignedItemBytes : mIssuerNamespaces.get(ns)) {
                    insInner.add(new RawCbor(encodedIssuerSignedItemBytes));
                }
                insInner.end();
            }
            insOuter.end();
            issuerSignedMapBuilder.put("nameSpaces", insOuter.end().build());
        }

        issuerSignedMapBuilder.put("issuerAuth", new RawCbor(mEncodedIssuerAuth));
        DataItem issuerSigned = issuerSignedMapBuilder.end().build();

        MapBuilder<CborBuilder> mapBuilder = CborMap.Companion.builder();
        mapBuilder.put("docType", mDocType);
        mapBuilder.put("issuerSigned", issuerSigned);
        mapBuilder.put("deviceSigned", mDeviceSigned);
        if (mErrors != null) {
            MapBuilder<CborBuilder> errorsOuterMapBuilder = CborMap.Companion.builder();
            for (String namespaceName : mErrors.keySet()) {
                MapBuilder<MapBuilder<CborBuilder>> errorsInnerMapBuilder =
                        errorsOuterMapBuilder.putMap(namespaceName);
                Map<String, Long> innerMap = mErrors.get(namespaceName);
                for (String dataElementName : innerMap.keySet()) {
                    long value = innerMap.get(dataElementName);
                    errorsInnerMapBuilder.put(dataElementName, value);
                }
            }
            mapBuilder.put("errors", errorsOuterMapBuilder.end().build());
        }

        return Cbor.encode(mapBuilder.end().build());
    }
}
