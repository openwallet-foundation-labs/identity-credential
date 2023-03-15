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

package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.UnicodeString;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for building <code>MobileSecurityObject</code> <a href="http://cbor.io/">CBOR</a>
 * as specified <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>.
 */
public class MobileSecurityObjectGenerator {

    private final String mDigestAlgorithm;
    private final String mDocType;
    private final PublicKey mDeviceKey;
    private final int mDigestSize;

    private final CborBuilder mValueDigestsBuilder = new CborBuilder();
    private final MapBuilder<CborBuilder> mValueDigestsOuter = mValueDigestsBuilder.addMap();
    private boolean digestEmpty = true;

    private final List<String> mAuthorizedNameSpaces = new ArrayList<>();
    private final Map<String, List<String>> mAuthorizedDataElements = new HashMap<>();
    private final Map<Long, byte[]> mKeyInfo = new HashMap<>();

    private Timestamp mSigned, mValidFrom, mValidUntil, mExpectedUpdate;

    /**
     * Constructs a new {@link MobileSecurityObjectGenerator}.
     *
     * @param digestAlgorithm The digest algorithm identifier. Must be one of {"SHA-256", "SHA-384", "SHA-512"}.
     * @param docType The document type.
     * @param deviceKey The public part of the key pair used for mdoc authentication.
     * @exception IllegalArgumentException if the <code>digestAlgorithm</code> is not one of
     *                                     {"SHA-256", "SHA-384", "SHA-512"}.
     */
    public MobileSecurityObjectGenerator(@NonNull String digestAlgorithm, @NonNull String docType,
                                         @NonNull PublicKey deviceKey) {
        final List<String> allowableDigestAlgorithms = List.of("SHA-256", "SHA-384", "SHA-512");
        if (!allowableDigestAlgorithms.contains(digestAlgorithm)) {
            throw new IllegalArgumentException("digestAlgorithm must be one of " +
                    allowableDigestAlgorithms);
        }

        mDigestAlgorithm = digestAlgorithm;
        mDocType = docType;
        mDeviceKey = deviceKey;
        switch (digestAlgorithm) {
            case "SHA-256": mDigestSize = 32; break;
            case "SHA-384": mDigestSize = 48; break;
            case "SHA-512": mDigestSize = 64; break;
            default: mDigestSize = -1;
        }
    }

    /**
     * Populates the <code>ValueDigests</code> mapping. This must be called at least once before
     * generating since <code>ValueDigests</code> must be non-empty.
     *
     * @param nameSpace The namespace.
     * @param digestIDs A non-empty mapping between a <code>DigestID</code> and a
     *                  <code>Digest</code>.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     * @exception IllegalArgumentException if the <code>digestIDs</code> is empty.
     */
    @NonNull
    public MobileSecurityObjectGenerator addDigestIdsForNamespace(@NonNull String nameSpace,
                                                                  @NonNull Map<Long, byte[]> digestIDs) {

        if (digestIDs.isEmpty()) {
            throw new IllegalArgumentException("digestIDs must not be empty");
        }
        digestEmpty = false;

        MapBuilder<MapBuilder<CborBuilder>> valueDigestsInner = mValueDigestsOuter.putMap(nameSpace);
        for (Long digestID : digestIDs.keySet()) {
            byte[] digest = digestIDs.get(digestID);
            if (digest.length != mDigestSize) {
                throw new IllegalArgumentException("digest is unexpected length: expected " +
                        mDigestSize + " , but got " + digest.length);
            }
            valueDigestsInner.put(digestID, digest);
        }
        valueDigestsInner.end();
        return this;
    }

    /**
     * Populates the <code>AuthorizedNameSpaces</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to full namespaces
     * included in the <code>authorizedNameSpaces</code> array. If authorization is given for a full
     * namespace, that namespace shall not be included in 
     * {@link #setDeviceKeyAuthorizedDataElements(Map)}.
     *
     * @param authorizedNameSpaces A list of namespaces which should be given authorization.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     * @throws IllegalArgumentException if the authorizedNameSpaces does not meet the constraints.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedNameSpaces(
            @NonNull List<String> authorizedNameSpaces) {

        Set<String> namespaceSet = new HashSet<>();
        namespaceSet.addAll(mAuthorizedDataElements.keySet());
        namespaceSet.retainAll(authorizedNameSpaces);

        // 18013-5 Section 9.1.2.4 says "If authorization is given for a full namespace (by including
        // the namespace in the AuthorizedNameSpaces array), that namespace shall not be included in
        // the AuthorizedDataElements map.
        if (!namespaceSet.isEmpty()){
            throw new IllegalArgumentException("authorizedNameSpaces includes a namespace already " +
                    "present in the mapping of authorized data elements provided.");
        }

        mAuthorizedNameSpaces.clear();
        mAuthorizedNameSpaces.addAll(authorizedNameSpaces);
        return this;
    }

    /**
     * Populates the <code>AuthorizedDataElements</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to data elements
     * included in the <code>authorizedDataElements</code> mapping. If a namespace is included here,
     * then it should not be included in {@link #setDeviceKeyAuthorizedNameSpaces(List)}
     *
     * @param authorizedDataElements A mapping from namespaces to a list of
     *                               <code>DataElementIdentifier</code>
     * @return The <code>MobileSecurityObjectGenerator</code>.
     * @throws IllegalArgumentException if authorizedDataElements does not meet the constraints.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedDataElements(
            @NonNull Map<String, List<String>> authorizedDataElements) {

        Set<String> namespaceSet = new HashSet<>();
        namespaceSet.addAll(authorizedDataElements.keySet());
        namespaceSet.retainAll(mAuthorizedNameSpaces);

        // 18013-5 Section 9.1.2.4 says "If authorization is given for a full namespace (by including
        // the namespace in the AuthorizedNameSpaces array), that namespace shall not be included in
        // the AuthorizedDataElements map.
        if (!namespaceSet.isEmpty()){
            throw new IllegalArgumentException("authorizedDataElements includes a namespace already " +
                    "present in the list of authorized name spaces provided.");
        }

        mAuthorizedDataElements.clear();
        mAuthorizedDataElements.putAll(authorizedDataElements);
        return this;
    }

    /**
     * Provides extra info for the mdoc authentication public key as part of the
     * <code>KeyInfo</code> portion of the <code>DeviceKeyInfo</code>.
     *
     * @param keyInfo A mapping to represent additional key information.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyInfo(@NonNull Map<Long, byte[]> keyInfo) {
        mKeyInfo.clear();
        mKeyInfo.putAll(keyInfo);
        return this;
    }

    /**
     * Sets the <code>ValidityInfo</code> structure which contains information related to the
     * validity of the MSO and its signature. This must be called before generating since this a
     * required component of the <code>MobileSecurityObject</code>.
     *
     * @param signed         The timestamp at which the MSO signature was created.
     * @param validFrom      The timestamp before which the MSO is not yet valid. This shall be
     *                       equal or later than the signed element.
     * @param validUntil     The timestamp after which the MSO is no longer valid. This shall be
     *                       later than the validFrom element.
     * @param expectedUpdate Optional: if provided, represents the timestamp at which the issuing
     *                       authority infrastructure expects to re-sign the MSO, else, null
     * @return The <code>MobileSecurityObjectGenerator</code>.
     * @exception IllegalArgumentException if the times are do not meet the constraints.
     */
    @NonNull
    public MobileSecurityObjectGenerator setValidityInfo(@NonNull Timestamp signed,
            @NonNull Timestamp validFrom, @NonNull Timestamp validUntil,
            @Nullable Timestamp expectedUpdate) {

        // 18013-5 Section 9.1.2.4 says "The timestamp of validFrom shall be equal or later than
        // the signed element."
        if (validFrom.toEpochMilli() < signed.toEpochMilli()) {
            throw new IllegalArgumentException("The validFrom timestamp should be equal or later " +
                    "than the signed timestamp");
        }

        // 18013-5 Section 9.1.2.4 says "The validUntil element contains the timestamp after which the
        // MSO is no longer valid. The value of the timestamp shall be later than the validFrom element."
        if (validUntil.toEpochMilli() <= validFrom.toEpochMilli()) {
            throw new IllegalArgumentException("The validUntil timestamp should be later " +
                    "than the validFrom timestamp");
        }
        mSigned = signed;
        mValidFrom = validFrom;
        mValidUntil = validUntil;
        mExpectedUpdate = expectedUpdate;
        return this;
    }

    @NonNull
    private CborBuilder generateDeviceKeyBuilder() {
        CborBuilder deviceKeyBuilder = new CborBuilder();
        MapBuilder<CborBuilder> deviceKeyMapBuilder = deviceKeyBuilder.addMap();
        deviceKeyMapBuilder.put(new UnicodeString("deviceKey"), Util.cborBuildCoseKey(mDeviceKey));

        if (!mAuthorizedNameSpaces.isEmpty() | !mAuthorizedDataElements.isEmpty()) {
            MapBuilder<MapBuilder<CborBuilder>> keyAuthMapBuilder = deviceKeyMapBuilder.putMap("keyAuthorizations");
            if (!mAuthorizedNameSpaces.isEmpty()) {
                ArrayBuilder<MapBuilder<MapBuilder<CborBuilder>>> authNameSpacesArrayBuilder =
                        keyAuthMapBuilder.putArray("nameSpaces");
                for (String namespace : mAuthorizedNameSpaces) {
                    authNameSpacesArrayBuilder.add(namespace);
                }
                authNameSpacesArrayBuilder.end();
            }

            if (!mAuthorizedDataElements.isEmpty()) {
                MapBuilder<MapBuilder<MapBuilder<CborBuilder>>> authDataElemOuter =
                        keyAuthMapBuilder.putMap("dataElements");
                for (String namespace : mAuthorizedDataElements.keySet()) {
                    ArrayBuilder<MapBuilder<MapBuilder<MapBuilder<CborBuilder>>>> authDataElemInner =
                            authDataElemOuter.putArray(namespace);
                    for (String dataElemIdentifier : mAuthorizedDataElements.get(namespace)) {
                        authDataElemInner.add(dataElemIdentifier);
                    }
                    authDataElemInner.end();
                }
                authDataElemOuter.end();
            }
            keyAuthMapBuilder.end();
        }

        if (!mKeyInfo.isEmpty()) {
            MapBuilder<MapBuilder<CborBuilder>> keyInfoMapBuilder = deviceKeyMapBuilder.putMap("keyInfo");
            for (Long label : mKeyInfo.keySet()) {
                keyInfoMapBuilder.put(label, mKeyInfo.get(label));
            }
            keyInfoMapBuilder.end();
        }

        deviceKeyMapBuilder.end();
        return deviceKeyBuilder;
    }

    @NonNull
    private CborBuilder generateValidityInfoBuilder() {
        CborBuilder validityInfoBuilder = new CborBuilder();
        MapBuilder<CborBuilder> validityMapBuilder = validityInfoBuilder.addMap();
        validityMapBuilder.put(new UnicodeString("signed"), Util.cborBuildDateTime(mSigned));
        validityMapBuilder.put(new UnicodeString("validFrom"), Util.cborBuildDateTime(mValidFrom));
        validityMapBuilder.put(new UnicodeString("validUntil"), Util.cborBuildDateTime(mValidUntil));
        if (mExpectedUpdate != null) {
            validityMapBuilder.put(new UnicodeString("expectedUpdate"),
                    Util.cborBuildDateTime(mExpectedUpdate));
        }
        validityMapBuilder.end();
        return validityInfoBuilder;
    }

    /**
     * Builds the <code>MobileSecurityObject</code> CBOR.
     *
     * <p>It's mandatory to call {@link #addDigestIdsForNamespace(String, Map)} and
     * {@link #setValidityInfo(Timestamp, Timestamp, Timestamp, Timestamp)} before this call.
     *
     * @return the bytes of <code>MobileSecurityObject</code> CBOR.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    @NonNull
    public byte[] generate() {
        if (digestEmpty) {
            throw new IllegalStateException("Must call addDigestIdsForNamespace before generating");
        } else if (mSigned == null) {
            throw new IllegalStateException("Must call setValidityInfo before generating");
        }

        CborBuilder msoBuilder = new CborBuilder();
        MapBuilder<CborBuilder> msoMapBuilder = msoBuilder.addMap();
        msoMapBuilder.put("version", "1.0");
        msoMapBuilder.put("digestAlgorithm", mDigestAlgorithm);
        msoMapBuilder.put("docType", mDocType);
        msoMapBuilder.put(new UnicodeString("valueDigests"), mValueDigestsBuilder.build().get(0));
        msoMapBuilder.put(new UnicodeString("deviceKeyInfo"), generateDeviceKeyBuilder().build().get(0));
        msoMapBuilder.put(new UnicodeString("validityInfo"), generateValidityInfoBuilder().build().get(0));
        msoMapBuilder.end();

        return Util.cborEncode(msoBuilder.build().get(0));
    }

}
