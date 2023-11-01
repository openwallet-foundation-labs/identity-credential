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

package com.android.identity.mdoc.util;

import com.android.identity.TestVectors;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialRequest;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.mso.MobileSecurityObjectParser;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.mdoc.request.DeviceRequestParser;
import com.android.identity.util.CborUtil;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import co.nstant.in.cbor.model.DataItem;

public class MdocUtilTest {
    private static final String TAG = "MdocUtilTest";

    @Test
    public void testGenerateIssuerNameSpaces() {
        Random deterministicRandomProvider = new Random(42);
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .build();

        Map<String, Map<String, byte[]>> overrides = new HashMap<>();
        Map<String, byte[]> overridesForNs1 = new HashMap<>();
        overridesForNs1.put("foo3", Util.cborEncodeString("bar3_override"));
        overrides.put("ns1", overridesForNs1);

        Map<String, List<String>> exceptions = new HashMap<>();
        exceptions.put("ns1", Arrays.asList("foo3"));
        exceptions.put("ns2", Arrays.asList("bar2"));

        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                deterministicRandomProvider,
                16,
                overrides);

        Assert.assertEquals(2, issuerNameSpaces.size());

        List<byte[]> ns1Values = issuerNameSpaces.get("ns1");
        Assert.assertEquals(3, ns1Values.size());
        List<byte[]> ns2Values = issuerNameSpaces.get("ns2");
        Assert.assertEquals(2, ns2Values.size());

        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 1,\n" +
                        "  \"random\": h'e43c084f4bbb2bf1839dee466d852cb5',\n" +
                        "  \"elementIdentifier\": \"foo1\",\n" +
                        "  \"elementValue\": \"bar1\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 2,\n" +
                        "  \"random\": h'be6a61aa9a0c6117bd6743e7dc978573',\n" +
                        "  \"elementIdentifier\": \"foo2\",\n" +
                        "  \"elementValue\": \"bar2\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(1),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 3,\n" +
                        "  \"random\": h'998e685e885cb361f86c974620bebfb0',\n" +
                        "  \"elementIdentifier\": \"foo3\",\n" +
                        "  \"elementValue\": \"bar3_override\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(2),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));

        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 4,\n" +
                        "  \"random\": h'1100b276545718c30f406cc8e3a188ff',\n" +
                        "  \"elementIdentifier\": \"bar1\",\n" +
                        "  \"elementValue\": \"foo1\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns2Values.get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 0,\n" +
                        "  \"random\": h'f11059eb6fbce62655dfbd6f83b89670',\n" +
                        "  \"elementIdentifier\": \"bar2\",\n" +
                        "  \"elementValue\": \"foo2\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns2Values.get(1),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));

        // Compare with digests above.
        Map<Long, byte[]> digests;

        digests = MdocUtil.calculateDigestsForNameSpace("ns1", issuerNameSpaces, "SHA-256");
        Assert.assertEquals(3, digests.size());
        Assert.assertEquals("3d4228384d110861f56b9b69e2720617d891cbb081393ead7aa972d37526f9db",
                Util.toHex(digests.get(1L)));
        Assert.assertEquals("1acacd599066a8408afcbba6d5ea87a03317a7a84ac5ac0d186a5e0a7ac53ca9",
                Util.toHex(digests.get(2L)));
        Assert.assertEquals("4b21f1b63a88d0d741fb10efea46e2c5d294d4e824f5c12d1f990f7b091e64ae",
                Util.toHex(digests.get(3L)));

        digests = MdocUtil.calculateDigestsForNameSpace("ns2", issuerNameSpaces, "SHA-256");
        Assert.assertEquals(2, digests.size());
        Assert.assertEquals("a1c590a4ea4de1b2c975277ade6f191b6ecdabcef8262beb83e6d923ac841e0b",
                Util.toHex(digests.get(4L)));
        Assert.assertEquals("d585cb8cd6dc901ac1e4f47b804792364f1ec067aa9acd4c5664a155b35bd081",
                Util.toHex(digests.get(0L)));

        // Check stripping
        Map<String, List<byte[]>> issuerNameSpacesStripped =
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, exceptions);
        ns1Values = issuerNameSpacesStripped.get("ns1");
        Assert.assertEquals(3, ns1Values.size());
        ns2Values = issuerNameSpacesStripped.get("ns2");
        Assert.assertEquals(2, ns2Values.size());
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 1,\n" +
                        "  \"random\": h'e43c084f4bbb2bf1839dee466d852cb5',\n" +
                        "  \"elementIdentifier\": \"foo1\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 2,\n" +
                        "  \"random\": h'be6a61aa9a0c6117bd6743e7dc978573',\n" +
                        "  \"elementIdentifier\": \"foo2\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(1),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 3,\n" +
                        "  \"random\": h'998e685e885cb361f86c974620bebfb0',\n" +
                        "  \"elementIdentifier\": \"foo3\",\n" +
                        "  \"elementValue\": \"bar3_override\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns1Values.get(2),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 4,\n" +
                        "  \"random\": h'1100b276545718c30f406cc8e3a188ff',\n" +
                        "  \"elementIdentifier\": \"bar1\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns2Values.get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 0,\n" +
                        "  \"random\": h'f11059eb6fbce62655dfbd6f83b89670',\n" +
                        "  \"elementIdentifier\": \"bar2\",\n" +
                        "  \"elementValue\": \"foo2\"\n" +
                        "} >>)",
                CborUtil.toDiagnostics(
                        ns2Values.get(1),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
    }

    @Test
    public void testGetDigestsForNameSpaceInTestVectors() {
        DataItem deviceResponse = Util.cborDecode(Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE));
        DataItem documentDataItem = Util.cborMapExtractArray(deviceResponse, "documents").get(0);

        DataItem issuerSigned = Util.cborMapExtractMap(documentDataItem, "issuerSigned");

        DataItem issuerAuthDataItem = Util.cborMapExtract(issuerSigned, "issuerAuth");
        DataItem mobileSecurityObjectBytes = Util.cborDecode(
                Util.coseSign1GetData(issuerAuthDataItem));
        DataItem mobileSecurityObject = Util.cborExtractTaggedAndEncodedCbor(
                mobileSecurityObjectBytes);
        byte[] encodedMobileSecurityObject = Util.cborEncode(mobileSecurityObject);
        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMobileSecurityObject).parse();

        DataItem nameSpaces = Util.cborMapExtractMap(issuerSigned, "nameSpaces");
        List<DataItem> arrayOfIssuerSignedItemBytes = Util.cborMapExtractArray(nameSpaces, "org.iso.18013.5.1");
        List<byte[]> issuerNamespacesForMdlNamespace = new ArrayList<>();
        for (DataItem di : arrayOfIssuerSignedItemBytes) {
            //Logger.dCbor(TAG, "di", Util.cborEncode(di));
            issuerNamespacesForMdlNamespace.add(Util.cborEncode(di));
        }
        Map<String, List<byte[]>> issuerNameSpacesFromTestVector = new LinkedHashMap<>();
        issuerNameSpacesFromTestVector.put("org.iso.18013.5.1", issuerNamespacesForMdlNamespace);

        Map<Long, byte[]> digestsCalculatedFromResponseInTestVector = MdocUtil.calculateDigestsForNameSpace(
                "org.iso.18013.5.1",
                issuerNameSpacesFromTestVector,
                "SHA-256");

        Map<Long, byte[]> digestsListedInMsoInTestVector = mso.getDigestIDs("org.iso.18013.5.1");

        // Note: Because of selective disclosure, the response doesn't contain all the data
        // elements listed in the MSO... and we can only test what's in the response. So we
        // need to start from there
        //
        for (long digestId : digestsCalculatedFromResponseInTestVector.keySet()) {
            byte[] calculatedDigest = digestsCalculatedFromResponseInTestVector.get(digestId);
            byte[] digestInMso = digestsListedInMsoInTestVector.get(digestId);
            Assert.assertArrayEquals(calculatedDigest, digestInMso);
        }
    }

    @Test
    public void testGenerateCredentialRequest() {
        byte[] encodedSessionTranscriptBytes = Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES);
        byte[] encodedSessionTranscript = Util.cborEncode(
                Util.cborExtractTaggedAndEncodedCbor(
                        Util.cborDecode(encodedSessionTranscriptBytes)));

        DeviceRequestParser parser = new DeviceRequestParser();
        parser.setDeviceRequest(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST));
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceRequestParser.DeviceRequest request = parser.parse();

        CredentialRequest.DataElement[] elementsInRequest = {
                new CredentialRequest.DataElement("org.iso.18013.5.1", "family_name", true),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "document_number", true),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "driving_privileges", true),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "issue_date", true),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "expiry_date", true),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "portrait", false),
        };

        CredentialRequest cr = MdocUtil.generateCredentialRequest(request.getDocumentRequests().get(0));
        Assert.assertEquals(elementsInRequest.length, cr.getRequestedDataElements().size());
        int n = 0;
        for (CredentialRequest.DataElement element : cr.getRequestedDataElements()) {
            CredentialRequest.DataElement otherElement = elementsInRequest[n++];
            Assert.assertEquals(element.getNameSpaceName(), otherElement.getNameSpaceName());
            Assert.assertEquals(element.getDataElementName(), otherElement.getDataElementName());
            Assert.assertEquals(element.getIntentToRetain(), otherElement.getIntentToRetain());
        }
    }
}
