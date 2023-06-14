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

import com.android.identity.credential.NameSpacedData;
import com.android.identity.internal.Util;
import com.android.identity.keystore.KeystoreEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

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
     * <p>At least one of {@link #setDeviceNamespacesSignature(NameSpacedData, KeystoreEngine, String, KeystoreEngine.KeyUnlockData, int)}
     * or {@link #setDeviceNamespacesMac(NameSpacedData, KeystoreEngine, String, KeystoreEngine.KeyUnlockData, PublicKey)}
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
                                          @NonNull KeystoreEngine keystoreEngine,
                                          @NonNull String keyAlias,
                                          @Nullable KeystoreEngine.KeyUnlockData keyUnlockData,
                                          @KeystoreEngine.Algorithm int signatureAlgorithm,
                                          @Nullable PublicKey eReaderKey)
            throws KeystoreEngine.KeyLockedException {

        CborBuilder deviceNameSpacesBuilder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = deviceNameSpacesBuilder.addMap();
        for (String nameSpaceName : dataElements.getNameSpaceNames()) {
            MapBuilder<MapBuilder<CborBuilder>> nsBuilder = mapBuilder.putMap(nameSpaceName);
            for (String dataElementName : dataElements.getDataElementNames(nameSpaceName)) {
                nsBuilder.put(
                        new UnicodeString(dataElementName),
                        Util.cborDecode(dataElements.getDataElement(nameSpaceName, dataElementName)));
            }
        }
        mapBuilder.end();
        byte[] encodedDeviceNameSpaces = Util.cborEncode(deviceNameSpacesBuilder.build().get(0));

        byte[] deviceAuthentication = Util.cborEncode(new CborBuilder()
                .addArray()
                .add("DeviceAuthentication")
                .add(Util.cborDecode(mEncodedSessionTranscript))
                .add(mDocType)
                .add(Util.cborBuildTaggedByteString(encodedDeviceNameSpaces))
                .end()
                .build().get(0));

        byte[] deviceAuthenticationBytes =
                Util.cborEncode(Util.cborBuildTaggedByteString(deviceAuthentication));

        byte[] encodedDeviceSignature = null;
        byte[] encodedDeviceMac = null;
        if (signatureAlgorithm != KeystoreEngine.ALGORITHM_UNSET) {
            encodedDeviceSignature = Util.cborEncode(Util.coseSign1Sign(
                    keystoreEngine,
                    keyAlias,
                    signatureAlgorithm,
                    keyUnlockData,
                    null,
                    deviceAuthenticationBytes,
                    null));
        } else {
            byte[] sharedSecret = keystoreEngine
                    .keyAgreement(keyAlias,
                            eReaderKey,
                            keyUnlockData);

            byte[] sessionTranscriptBytes =
                    Util.cborEncode(Util.cborBuildTaggedByteString(mEncodedSessionTranscript));

            byte[] salt;
            try {
                salt = MessageDigest.getInstance("SHA-256").digest(sessionTranscriptBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Unexpected exception", e);
            }
            byte[] info = "EMacKey".getBytes(StandardCharsets.UTF_8);
            byte[] derivedKey = Util.computeHkdf("HmacSha256", sharedSecret, salt, info, 32);
            SecretKey secretKey = new SecretKeySpec(derivedKey, "");

            encodedDeviceMac = Util.cborEncode(
                    Util.coseMac0(secretKey,
                            new byte[0],                 // payload
                            deviceAuthenticationBytes));  // detached content
        }

        String deviceAuthType = "";
        DataItem deviceAuthDataItem = null;
        if (encodedDeviceSignature != null) {
            deviceAuthType = "deviceSignature";
            deviceAuthDataItem = Util.cborDecode(encodedDeviceSignature);
        } else {
            deviceAuthType = "deviceMac";
            deviceAuthDataItem = Util.cborDecode(encodedDeviceMac);
        }

        mDeviceSigned = new CborBuilder()
                .addMap()
                .put(new UnicodeString("nameSpaces"),
                        Util.cborBuildTaggedByteString(encodedDeviceNameSpaces))
                .putMap("deviceAuth")
                .put(new UnicodeString(deviceAuthType), deviceAuthDataItem)
                .end()
                .end()
                .build().get(0);
        return this;
    }

    /**
     * Sets device-signed data elements to return.
     *
     * <p>This variant produces an EC signature as per ISO/IEC 18013-5:2021 section 9.1.3.6
     * mdoc ECDSA / EdDSA Authentication.
     *
     * @param dataElements the data elements to return in {@code DeviceSigned}.
     * @param keystoreEngine the {@link KeystoreEngine} for the authentication key to sign with.
     * @param keyAlias the alias for the authentication key to sign with.
     * @param keyUnlockData unlock data for the authentication key, or {@code null}.
     * @param signatureAlgorithm the signature algorithm to use.
     * @return the generator.
     * @throws KeystoreEngine.KeyLockedException if the authentication key is locked.
     */
    public @NonNull
    DocumentGenerator setDeviceNamespacesSignature(@NonNull NameSpacedData dataElements,
                                                   @NonNull KeystoreEngine keystoreEngine,
                                                   @NonNull String keyAlias,
                                                   @Nullable KeystoreEngine.KeyUnlockData keyUnlockData,
                                                   @KeystoreEngine.Algorithm int signatureAlgorithm)
            throws KeystoreEngine.KeyLockedException {
        return setDeviceNamespaces(dataElements,
                keystoreEngine,
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
     * @param keystoreEngine the {@link KeystoreEngine} for the authentication key to sign with.
     * @param keyAlias the alias for the authentication key to sign with.
     * @param keyUnlockData unlock data for the authentication key, or {@code null}.
     * @param eReaderKey the ephemeral public key used by the remote reader.
     * @return the generator.
     * @throws KeystoreEngine.KeyLockedException if the authentication key is locked.
     */
    public @NonNull
    DocumentGenerator setDeviceNamespacesMac(@NonNull NameSpacedData dataElements,
                                             @NonNull KeystoreEngine keystoreEngine,
                                             @NonNull String keyAlias,
                                             @Nullable KeystoreEngine.KeyUnlockData keyUnlockData,
                                             @NonNull PublicKey eReaderKey)
            throws KeystoreEngine.KeyLockedException {
        return setDeviceNamespaces(dataElements,
                keystoreEngine,
                keyAlias,
                keyUnlockData,
                KeystoreEngine.ALGORITHM_UNSET,
                eReaderKey);
    }

    /**
     * Generates CBOR.
     *
     * This generates the bytes of the {@code Document} CBOR according to ISO/IEC 18013-5:2021
     * section 8.3.2.1.2.2.
     *
     * @return the bytes described above.
     * @throws IllegalStateException if one of {@link #setDeviceNamespacesSignature(NameSpacedData, KeystoreEngine, String, KeystoreEngine.KeyUnlockData, int)}
     *   or {@link #setDeviceNamespacesMac(NameSpacedData, KeystoreEngine, String, KeystoreEngine.KeyUnlockData, PublicKey)} hasn't been called on the generator.
     */
    public @NonNull
    byte[] generate() {
        if (mDeviceSigned == null) {
            throw new IllegalStateException("DeviceSigned isn't set");
        }

        CborBuilder issuerNameSpacesBuilder = null;
        if (mIssuerNamespaces != null) {
            issuerNameSpacesBuilder = new CborBuilder();
            MapBuilder<CborBuilder> insOuter = issuerNameSpacesBuilder.addMap();
            for (String ns : mIssuerNamespaces.keySet()) {
                ArrayBuilder<MapBuilder<CborBuilder>> insInner = insOuter.putArray(ns);
                for (byte[] encodedIssuerSignedItemBytes : mIssuerNamespaces.get(ns)) {
                    insInner.add(Util.cborDecode(encodedIssuerSignedItemBytes));
                }
                insInner.end();
            }
            insOuter.end();
        }

        CborBuilder issuerSignedBuilder = new CborBuilder();
        MapBuilder<CborBuilder> issuerSignedMapBuilder = issuerSignedBuilder.addMap();
        if (issuerNameSpacesBuilder != null) {
            issuerSignedMapBuilder.put(new UnicodeString("nameSpaces"), issuerNameSpacesBuilder.build().get(0));
        }
        issuerSignedMapBuilder.put(new UnicodeString("issuerAuth"), Util.cborDecode(mEncodedIssuerAuth));
        issuerSignedMapBuilder.end();
        DataItem issuerSigned = issuerSignedBuilder.build().get(0);

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        mapBuilder.put("docType", mDocType);
        mapBuilder.put(new UnicodeString("issuerSigned"), issuerSigned);
        mapBuilder.put(new UnicodeString("deviceSigned"), mDeviceSigned);
        if (mErrors != null) {
            CborBuilder errorsBuilder = new CborBuilder();
            MapBuilder<CborBuilder> errorsOuterMapBuilder = errorsBuilder.addMap();
            for (String namespaceName : mErrors.keySet()) {
                MapBuilder<MapBuilder<CborBuilder>> errorsInnerMapBuilder =
                        errorsOuterMapBuilder.putMap(namespaceName);
                Map<String, Long> innerMap = mErrors.get(namespaceName);
                for (String dataElementName : innerMap.keySet()) {
                    long value = innerMap.get(dataElementName);
                    errorsInnerMapBuilder.put(dataElementName, value);
                }
            }
            mapBuilder.put(new UnicodeString("errors"), errorsBuilder.build().get(0));
        }

        return Util.cborEncode(builder.build().get(0));
    }
}
