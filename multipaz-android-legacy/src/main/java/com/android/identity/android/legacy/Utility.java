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
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.annotation.NonNull;

import org.multipaz.mdoc.response.DeviceResponseGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.model.DataItem;

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
                Log.w(TAG, "Skipping namespace " + namespaceName + " which is not in "
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
}