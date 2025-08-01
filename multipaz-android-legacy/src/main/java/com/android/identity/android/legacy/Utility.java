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

package com.android.identity.android.legacy;

import android.content.Context;
import android.icu.util.Calendar;

import androidx.annotation.Nullable;

import androidx.annotation.NonNull;

import org.multipaz.crypto.Algorithm;
import org.multipaz.crypto.EcCurve;
import org.multipaz.crypto.EcPublicKeyJvmKt;
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator;
import org.multipaz.mdoc.response.DeviceResponseGenerator;
import org.multipaz.util.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.UnicodeString;
import kotlin.time.Instant;

/**
 * Miscellaneous utility functions that are useful when building mdoc applications.
 */
public class Utility {
    private static final String TAG = "Utility";

    // Not called.
    private Utility() {
    }

    /**
     * Merges issuer-signed data with credential result data.
     *
     * @param issuerSignedMapping A mapping obtained from
     *                            {@link org.multipaz.mdoc.mso.StaticAuthDataParser}.
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
                Logger.INSTANCE.w(TAG, "Skipping namespace " + namespaceName + " which is not in "
                        + "issuerSignedMapping");
            } else {
                Collection<String> entryNames = issuerSigned.getEntryNames(namespaceName);
                for (byte[] encodedTaggedIssuerSignedItem : encodedIssuerSignedItemForNs) {
                    byte[] encodedIssuerSignedItem = Util.cborExtractTaggedCbor(encodedTaggedIssuerSignedItem);
                    DataItem issuerSignedItem = Util.cborDecode(encodedIssuerSignedItem);
                    String elemName = Util.cborMapExtractString(issuerSignedItem, "elementIdentifier");

                    if (!entryNames.contains(elemName)) {
                        continue;
                    }
                    byte[] elemValue = issuerSigned.getEntry(namespaceName, elemName);
                    if (elemValue != null) {
                        byte[] encodedIssuerSignedItemWithValue =
                                Util.issuerSignedItemSetValue(encodedIssuerSignedItem, elemValue);

                        newEncodedIssuerSignedItemForNs.add(
                                Util.cborEncode(Util.cborBuildTaggedByteString(encodedIssuerSignedItemWithValue)));
                    }
                }
            }

            if (newEncodedIssuerSignedItemForNs.size() > 0) {
                newIssuerSignedMapping.put(namespaceName, newEncodedIssuerSignedItemForNs);
            }
        }
        return newIssuerSignedMapping;
    }

    /**
     * Like {@link DeviceResponseGenerator#addDocument(String, byte[], byte[], byte[], Map, Map, byte[])}
     * but takes a
     * {@link CredentialDataResult} instead and merges the results into the "elementValue"
     * entry of each IssuerSignedItem value.
     *
     * <p>Note: The <code>issuerSignedData</code> and <code>encodedIssuerAuth</code> are
     * parameters usually obtained via {@link org.multipaz.mdoc.mso.StaticAuthDataParser}.
     *
     * @param deviceResponseGenerator The generator to add the document to.
     * @param docType              The type of the document to send.
     * @param credentialDataResult The device- and issuer-signed data elements to include.
     * @param issuerSignedMapping A mapping from namespaces to an array of IssuerSignedItemBytes
     *                            CBOR for the namespace. The "elementValue" value in each
     *                            IssuerSignedItem CBOR must be set to the NULL value.
     * @param encodedIssuerAuth   the bytes of <code>COSE_Sign1</code> signed by the issuing
     *                            authority and where the payload is set to
     *                            <code>MobileSecurityObjectBytes</code>.
     * @return                    the generator.
     */
    public static @NonNull DeviceResponseGenerator addDocument(
            @NonNull DeviceResponseGenerator deviceResponseGenerator,
            @NonNull String docType,
            @NonNull CredentialDataResult credentialDataResult,
            @NonNull Map<String, List<byte[]>> issuerSignedMapping,
            @Nullable Map<String, Map<String, Long>> errors,
            @NonNull byte[] encodedIssuerAuth) {
        Map<String, List<byte[]>> issuerSignedMappingWithData =
                mergeIssuerSigned(issuerSignedMapping,
                        credentialDataResult.getIssuerSignedEntries());
        return deviceResponseGenerator.addDocument(docType,
                credentialDataResult.getDeviceNameSpaces(),
                credentialDataResult.getDeviceSignature(),
                credentialDataResult.getDeviceMac(),
                issuerSignedMappingWithData,
                errors,
                encodedIssuerAuth);
    }


    public static IdentityCredentialStore getIdentityCredentialStore(@NonNull Context context) {
        // We generally want to run all tests against the software implementation since
        // hardware-based implementations are already tested against CTS and VTS and the bulk
        // of the code in the Jetpack is the software implementation. This also helps avoid
        // whatever bugs or flakiness that may exist in hardware implementations.
        //
        // Occasionally it's useful for a developer to test that the hardware-backed paths
        // (HardwareIdentityCredentialStore + friends) work as intended. This can be done by
        // uncommenting the line below and making sure it runs on a device with the appropriate
        // hardware support.
        //
        // See b/164480361 for more discussion.
        //
        //return IdentityCredentialStore.getHardwareInstance(context);
        return IdentityCredentialStore.getKeystoreInstance(context, context.getNoBackupFilesDir());
    }

    @SuppressWarnings("deprecation")
    public static void provisionMsosForExistingCredential(
            @Nullable IdentityCredential c,
            @NonNull PrivateKey issuingAuthorityKey,
            @NonNull X509Certificate issuingAuthorityCertificate,
            @NonNull String docType,
            @NonNull PersonalizationData personalizationData,
            int numAuthKeys,
            int maxUsesPerKey)
            throws IdentityCredentialException {
        c.setAvailableAuthenticationKeys(numAuthKeys, maxUsesPerKey);
        Collection<X509Certificate> authKeysNeedCert = c.getAuthKeysNeedingCertification();

        final Timestamp signedDate = Timestamp.now();
        final Timestamp validFromDate = Timestamp.now();
        Calendar validToCalendar = Calendar.getInstance();
        validToCalendar.add(Calendar.MONTH, 12);
        final Timestamp validToDate = Timestamp.ofEpochMilli(validToCalendar.getTimeInMillis());

        for (X509Certificate authKeyCert : authKeysNeedCert) {
            PublicKey authKey = authKeyCert.getPublicKey();

            MobileSecurityObjectGenerator msoGenerator =
                    new MobileSecurityObjectGenerator(
                            Algorithm.SHA256,
                            docType,
                            EcPublicKeyJvmKt.toEcPublicKey(authKey, EcCurve.P256)
                    ).setValidityInfo(
                            Instant.Companion.fromEpochMilliseconds(signedDate.toEpochMilli()),
                            Instant.Companion.fromEpochMilliseconds(validFromDate.toEpochMilli()),
                            Instant.Companion.fromEpochMilliseconds(validToDate.toEpochMilli()),
                            null
                    );

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

                    DataItem issuerSignedItem =
                            new CborBuilder()
                                    .addMap()
                                    .put("digestID", digestId)
                                    .put("random", random)
                                    .put("elementIdentifier", entry)
                                    .put(new UnicodeString("elementValue"), value)
                                    .end()
                                    .build()
                                    .get(0);
                    byte[] encodedIssuerSignedItem = Util.cborEncode(issuerSignedItem);

                    byte[] digest = null;
                    try {
                        // For the digest, it's of the _tagged_ bstr so wrap it
                        byte[] encodedIssuerSignedItemBytes =
                                Util.cborEncode(Util.cborBuildTaggedByteString(encodedIssuerSignedItem));
                        digest = MessageDigest.getInstance("SHA-256").digest(encodedIssuerSignedItemBytes);
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

            byte[] taggedEncodedMso =
                    Util.cborEncode(Util.cborBuildTaggedByteString(encodedMobileSecurityObject));

            // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
            //
            // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
            //
            ArrayList<X509Certificate> issuerAuthorityCertChain = new ArrayList<>();
            issuerAuthorityCertChain.add(issuingAuthorityCertificate);
            byte[] encodedIssuerAuth =
                    Util.cborEncode(
                            Util.coseSign1Sign(
                                    issuingAuthorityKey,
                                    "SHA256withECDSA",
                                    taggedEncodedMso,
                                    null,
                                    issuerAuthorityCertChain));

            // Store issuerSignedMapping and issuerAuth (the MSO) in staticAuthData...
            //
            byte[] staticAuthData = encodeStaticAuthData(issuerSignedMapping, encodedIssuerAuth);
            c.storeStaticAuthenticationData(authKeyCert, validToCalendar, staticAuthData);
        } // for each authkey
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

}