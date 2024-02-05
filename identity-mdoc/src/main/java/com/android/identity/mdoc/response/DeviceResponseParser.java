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

import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.CborArray;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.DataItemExtensionsKt;
import com.android.identity.cbor.RawCbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.cose.Cose;
import com.android.identity.cose.CoseMac0;
import com.android.identity.cose.CoseNumberLabel;
import com.android.identity.cose.CoseSign1;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.CertificateChain;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.mso.MobileSecurityObjectParser;
import com.android.identity.crypto.EcCurve;
import com.android.identity.util.Constants;
import com.android.identity.util.Logger;
import com.android.identity.util.Timestamp;
import com.android.identity.internal.Util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for parsing the bytes of <code>DeviceResponse</code>
 * <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
 */
public final class DeviceResponseParser {

    private byte[] mEncodedDeviceResponse;
    private byte[] mEncodedSessionTranscript;
    private EcPrivateKey mEReaderKey;

    /**
     * Constructs a {@link DeviceResponseParser}.
     */
    public DeviceResponseParser() {
    }

    /**
     * Sets the bytes of the <code>DeviceResponse</code> CBOR.
     *
     * @param encodedDeviceResponse the bytes of <code>DeviceResponse</code>.
     * @return the <code>DeviceResponseParser</code>.
     */
    public @NonNull DeviceResponseParser setDeviceResponse(@NonNull byte[] encodedDeviceResponse) {
        mEncodedDeviceResponse = encodedDeviceResponse;
        return this;
    }

    /**
     * Sets the bytes of the <code>SessionTranscript</code> CBOR.
     *
     * @param encodedSessionTranscript the bytes of <code>SessionTranscript</code>.
     * @return the <code>DeviceResponseParser</code>.
     */
    public @NonNull DeviceResponseParser setSessionTranscript(
            @NonNull byte[] encodedSessionTranscript) {
        mEncodedSessionTranscript = encodedSessionTranscript;
        return this;
    }

    /**
     * Sets the private part of the ephemeral key used in the session where the
     * <code>DeviceResponse</code> was obtained.
     *
     * <p>This is only required if the <code>DeviceResponse</code> is using
     * the MAC method for device authentication.
     *
     * TODO: convert to using SecureArea
     *
     * @param eReaderKey the private part of the reader ephemeral key.
     * @return the <code>DeviceResponseParser</code>.
     */
    public @NonNull DeviceResponseParser setEphemeralReaderKey(@NonNull EcPrivateKey eReaderKey) {
        mEReaderKey = eReaderKey;
        return this;
    }

    /**
     * Parses the device response.
     *
     * <p>It's mandatory to call {@link #setDeviceResponse(byte[])},
     * {@link #setSessionTranscript(byte[])} before this call. If the response is using MAC for
     * device authentication, {@link #setEphemeralReaderKey(EcPrivateKey)} must also have been
     * called.
     *
     * <p>This parser will successfully parse responses where issuer-signed data elements fails
     * the digest check against the MSO, where {@code DeviceSigned} authentication checks fail,
     * and where {@code IssuerSigned} authentication checks fail. The methods
     * {@link Document#getIssuerEntryDigestMatch(String, String)},
     * {@link Document#getDeviceSignedAuthenticated()}, and
     * {@link Document#getIssuerSignedAuthenticated()}
     * can be used to get additional information about this.
     *
     * @return a {@link DeviceResponseParser.DeviceResponse} with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    public @NonNull DeviceResponse parse() {
        if (mEncodedDeviceResponse == null) {
            throw new IllegalStateException("deviceResponse has not been set");
        }
        if (mEncodedSessionTranscript == null) {
            throw new IllegalStateException("sessionTranscript has not been set");
        }
        // mEReaderKey may be omitted if the response is using ECDSA instead of MAC
        // for device authentication.
        DeviceResponse response = new DeviceResponse();
        response.parse(mEncodedDeviceResponse, mEncodedSessionTranscript, mEReaderKey);
        return response;
    }

    /**
     * An object used to represent data parsed from <code>DeviceResponse</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
     */
    public static final class DeviceResponse {
        static final String TAG = "DeviceResponse";

        List<Document> mResultDocuments = null;
        private String mVersion;

        // Returns the DeviceKey from the MSO
        //
        private @NonNull
        EcPublicKey parseIssuerSigned(
                String expectedDocType,
                DataItem issuerSigned,
                Document.Builder builder) {

            CoseSign1 issuerAuth = issuerSigned.get("issuerAuth").getAsCoseSign1();

            // 18013-5 clause "9.1.2.4 Signing method and structure for MSO" guarantees
            // that x5chain is in the unprotected headers and that alg is in the
            // protected headers...

            CertificateChain issuerAuthorityCertChain =
                    issuerAuth.getUnprotectedHeaders()
                            .get(new CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN))
                            .getAsCertificateChain();

            Algorithm signatureAlgorithm =
                    Algorithm.Companion.fromInt( (int)
                            issuerAuth.getProtectedHeaders()
                                    .get(new CoseNumberLabel(Cose.COSE_LABEL_ALG))
                                    .getAsNumber());

            EcPublicKey documentSigningKey =
                    issuerAuthorityCertChain.getCertificates().get(0).getPublicKey();

            boolean issuerSignedAuthenticated = Cose.coseSign1Check(
                    documentSigningKey,
                    null,
                    issuerAuth,
                    signatureAlgorithm);
            builder.setIssuerSignedAuthenticated(issuerSignedAuthenticated);
            builder.setIssuerCertificateChain(issuerAuthorityCertChain);

            byte[] encodedMobileSecurityObject = Cbor.decode(issuerAuth.getPayload()).getAsTagged().getAsBstr();
            MobileSecurityObjectParser.MobileSecurityObject parsedMso = new MobileSecurityObjectParser()
                    .setMobileSecurityObject(encodedMobileSecurityObject).parse();

            builder.setValidityInfoSigned(parsedMso.getSigned());
            builder.setValidityInfoValidFrom(parsedMso.getValidFrom());
            builder.setValidityInfoValidUntil(parsedMso.getValidUntil());
            if (parsedMso.getExpectedUpdate() != null) {
                builder.setValidityInfoExpectedUpdate(parsedMso.getExpectedUpdate());
            }

            /* don't care about version for now */
            String digestAlgorithm = parsedMso.getDigestAlgorithm();
            MessageDigest digester;
            try {
                digester = MessageDigest.getInstance(digestAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Failed creating digester");
            }

            String msoDocType = parsedMso.getDocType();
            if (!msoDocType.equals(expectedDocType)) {
                throw new IllegalArgumentException("docType in MSO '" + msoDocType
                        + "' does not match docType from Document");
            }

            Set<String> nameSpaceNames = parsedMso.getValueDigestNamespaces();
            Map<String, Map<Long, byte[]>> digestMapping = new HashMap<>();
            for (String nameSpaceName : nameSpaceNames) {
                digestMapping.put(nameSpaceName, parsedMso.getDigestIDs(nameSpaceName));
            }

            EcPublicKey deviceKey = parsedMso.getDeviceKey();

            // nameSpaces may be absent...
            DataItem nameSpaces = issuerSigned.getOrNull("nameSpaces");
            if (nameSpaces != null) {
                for (DataItem nameSpaceDataItem : nameSpaces.getAsMap().keySet()) {
                    String nameSpace = nameSpaceDataItem.getAsTstr();
                    Map<Long, byte[]> innerDigestMapping = digestMapping.get(nameSpace);
                    if (innerDigestMapping == null) {
                        throw new IllegalArgumentException("No digestID MSO entry for namespace "
                                + nameSpace);
                    }

                    DataItem elementsDataItem = nameSpaces.get(nameSpaceDataItem);
                    for (DataItem elem : elementsDataItem.getAsArray()) {
                        if (!(elem instanceof Tagged &&
                                ((Tagged) elem).getTagNumber() == 24 &&
                                ((Tagged) elem).getAsTagged() instanceof Bstr)) {
                            throw new IllegalArgumentException(
                                    "issuerSignedItemBytes is not a tagged ByteString");
                        }

                        // We need the encoded representation with the tag.
                        byte[] encodedIssuerSignedItemBytes = Cbor.encode(elem);
                        byte[] expectedDigest = digester.digest(encodedIssuerSignedItemBytes);

                        DataItem issuerSignedItem = Cbor.decode(elem.getAsTagged().getAsBstr());
                        String elementName = issuerSignedItem.get("elementIdentifier").getAsTstr();
                        DataItem elementValue = issuerSignedItem.get("elementValue");
                        long digestId = issuerSignedItem.get("digestID").getAsNumber();

                        byte[] digest = innerDigestMapping.get(digestId);
                        if (digest == null) {
                            throw new IllegalArgumentException("No digestID MSO entry for ID "
                                    + digestId + " in namespace " + nameSpace);
                        }
                        boolean digestMatch = Arrays.equals(expectedDigest, digest);
                        builder.addIssuerEntry(nameSpace, elementName,
                                Cbor.encode(elementValue),
                                digestMatch);
                    }
                }
           }
            return deviceKey;
        }

        private void parseDeviceSigned(
                DataItem deviceSigned,
                String docType,
                byte[] encodedSessionTranscript,
                EcPublicKey deviceKey,
                EcPrivateKey eReaderKey,
                Document.Builder builder) {
            DataItem nameSpacesBytes = deviceSigned.get("nameSpaces");
            DataItem nameSpaces = nameSpacesBytes.getAsTaggedEncodedCbor();

            DataItem deviceAuth = deviceSigned.get("deviceAuth");

            DataItem deviceAuthentication = CborArray.Companion.builder()
                    .add("DeviceAuthentication")
                    .add(new RawCbor(encodedSessionTranscript))
                    .add(docType)
                    .add(nameSpacesBytes)
                    .end()
                    .build();
            byte[] deviceAuthenticationBytes =
                    Cbor.encode(new Tagged(24, new Bstr(Cbor.encode(deviceAuthentication))));

            boolean deviceSignedAuthenticated = false;

            DataItem deviceSignature = deviceAuth.getOrNull("deviceSignature");
            if (deviceSignature != null) {

                CoseSign1 deviceSignatureCoseSign1 = deviceSignature.getAsCoseSign1();

                // 18013-5 clause "9.1.3.6 mdoc ECDSA / EdDSA Authentication" guarantees
                // that alg is in the protected header
                //
                Algorithm signatureAlgorithm =
                        Algorithm.Companion.fromInt( (int)
                                deviceSignatureCoseSign1.getProtectedHeaders()
                                        .get(new CoseNumberLabel(Cose.COSE_LABEL_ALG))
                                        .getAsNumber());
                deviceSignedAuthenticated =
                        Cose.coseSign1Check(deviceKey,
                                deviceAuthenticationBytes,
                                deviceSignatureCoseSign1,
                                signatureAlgorithm);
                builder.setDeviceSignedAuthenticatedViaSignature(true);
            } else {
                DataItem deviceMacDataItem = deviceAuth.getOrNull("deviceMac");
                if (deviceMacDataItem == null) {
                    throw new IllegalArgumentException(
                            "Neither deviceSignature nor deviceMac in deviceAuth");
                }
                CoseMac0 deviceMac = deviceMacDataItem.getAsCoseMac0();
                byte[] tagInResponse = deviceMac.getTag();

                byte[] eMacKey = calcEMacKeyForReader(
                        deviceKey,
                        eReaderKey,
                        encodedSessionTranscript);

                byte[] expectedTag = Cose.coseMac0(
                        Algorithm.HMAC_SHA256,
                        eMacKey,
                        deviceAuthenticationBytes,
                        false,
                        Map.of(
                                new CoseNumberLabel(Cose.COSE_LABEL_ALG),
                                DataItemExtensionsKt.getDataItem(Algorithm.HMAC_SHA256.getCoseAlgorithmIdentifier())
                        ),
                        Map.of()
                ).getTag();

                deviceSignedAuthenticated = Arrays.equals(expectedTag, tagInResponse);
                if (deviceSignedAuthenticated) {
                    Logger.d(TAG, "Verified DeviceSigned using MAC");
                } else {
                    Logger.d(TAG, "Device MAC mismatch, got " + Util.toHex(tagInResponse)
                            + " expected " + Util.toHex(expectedTag));
                }


            }
            builder.setDeviceSignedAuthenticated(deviceSignedAuthenticated);

            for (DataItem nameSpaceDataItem : nameSpaces.getAsMap().keySet()) {
                String nameSpace = nameSpaceDataItem.getAsTstr();
                DataItem innerMap = nameSpaces.get(nameSpaceDataItem);
                for (DataItem elementNameDataItem: innerMap.getAsMap().keySet()) {
                    String elementName = elementNameDataItem.getAsTstr();
                    DataItem elementValue = innerMap.get(elementNameDataItem);
                    builder.addDeviceEntry(nameSpace, elementName, Cbor.encode(elementValue));
                }
            }
        }

        private static @NonNull
        byte[] calcEMacKeyForReader(
                @NonNull EcPublicKey authenticationPublicKey,
                @NonNull EcPrivateKey ephemeralReaderPrivateKey,
                @NonNull byte[] encodedSessionTranscript) {
            byte[] sharedSecret = Crypto.keyAgreement(ephemeralReaderPrivateKey, authenticationPublicKey);

            byte[] sessionTranscriptBytes =
                    Cbor.encode(new Tagged(24, new Bstr(encodedSessionTranscript)));

            byte[] salt = Crypto.digest(Algorithm.SHA256, sessionTranscriptBytes);
            byte[] info = "EMacKey".getBytes(StandardCharsets.UTF_8);
            return Crypto.hkdf(Algorithm.HMAC_SHA256, sharedSecret, salt, info, 32);
        }


        void parse(byte[] encodedDeviceResponse,
                byte[] encodedSessionTranscript,
                EcPrivateKey eReaderKey) {
            mResultDocuments = null;

            DataItem deviceResponse = Cbor.decode(encodedDeviceResponse);

            ArrayList<Document> documents = new ArrayList<>();

            mVersion = deviceResponse.get("version").getAsTstr();
            if (mVersion.compareTo("1.0") < 0) {
                throw new IllegalArgumentException("Given version '" + mVersion + "' not >= '1.0'");
            }

            DataItem documentsDataItem = deviceResponse.getOrNull("documents");
            if (documentsDataItem != null) {
                for (DataItem documentItem : documentsDataItem.getAsArray()) {
                    String docType = documentItem.get("docType").getAsTstr();
                    Document.Builder builder = new Document.Builder(docType);

                    DataItem issuerSignedItem = documentItem.get("issuerSigned");
                    EcPublicKey deviceKey = parseIssuerSigned(docType, issuerSignedItem, builder);
                    builder.setDeviceKey(deviceKey);

                    DataItem deviceSigned = documentItem.get("deviceSigned");
                    parseDeviceSigned(deviceSigned, docType, encodedSessionTranscript, deviceKey, eReaderKey, builder);

                    documents.add(builder.build());
                }
            }

            mResultStatus = deviceResponse.get("status").getAsNumber();

            // TODO: maybe also parse + convey "documentErrors" and "errors" keys in
            //  DeviceResponse map.

            mResultDocuments = documents;
        }

        DeviceResponse() {
        }

        /**
         * Gets the version string set in the <code>DeviceResponse</code> CBOR.
         *
         * @return the version string e.g. "1.0".
         */
        public @NonNull String getVersion() {
            return mVersion;
        }

        /**
         * Gets the documents in the device response.
         *
         * @return A list of {@link Document} objects.
         */
        public @NonNull List<Document> getDocuments() {
            return mResultDocuments;
        }

        private
        long mResultStatus = Constants.DEVICE_RESPONSE_STATUS_OK;

        /**
         * Gets the top-level status in the <code>DeviceResponse</code> CBOR.
         *
         * <p>Note that this value is not a result of parsing/validating the
         * <code>DeviceResponse</code> CBOR. It's a value which was part of
         * the CBOR and chosen by the remote device.
         *
         * @return One of {@link Constants#DEVICE_RESPONSE_STATUS_OK},
         * {@link Constants#DEVICE_RESPONSE_STATUS_GENERAL_ERROR},
         * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR}, or
         * {@link Constants#DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR}.
         */
        public
        long getStatus() {
            return mResultStatus;
        }
    }

    /**
     * An object used to represent data parsed from the <code>Document</code>
     * <a href="http://cbor.io/">CBOR</a> (part of <code>DeviceResponse</code>)
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
     */
    public static class Document {
        static final String TAG = "Document";
        private EcCurve mDeviceKeyCurve;

        static class EntryData {
            byte[] mValue;
            boolean mDigestMatch;

            EntryData(byte[] value, boolean digestMatch) {
                this.mValue = value;
                this.mDigestMatch = digestMatch;
            }
        }

        String mDocType;
        Map<String, Map<String, EntryData>> mDeviceData = new LinkedHashMap<>();
        Map<String, Map<String, EntryData>> mIssuerData = new LinkedHashMap<>();
        CertificateChain mIssuerCertificateChain;
        int mNumIssuerEntryDigestMatchFailures;
        boolean mDeviceSignedAuthenticated;
        boolean mIssuerSignedAuthenticated;
        Timestamp mValidityInfoSigned;
        Timestamp mValidityInfoValidFrom;
        Timestamp mValidityInfoValidUntil;
        Timestamp mValidityInfoExpectedUpdate;
        EcPublicKey mDeviceKey;
        boolean mDeviceSignedAuthenticatedViaSignature;

        /**
         * Returns the type of document (commonly referred to as <code>docType</code>).
         *
         * @return the document type.
         */
        public @NonNull String getDocType() {
            return mDocType;
        }

        /**
         * Returns the <code>signed</code> date from the MSO.
         *
         * @return a {@code Timestamp} for when the MSO was signed.
         */
        public @NonNull
        Timestamp getValidityInfoSigned() {
            return mValidityInfoSigned;
        }

        /**
         * Returns the <code>validFrom</code> date from the MSO.
         *
         * @return a {@code Timestamp} for when the MSO is valid from.
         */
        public @NonNull
        Timestamp getValidityInfoValidFrom() {
            return mValidityInfoValidFrom;
        }

        /**
         * Returns the <code>validUntil</code> date from the MSO.
         *
         * @return a {@code Timestamp} for when the MSO is valid until.
         */
        public @NonNull
        Timestamp getValidityInfoValidUntil() {
            return mValidityInfoValidUntil;
        }

        /**
         * Returns the <code>expectedUpdate</code> date from the MSO.
         *
         * @return a {@code Timestamp} for when the MSO is valid until or {@code null} if
         *   this isn't set.
         */
        public @Nullable
        Timestamp getValidityInfoExpectedUpdate() {
            return mValidityInfoExpectedUpdate;
        }

        /**
         * Returns the <code>DeviceKey</code> from the MSO.
         *
         * @return a {@code EcPublicKey} representing the <code>DeviceKey</code>.
         */
        public @NonNull
        EcPublicKey getDeviceKey() {
            return mDeviceKey;
        }

        /**
         * Gets the curve of {@code DeviceKey}.
         *
         * @return the curve.
         */
        public EcCurve
        getDeviceKeyCurve() {
            return mDeviceKeyCurve;
        }

        /**
         * Returns the X509 certificate chain for the issuer which signed the data in the document.
         *
         * @return A X.509 certificate chain.
         */
        public @NonNull CertificateChain getIssuerCertificateChain() {
            return mIssuerCertificateChain;
        }

        /**
         * Returns whether the {@code IssuerSigned} data was authenticated.
         *
         * <p>This returns {@code true} only if the signature on the {@code MobileSecurityObject}
         * data was made with the public key in the leaf certificate returned by.
         * {@link #getIssuerCertificateChain()}
         *
         * @return whether the {@code DeviceSigned} data was authenticated.
         */
        public boolean getIssuerSignedAuthenticated() {
            return mIssuerSignedAuthenticated;
        }

        /**
         * Gets the names of namespaces with retrieved entries of the issuer-signed data.
         *
         * <p>If the document doesn't contain any issuer-signed data, this returns the empty
         * collection.
         *
         * @return Collection of names of namespaces in the issuer-signed data.
         */
        public @NonNull List<String> getIssuerNamespaces() {
            return new ArrayList<>(mIssuerData.keySet());
        }

        /**
         * Gets the names of data elements in the given issuer-signed namespace.
         *
         * @param namespaceName the name of the namespace to get data element names from.
         * @return A collection of data element names for the namespace.
         * @exception IllegalArgumentException if the given namespace isn't in the data.
         */
        public @NonNull List<String> getIssuerEntryNames(@NonNull String namespaceName) {
            Map<String, EntryData> innerMap = mIssuerData.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace not in data");
            }
            return new ArrayList<>(innerMap.keySet());
        }

        /**
         * Gets whether the digest for the given entry matches the digest in the MSO.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the encoded CBOR data for the data element
         * @exception IllegalArgumentException if the given namespace or entry isn't in the data.
         */
        public boolean getIssuerEntryDigestMatch(@NonNull String namespaceName,
                @NonNull String name) {
            Map<String, EntryData> innerMap = mIssuerData.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace not in data");
            }
            EntryData entryData = innerMap.get(name);
            if (entryData == null || entryData.mValue == null) {
                throw new IllegalArgumentException("Entry not in data");
            }
            return entryData.mDigestMatch;
        }

        /**
         * Gets the number of issuer entries for that didn't match the digest in the MSO.
         *
         * @return Number of entries for which {@link #getIssuerEntryDigestMatch(String, String)}
         *   returns {@code false}.
         */
        public int getNumIssuerEntryDigestMatchFailures() {
            return mNumIssuerEntryDigestMatchFailures;
        }

        /**
         * Gets the raw CBOR data for the value of given data element in a given namespace in
         * issuer-signed data.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the encoded CBOR data for the data element
         * @exception IllegalArgumentException if the given namespace or entry isn't in the data.
         */
        public @NonNull byte[] getIssuerEntryData(@NonNull String namespaceName,
                @NonNull String name) {
            Map<String, EntryData> innerMap = mIssuerData.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace not in data");
            }
            EntryData entryData = innerMap.get(name);
            if (entryData == null || entryData.mValue == null) {
                throw new IllegalArgumentException("Entry not in data");
            }
            return entryData.mValue;
        }

        /**
         * Like {@link #getIssuerEntryData(String, String)} but returns the CBOR decoded
         * as a string.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull String getIssuerEntryString(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getIssuerEntryData(namespaceName, name);
            return Cbor.decode(value).getAsTstr();
        }

        /**
         * Like {@link #getIssuerEntryData(String, String)} but returns the CBOR decoded
         * as a byte-string.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull byte[] getIssuerEntryByteString(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getIssuerEntryData(namespaceName, name);
            return Cbor.decode(value).getAsBstr();
        }

        /**
         * Like {@link #getIssuerEntryData(String, String)} but returns the CBOR decoded
         * as a boolean.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public boolean getIssuerEntryBoolean(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getIssuerEntryData(namespaceName, name);
            return Cbor.decode(value).getAsBoolean();
        }

        /**
         * Like {@link #getIssuerEntryData(String, String)} but returns the CBOR decoded
         * as a long.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public long getIssuerEntryNumber(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getIssuerEntryData(namespaceName, name);
            return Cbor.decode(value).getAsNumber();
        }

        /**
         * Like {@link #getIssuerEntryData(String, String)} but returns the CBOR decoded
         * as a {@link Timestamp}.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull Timestamp getIssuerEntryDateTime(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getIssuerEntryData(namespaceName, name);
            return Timestamp.ofEpochMilli(Cbor.decode(value).getAsDateTimeString().toEpochMilliseconds());
        }

        // ---

        /**
         * Returns whether the {@code DeviceSigned} data was authenticated.
         *
         * <p>This returns {@code true} only if the returned device-signed data was properly
         * MACed or signed by a {@code DeviceKey} in the MSO.
         *
         * @return whether the {@code DeviceSigned} data was authenticated.
         */
        public boolean getDeviceSignedAuthenticated() {
            return mDeviceSignedAuthenticated;
        }

        /**
         * Returns whether {@code DeviceSigned} was authenticated using ECDSA signature or
         * using a MAC.
         *
         * @return {@code true} if ECDSA signature was used, {@code false} otherwise.
         */
        public boolean getDeviceSignedAuthenticatedViaSignature() {
            return mDeviceSignedAuthenticatedViaSignature;
        }

        /**
         * Gets the names of namespaces with retrieved entries of the device-signed data.
         *
         * <p>If the document doesn't contain any device-signed data, this returns the empty
         * collection.
         *
         * @return Collection of names of namespaces in the device-signed data.
         */
        public @NonNull List<String> getDeviceNamespaces() {
            return new ArrayList<>(mDeviceData.keySet());
        }

        /**
         * Gets the names of data elements in the given device-signed namespace.
         *
         * @param namespaceName the name of the namespace to get data element names from.
         * @return A collection of data element names for the namespace.
         * @exception IllegalArgumentException if the given namespace isn't in the data.
         */
        public @NonNull List<String> getDeviceEntryNames(@NonNull String namespaceName) {
            Map<String, EntryData> innerMap = mDeviceData.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace not in data");
            }
            return new ArrayList<>(innerMap.keySet());
        }

        /**
         * Gets the raw CBOR data for the value of given data element in a given namespace in
         * device-signed data.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the encoded CBOR data for the data element
         * @exception IllegalArgumentException if the given namespace or entry isn't in the data.
         */
        public @NonNull byte[] getDeviceEntryData(@NonNull String namespaceName,
                @NonNull String name) {
            Map<String, EntryData> innerMap = mDeviceData.get(namespaceName);
            if (innerMap == null) {
                throw new IllegalArgumentException("Namespace not in data");
            }
            byte[] value = innerMap.get(name).mValue;
            if (value == null) {
                throw new IllegalArgumentException("Entry not in data");
            }
            return value;
        }

        /**
         * Like {@link #getDeviceEntryData(String, String)} but returns the CBOR decoded
         * as a string.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull String getDeviceEntryString(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getDeviceEntryData(namespaceName, name);
            return Cbor.decode(value).getAsTstr();
        }

        /**
         * Like {@link #getDeviceEntryData(String, String)} but returns the CBOR decoded
         * as a byte-string.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull byte[] getDeviceEntryByteString(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getDeviceEntryData(namespaceName, name);
            return Cbor.decode(value).getAsBstr();
        }

        /**
         * Like {@link #getDeviceEntryData(String, String)} but returns the CBOR decoded
         * as a boolean.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public boolean getDeviceEntryBoolean(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getDeviceEntryData(namespaceName, name);
            return Cbor.decode(value).getAsBoolean();
        }

        /**
         * Like {@link #getDeviceEntryData(String, String)} but returns the CBOR decoded
         * as a long.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public long getDeviceEntryNumber(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getDeviceEntryData(namespaceName, name);
            return Cbor.decode(value).getAsNumber();
        }

        /**
         * Like {@link #getDeviceEntryData(String, String)} but returns the CBOR decoded
         * as a {@link Timestamp}.
         *
         * @param namespaceName the name of the namespace to get a data element value from.
         * @param name the name of the data element in the given namespace.
         * @return the decoded data.
         * @exception IllegalArgumentException if the CBOR data isn't in data or not the right type.
         */
        public @NonNull Timestamp getDeviceEntryDateTime(@NonNull String namespaceName,
                @NonNull String name) {
            byte[] value = getDeviceEntryData(namespaceName, name);
            return Timestamp.ofEpochMilli(Cbor.decode(value).getAsDateTimeString().toEpochMilliseconds());
        }

        static class Builder {
            private final Document mResult;

            Builder(@NonNull String docType) {
                this.mResult = new Document();
                this.mResult.mDocType = docType;
            }

            Builder addIssuerEntry(String namespaceName, String name, byte[] value,
                    boolean digestMatch) {
                Map<String, EntryData> innerMap = mResult.mIssuerData.get(namespaceName);
                if (innerMap == null) {
                    innerMap = new LinkedHashMap<>();
                    mResult.mIssuerData.put(namespaceName, innerMap);
                }
                innerMap.put(name, new EntryData(value, digestMatch));
                if (!digestMatch) {
                    mResult.mNumIssuerEntryDigestMatchFailures += 1;
                }
                return this;
            }

            void setIssuerCertificateChain(CertificateChain certificateChain) {
                mResult.mIssuerCertificateChain = certificateChain;
            }

            Builder addDeviceEntry(String namespaceName, String name, byte[] value) {
                Map<String, EntryData> innerMap = mResult.mDeviceData.get(namespaceName);
                if (innerMap == null) {
                    innerMap = new LinkedHashMap<>();
                    mResult.mDeviceData.put(namespaceName, innerMap);
                }
                innerMap.put(name, new EntryData(value, true));
                return this;
            }

            Builder setDeviceSignedAuthenticated(boolean deviceSignedAuthenticated) {
                mResult.mDeviceSignedAuthenticated = deviceSignedAuthenticated;
                return this;
            }

            Builder setIssuerSignedAuthenticated(boolean issuerSignedAuthenticated) {
                mResult.mIssuerSignedAuthenticated = issuerSignedAuthenticated;
                return this;
            }

            Builder setValidityInfoSigned(@NonNull Timestamp value) {
                mResult.mValidityInfoSigned = value;
                return this;
            }

            Builder setValidityInfoValidFrom(@NonNull Timestamp value) {
                mResult.mValidityInfoValidFrom = value;
                return this;
            }

            Builder setValidityInfoValidUntil(@NonNull Timestamp value) {
                mResult.mValidityInfoValidUntil = value;
                return this;
            }

            Builder setValidityInfoExpectedUpdate(@NonNull Timestamp value) {
                mResult.mValidityInfoExpectedUpdate = value;
                return this;
            }

            Builder setDeviceKey(@NonNull EcPublicKey deviceKey) {
                mResult.mDeviceKey = deviceKey;
                return this;
            }

            Builder setDeviceSignedAuthenticatedViaSignature(boolean value) {
                mResult.mDeviceSignedAuthenticatedViaSignature = value;
                return this;
            }

            Document build() {
                return mResult;
            }
        }
    }
}
