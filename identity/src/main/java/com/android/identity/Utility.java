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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.icu.util.Calendar;
import android.util.Log;
import androidx.core.util.Pair;

import androidx.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Miscellaneous utility functions that are useful when building mdoc applications.
 */
public class Utility {
    private static final String TAG = "Utility";

    // Not called.
    private Utility() {
    }

    /**
     * Helper to encode digest-id mapping and issuerAuth CBOR into a single byte array.
     *
     * <p>The resulting byte array can be stored as <code>staticAuthData</code> using
     * {@link IdentityCredential#storeStaticAuthenticationData(X509Certificate, Calendar, byte[])}
     * and returned using {@link ResultData#getStaticAuthenticationData()} at presentation time.
     *
     * <p>Use {@link #decodeStaticAuthData(byte[])} for the reverse operation.
     *
     * <p>The returned data are the bytes of CBOR with the following CDDL:
     * <pre>
     *     StaticAuthData = {
     *         "digestIdMapping": DigestIdMapping,
     *         "issuerAuth" : IssuerAuth
     *     }
     *
     *     DigestIdMapping = {
     *         NameSpace =&gt; [ + IssuerSignedItemBytes ]
     *     }
     *
     *     ; Defined in ISO 18013-5
     *     ;
     *     NameSpace = String
     *     DataElementIdentifier = String
     *     DigestID = uint
     *     IssuerAuth = COSE_Sign1 ; The payload is MobileSecurityObjectBytes
     *
     *     IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem)
     *
     *     IssuerSignedItem = {
     *       "digestID" : uint,                           ; Digest ID for issuer data auth
     *       "random" : bstr,                             ; Random value for issuer data auth
     *       "elementIdentifier" : DataElementIdentifier, ; Data element identifier
     *       "elementValue" : NULL                        ; Placeholder for Data element value
     *     }
     * </pre>
     *
     * <p>Note that the the byte[] arrays returned in the list of map values are
     * the bytes of IssuerSignedItem, not IssuerSignedItemBytes.
     *
     * @param issuerSignedMapping A mapping from namespaces into a list of the bytes of
     *                            IssuerSignedItem (not tagged). The elementValue key
     *                            must be present and set to the NULL value.
     * @param encodedIssuerAuth   The bytes of <code>COSE_Sign1</code> signed by the issuing
     *                            authority and where the payload is set to bytes of
     *                            <code>MobileSecurityObjectBytes</code>.
     * @return the bytes of the CBOR described above.
     */
    public static @NonNull
    byte[] encodeStaticAuthData(
            @NonNull Map<String, List<byte[]>> issuerSignedMapping,
            @NonNull byte[] encodedIssuerAuth) {

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> outerBuilder = builder.addMap();
        for (Map.Entry<String, List<byte[]>> oe : issuerSignedMapping.entrySet()) {
            String ns = oe.getKey();
            ArrayBuilder<MapBuilder<CborBuilder>> innerBuilder = outerBuilder.putArray(ns);
            for (byte[] encodedIssuerSignedItem : oe.getValue()) {
                // This API specifies that these are just the bytes of IssuerSignedItem, not
                // the bytes of a tagged bytestring.
                DataItem issuerSignedItem = Util.cborDecode(encodedIssuerSignedItem);

                // Ensure that elementValue is NULL to avoid applications or issuers that send
                // the raw DataElementValue in the IssuerSignedItem. If we allowed non-NULL
                // values, then PII would be exposed that would otherwise be guarded by
                // access control checks.
                DataItem value = Util.cborMapExtract(issuerSignedItem, "elementValue");
                if (!(value instanceof SimpleValue)
                        || ((SimpleValue) value).getSimpleValueType() != SimpleValueType.NULL) {
                    String name = Util.cborMapExtractString(issuerSignedItem, "elementIdentifier");
                    throw new IllegalArgumentException("elementValue for nameSpace " + ns
                            + " elementName " + name + " is not NULL");
                }

                // We'll do the tagging.
                innerBuilder.add(Util.cborBuildTaggedByteString(encodedIssuerSignedItem));
            }
        }
        DataItem digestIdMappingItem = builder.build().get(0);

        byte[] staticAuthData = Util.cborEncode(new CborBuilder()
                .addMap()
                .put(new UnicodeString("digestIdMapping"), digestIdMappingItem)
                .put(new UnicodeString("issuerAuth"), Util.cborDecode(encodedIssuerAuth))
                .end()
                .build().get(0));
        return staticAuthData;
    }

    /**
     * Helper to decode <code>staticAuthData</code> in the format specified by the
     * {@link #encodeStaticAuthData(Map, byte[])} method.
     *
     * <p>Note that the the byte[] arrays returned in the list of map values are
     * the bytes of IssuerSignedItem, not IssuerSignedItemBytes.
     *
     * @param staticAuthData the bytes of CBOR as described above.
     * @return <code>issuerSignedMapping</code> and <code>encodedIssuerAuth</code>.
     * @throws IllegalArgumentException if the given data is not in the format specified by the
     *                                  {@link #encodeStaticAuthData(Map, byte[])} method.
     */
    public static @NonNull
    Pair<Map<String, List<byte[]>>, byte[]>
    decodeStaticAuthData(@NonNull byte[] staticAuthData) {
        DataItem topMapItem = Util.cborDecode(staticAuthData);
        if (!(topMapItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Top-level is not a map");
        }
        co.nstant.in.cbor.model.Map topMap = (co.nstant.in.cbor.model.Map) topMapItem;
        DataItem issuerAuthItem = topMap.get(new UnicodeString("issuerAuth"));
        if (issuerAuthItem == null) {
            throw new IllegalArgumentException("issuerAuth item does not exist");
        }
        byte[] encodedIssuerAuth = Util.cborEncode(issuerAuthItem);

        Map<String, List<byte[]>> buildOuterMap = new HashMap<>();

        DataItem outerMapItem = topMap.get(new UnicodeString("digestIdMapping"));
        if (!(outerMapItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException(
                    "digestIdMapping value is not a map or does not exist");
        }
        co.nstant.in.cbor.model.Map outerMap = (co.nstant.in.cbor.model.Map) outerMapItem;
        for (DataItem outerKey : outerMap.getKeys()) {
            if (!(outerKey instanceof UnicodeString)) {
                throw new IllegalArgumentException("Outer key is not a string");
            }
            String ns = ((UnicodeString) outerKey).getString();

            List<byte[]> buildInnerArray = new ArrayList<>();
            buildOuterMap.put(ns, buildInnerArray);

            DataItem outerValue = outerMap.get(outerKey);
            if (!(outerValue instanceof co.nstant.in.cbor.model.Array)) {
                throw new IllegalArgumentException("Outer value is not an array");
            }
            co.nstant.in.cbor.model.Array innerArray = (co.nstant.in.cbor.model.Array) outerValue;
            for (DataItem innerKey : innerArray.getDataItems()) {
                if (!(innerKey instanceof ByteString)) {
                    throw new IllegalArgumentException("Inner key is not a bstr");
                }
                if (innerKey.getTag().getValue() != 24) {
                    throw new IllegalArgumentException("Inner key does not have tag 24");
                }
                byte[] encodedIssuerSignedItemBytes = ((ByteString) innerKey).getBytes();

                // Strictly not necessary but check that elementValue is NULL. This is to
                // avoid applications (or issuers) sending the value in issuerSignedMapping
                // which is part of staticAuthData. This would be bad because then the
                // data element value would be available without any access control checks.
                //
                DataItem issuerSignedItem = Util.cborExtractTaggedAndEncodedCbor(innerKey);
                DataItem value = Util.cborMapExtract(issuerSignedItem, "elementValue");
                if (!(value instanceof SimpleValue)
                        || ((SimpleValue) value).getSimpleValueType() != SimpleValueType.NULL) {
                    String name = Util.cborMapExtractString(issuerSignedItem, "elementIdentifier");
                    throw new IllegalArgumentException("elementValue for nameSpace " + ns
                            + " elementName " + name + " is not NULL");
                }

                buildInnerArray.add(encodedIssuerSignedItemBytes);
            }
        }

        return new Pair<>(buildOuterMap, encodedIssuerAuth);
    }


    /**
     * Helper function to create a self-signed credential, including authentication keys and
     * static authentication data.
     *
     * <p>The created authentication keys will have associated <code>staticAuthData</code>
     * which is encoded in the same format as returned by
     * the {@link #encodeStaticAuthData(Map, byte[])} helper method meaning that at
     * presentation-time the {@link #decodeStaticAuthData(byte[])} helper can be used to recover
     * the digest-id mapping and <code>IssuerAuth</code> CBOR.
     *
     * <p>This helper is useful only when developing mdoc applications that are not yet
     * using a live issuing authority.
     *
     * @param store                       the {@link IdentityCredentialStore} to create the
     *                                    credential in.
     * @param credentialName              name to use for the credential, e.g. "test".
     * @param issuingAuthorityKey         the private key to use for signing the static auth data.
     * @param issuingAuthorityCertificate the certificate corresponding the signing key.
     * @param docType                     the document type of the credential, e.g. "org.iso
     *                                    .18013.5.1.mDL".
     * @param personalizationData         the data to put in the document, organized by namespace.
     * @param numAuthKeys                 number of authentication keys to create.
     * @param maxUsesPerKey               number of uses for each authentication key.
     * @return bytes of a COSE_Sign1 for proof of provisioning
     */
    @SuppressWarnings("deprecation")
    public static
    @NonNull byte[] provisionSelfSignedCredential(
            @NonNull IdentityCredentialStore store,
            @NonNull String credentialName,
            @NonNull PrivateKey issuingAuthorityKey,
            @NonNull X509Certificate issuingAuthorityCertificate,
            @NonNull String docType,
            @NonNull PersonalizationData personalizationData,
            int numAuthKeys,
            int maxUsesPerKey) throws IdentityCredentialException {

        final byte[] provisioningChallenge = "dummyChallenge".getBytes(UTF_8);

        store.deleteCredentialByName(credentialName);
        WritableIdentityCredential wc = store.createCredential(credentialName, docType);

        Collection<X509Certificate> certChain = wc.getCredentialKeyCertificateChain(provisioningChallenge);
        Log.i(TAG, String.format(Locale.US, "Cert chain for self-signed credential '%s' has %d elements",
                credentialName, certChain.size()));
        int certNum = 0;
        for (X509Certificate certificate : certChain) {
            try {
                Log.i(TAG, String.format(Locale.US, "Certificate %d: %s",
                        certNum++, Util.toHex(certificate.getEncoded())));
            } catch (CertificateEncodingException e) {
                e.printStackTrace();
            }
        }
        byte[] signedPop = wc.personalize(personalizationData);

        IdentityCredential c = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        c.setAvailableAuthenticationKeys(numAuthKeys, maxUsesPerKey);
        Collection<X509Certificate> authKeysNeedCert = c.getAuthKeysNeedingCertification();

        final Timestamp signedDate = Timestamp.now();
        final Timestamp validFromDate = Timestamp.now();
        Calendar validToCalendar = Calendar.getInstance();
        validToCalendar.add(Calendar.MONTH, 12);
        final Timestamp validToDate = Timestamp.ofEpochMilli(validToCalendar.getTimeInMillis());

        for (X509Certificate authKeyCert : authKeysNeedCert) {
            PublicKey authKey = authKeyCert.getPublicKey();

            MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator("SHA-256",
                    docType, authKey).setValidityInfo(signedDate, validFromDate, validToDate, null);

            Random r = new SecureRandom();

            // Count number of entries and generate digest ids
            int numEntries = 0;
            for (PersonalizationData.NamespaceData nsd : personalizationData.getNamespaceDatas()) {
                numEntries += nsd.getEntryNames().size();
            }
            List<Long> digestIds = new ArrayList<>();
            for (Long n = 0L; n < numEntries; n++) {
                digestIds.add(n);
            }
            Collections.shuffle(digestIds);

            HashMap<String, List<byte[]>> issuerSignedMapping = new HashMap<>();

            Iterator<Long> digestIt = digestIds.iterator();
            for (PersonalizationData.NamespaceData nsd : personalizationData.getNamespaceDatas()) {
                String ns = nsd.getNamespaceName();

                List<byte[]> innerArray = new ArrayList<>();

                Map<Long, byte[]> vdInner = new HashMap<>();

                for (String entry : nsd.getEntryNames()) {
                    byte[] encodedValue = nsd.getEntryValue(entry);
                    Long digestId = digestIt.next();
                    byte[] random = new byte[16];
                    r.nextBytes(random);
                    DataItem value = Util.cborDecode(encodedValue);

                    DataItem issuerSignedItem = new CborBuilder()
                            .addMap()
                            .put("digestID", digestId)
                            .put("random", random)
                            .put("elementIdentifier", entry)
                            .put(new UnicodeString("elementValue"), value)
                            .end()
                            .build().get(0);
                    byte[] encodedIssuerSignedItem = Util.cborEncode(issuerSignedItem);

                    byte[] digest = null;
                    try {
                        // For the digest, it's of the _tagged_ bstr so wrap it
                        byte[] encodedIssuerSignedItemBytes =
                                Util.cborEncode(Util.cborBuildTaggedByteString(
                                        encodedIssuerSignedItem));
                        digest = MessageDigest.getInstance("SHA-256").digest(
                                encodedIssuerSignedItemBytes);
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalArgumentException("Failed creating digester", e);
                    }

                    // Replace elementValue in encodedIssuerSignedItem with NULL value.
                    //
                    byte[] encodedIssuerSignedItemCleared =
                            Util.issuerSignedItemClearValue(encodedIssuerSignedItem);
                    innerArray.add(encodedIssuerSignedItemCleared);

                    vdInner.put(digestId, digest);
                }

                issuerSignedMapping.put(ns, innerArray);

                msoGenerator.addDigestIdsForNamespace(ns, vdInner);
            }

            byte[] encodedMobileSecurityObject = msoGenerator.generate();

            byte[] taggedEncodedMso = Util.cborEncode(
                    Util.cborBuildTaggedByteString(encodedMobileSecurityObject));

            // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
            //
            // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
            //
            ArrayList<X509Certificate> issuerAuthorityCertChain = new ArrayList<>();
            issuerAuthorityCertChain.add(issuingAuthorityCertificate);
            byte[] encodedIssuerAuth =
                    Util.cborEncode(Util.coseSign1Sign(issuingAuthorityKey,
                            "SHA256withECDSA", taggedEncodedMso,
                            null,
                            issuerAuthorityCertChain));

            // Store issuerSignedMapping and issuerAuth (the MSO) in staticAuthData...
            //
            byte[] staticAuthData = encodeStaticAuthData(
                    issuerSignedMapping, encodedIssuerAuth);
            c.storeStaticAuthenticationData(authKeyCert,
                    validToCalendar,
                    staticAuthData);

        } // for each authkey

        return signedPop;
    }


    /**
     * Merges issuer-signed data with credential result data.
     *
     * @param issuerSignedMapping A mapping obtained from {@link #decodeStaticAuthData(byte[])}.
     * @param issuerSigned        Data values from a credential.
     * @return The given mapping but where each <code>encodedIssuerAuth</code> has the given
     * data values filled in.
     */
    public static @NonNull
    Map<String, List<byte[]>> mergeIssuerSigned(
            @NonNull Map<String, List<byte[]>> issuerSignedMapping,
            @NonNull CredentialDataResult.Entries issuerSigned) {

        Map<String, List<byte[]>> newIssuerSignedMapping = new HashMap<>();

        for (String namespaceName : issuerSigned.getNamespaces()) {
            List<byte[]> newEncodedIssuerSignedItemForNs = new ArrayList<>();

            List<byte[]> encodedIssuerSignedItemForNs = issuerSignedMapping.get(namespaceName);
            if (encodedIssuerSignedItemForNs == null) {
                // Fine if this is null, the verifier might have requested elements in a namespace
                // we have no issuer-signed values for.
                Log.w(TAG, "Skipping namespace " + namespaceName + " which is not in "
                        + "issuerSignedMapping");
            } else {
                Collection<String> entryNames = issuerSigned.getEntryNames(namespaceName);
                for (byte[] encodedIssuerSignedItem : encodedIssuerSignedItemForNs) {
                    DataItem issuerSignedItem = Util.cborDecode(encodedIssuerSignedItem);
                    String elemName = Util
                            .cborMapExtractString(issuerSignedItem, "elementIdentifier");

                    if (!entryNames.contains(elemName)) {
                        continue;
                    }
                    byte[] elemValue = issuerSigned.getEntry(namespaceName, elemName);
                    if (elemValue != null) {
                        byte[] encodedIssuerSignedItemWithValue =
                                Util.issuerSignedItemSetValue(encodedIssuerSignedItem, elemValue);

                        newEncodedIssuerSignedItemForNs.add(encodedIssuerSignedItemWithValue);
                    }
                }
            }

            if (newEncodedIssuerSignedItemForNs.size() > 0) {
                newIssuerSignedMapping.put(namespaceName, newEncodedIssuerSignedItemForNs);
            }
        }
        return newIssuerSignedMapping;
    }

}
