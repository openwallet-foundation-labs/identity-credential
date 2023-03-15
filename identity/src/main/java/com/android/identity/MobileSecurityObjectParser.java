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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.nstant.in.cbor.model.DataItem;

/**
 * Helper class for parsing the bytes of <code>MobileSecurityObject</code>
 * <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>.
 */
public class MobileSecurityObjectParser {
    private byte[] mEncodedMobileSecurityObject;

    /**
     * Constructs a {@link MobileSecurityObjectParser}.
     */
    public MobileSecurityObjectParser() {
    }

    /**
     * Sets the bytes of the <code>MobileSecurityObject</code> CBOR.
     *
     * @param encodedMobileSecurityObject The bytes of <code>MobileSecurityObject</code>.
     * @return The <code>MobileSecurityObjectParser</code>.
     */
    @NonNull
    public MobileSecurityObjectParser setMobileSecurityObject(
            @NonNull byte[] encodedMobileSecurityObject) {
        mEncodedMobileSecurityObject = encodedMobileSecurityObject;
        return this;
    }

    /**
     * Parses the mobile security object.
     *
     * <p>It's mandatory to call {@link #setMobileSecurityObject(byte[])} before this call.
     *
     * @return a {@link MobileSecurityObject} with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    public @NonNull MobileSecurityObject parse() {
        if (mEncodedMobileSecurityObject == null) {
            throw new IllegalStateException("mobileSecurityObject has not been set");
        }
        MobileSecurityObject mso = new MobileSecurityObject();
        mso.parse(mEncodedMobileSecurityObject);
        return mso;
    }

    /**
     * An object used to represent data parsed from <code>MobileSecurityObject</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>
     */
    public static class MobileSecurityObject {
        private String mVersion;
        private String mDigestAlgorithm;
        private String mDocType;
        private Map<String, Map<Long, byte[]>> mValueDigests;
        private PublicKey mDeviceKey;
        private List<String> mAuthorizedNameSpaces;
        private Map<String, List<String>> mAuthorizedDataElements;
        private Map<Long, byte[]> mDeviceKeyInfo;
        private Timestamp mSigned, mValidFrom, mValidUntil, mExpectedUpdate;

        MobileSecurityObject() {}

        /**
         * Gets the version string set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return The version string e.g. "1.0".
         */
        @NonNull
        public String getVersion() {
            return mVersion;
        }

        /**
         * Gets the digest algorithm set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return The digest algorithm e.g. "SHA-256".
         */
        @NonNull
        public String getDigestAlgorithm() { return mDigestAlgorithm; }

        /**
         * Gets the document type set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return The document type e.g. "org.iso.18013.5.1.mDL".
         */
        @NonNull
        public String getDocType() { return mDocType; }

        /**
         * Gets the set of namespaces provided in the ValueDigests map within the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return The set of namespaces provided in the ValueDigests map.
         */
        @NonNull
        public Set<String> getValueDigestNamespaces() { return mValueDigests.keySet(); }

        /**
         * Gets a non-empty mapping between a <code>DigestID</code> and a <code>Digest</code> for a
         * particular namespace, as set in the ValueDigests map within the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @param namespace The namespace of interest.
         * @return The mapping present for that namespace if it exists within the ValueDigests,
         *         else null.
         */
        @Nullable
        public Map<Long, byte[]> getDigestIDs(@NonNull String namespace) {
            return mValueDigests.get(namespace);
        }

        /**
         * Gets the mdoc authentication public key set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return The public part of the key pair used for mdoc authentication.
         */
        @NonNull
        public PublicKey getDeviceKey() { return mDeviceKey; }

        /**
         * Gets the <code>AuthorizedNameSpaces</code> portion of the <code>keyAuthorizations</code>
         * within <code>DeviceKeyInfo</code>.
         *
         * @return The list of namespaces which should be given authorization, null if it does not
         *         exist in the MSO.
         */
        @Nullable
        public List<String> getDeviceKeyAuthorizedNameSpaces() { return mAuthorizedNameSpaces; }

        /**
         * Gets the <code>AuthorizedDataElements</code> portion of the <code>keyAuthorizations</code>
         * within <code>DeviceKeyInfo</code>.
         *
         * @return A mapping from namespaces to a list of <code>DataElementIdentifier</code>, null
         *         if it does not exist in the MSO.
         */
        @Nullable
        public Map<String, List<String>> getDeviceKeyAuthorizedDataElements() {
            return mAuthorizedDataElements;
        }

        /**
         * Gets extra info for the mdoc authentication public key as part of the
         * <code>KeyInfo</code> portion of the <code>DeviceKeyInfo</code>.
         *
         * @return A mapping to represent additional key information, null if it does not exist in
         *         the MSO.
         */
        @Nullable
        public Map<Long, byte[]> getDeviceKeyInfo() { return mDeviceKeyInfo; }

        /**
         * Gets the timestamp at which the MSO signature was created, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return The timestamp at which the MSO signature was created.
         */
        @NonNull
        public Timestamp getSigned() { return mSigned; }

        /**
         * Gets the timestamp before which the MSO is not yet valid, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return The timestamp before which the MSO is not yet valid.
         */
        @NonNull
        public Timestamp getValidFrom() { return mValidFrom; }

        /**
         * Gets the timestamp after which the MSO is no longer valid, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return The timestamp after which the MSO is no longer valid.
         */
        @NonNull
        public Timestamp getValidUntil() { return mValidUntil; }

        /**
         * Gets the timestamp at which the issuing authority infrastructure expects to re-sign the
         * MSO, if provided in the <code>MobileSecurityObject</code> CBOR, else null.
         *
         * @return The timestamp at which the issuing authority infrastructure expects to re-sign
         * the MSO.
         */
        @Nullable
        public Timestamp getExpectedUpdate() { return mExpectedUpdate; }

        private void parseValueDigests(DataItem valueDigests) {
            mValueDigests = new HashMap<>();
            for (String namespace : Util.cborMapExtractMapStringKeys(valueDigests)) {
                DataItem digestIDsDataItem = Util.cborMapExtractMap(valueDigests, namespace);

                Map<Long, byte[]> digestIDs = new HashMap<>();
                for (Long digestID : Util.cborMapExtractMapNumberKeys(digestIDsDataItem)) {
                    digestIDs.put(digestID, Util.cborMapExtractByteString(digestIDsDataItem, digestID));
                }

                mValueDigests.put(namespace, digestIDs);
            }
        }

        private void parseDeviceKeyInfo(DataItem deviceKeyInfo) {
            mDeviceKey = Util.coseKeyDecode(Util.cborMapExtract(deviceKeyInfo, "deviceKey"));

            mAuthorizedNameSpaces = null;
            mAuthorizedDataElements = null;
            if (Util.cborMapHasKey(deviceKeyInfo, "keyAuthorizations")) {
                DataItem keyAuth = Util.cborMapExtractMap(deviceKeyInfo, "keyAuthorizations");
                if (Util.cborMapHasKey(keyAuth, "nameSpaces")) {
                    mAuthorizedNameSpaces = new ArrayList<>();
                    List<DataItem> nsDataItems = Util.cborMapExtractArray(keyAuth, "nameSpaces");
                    for (DataItem nsDataItem : nsDataItems) {
                        mAuthorizedNameSpaces.add(Util.checkedStringValue(nsDataItem));
                    }
                }

                if (Util.cborMapHasKey(keyAuth, "dataElements")) {
                    mAuthorizedDataElements = new HashMap<>();
                    DataItem dataElements = Util.cborMapExtractMap(keyAuth, "dataElements");
                    for (String namespace : Util.cborMapExtractMapStringKeys(dataElements)) {
                        List<DataItem> dataElemDataItem = Util.cborMapExtractArray(dataElements, namespace);
                        List<String> dataElemArray = new ArrayList<>();
                        for (DataItem dataElementIdentifier : dataElemDataItem) {
                            dataElemArray.add(Util.checkedStringValue(dataElementIdentifier));
                        }

                        mAuthorizedDataElements.put(namespace, dataElemArray);
                    }
                }
            }

            mDeviceKeyInfo = null;
            if (Util.cborMapHasKey(deviceKeyInfo, "keyInfo")) {
                mDeviceKeyInfo = new HashMap<>();
                DataItem keyInfo = Util.cborMapExtractMap(deviceKeyInfo, "keyInfo");
                for (Long keyInfoKey : Util.cborMapExtractMapNumberKeys(keyInfo)) {
                    mDeviceKeyInfo.put(keyInfoKey, Util.cborMapExtractByteString(keyInfo, keyInfoKey));
                }
            }
        }

        private void parseValidityInfo(DataItem validityInfo) {
            mSigned = Util.cborMapExtractDateTime(validityInfo, "signed");
            mValidFrom = Util.cborMapExtractDateTime(validityInfo, "validFrom");
            mValidUntil = Util.cborMapExtractDateTime(validityInfo, "validUntil");
            if (Util.cborMapHasKey(validityInfo, "expectedUpdate")) {
                mExpectedUpdate = Util.cborMapExtractDateTime(validityInfo, "expectedUpdate");
            } else {
                mExpectedUpdate = null;
            }

            if (mValidFrom.toEpochMilli() < mSigned.toEpochMilli()) {
                throw new IllegalArgumentException("The validFrom timestamp should be equal or later " +
                        "than the signed timestamp");
            }
            if (mValidUntil.toEpochMilli() <= mValidFrom.toEpochMilli()) {
                throw new IllegalArgumentException("The validUntil timestamp should be later " +
                        "than the validFrom timestamp");
            }
        }

        void parse(byte[] encodedMobileSecurityObject) {
            DataItem mso = Util.cborDecode(encodedMobileSecurityObject);

            mVersion = Util.cborMapExtractString(mso, "version");
            if (mVersion.compareTo("1.0") < 0) {
                throw new IllegalArgumentException("Given version '" + mVersion + "' not >= '1.0'");
            }

            mDigestAlgorithm = Util.cborMapExtractString(mso, "digestAlgorithm");
            final List<String> allowableDigestAlgorithms = List.of("SHA-256", "SHA-384", "SHA-512");
            if (!allowableDigestAlgorithms.contains(mDigestAlgorithm)) {
                throw new IllegalArgumentException("Given digest algorithm '" + mDigestAlgorithm +
                        "' one of " + allowableDigestAlgorithms);
            }

            mDocType = Util.cborMapExtractString(mso, "docType");
            parseValueDigests(Util.cborMapExtract(mso, "valueDigests"));
            parseDeviceKeyInfo(Util.cborMapExtract(mso, "deviceKeyInfo"));
            parseValidityInfo(Util.cborMapExtract(mso, "validityInfo"));
        }
    }

}
