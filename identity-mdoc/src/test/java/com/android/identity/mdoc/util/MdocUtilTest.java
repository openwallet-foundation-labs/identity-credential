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

import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DataItem;
import com.android.identity.cbor.DiagnosticOption;
import com.android.identity.cbor.Tstr;
import com.android.identity.cose.CoseSign1;
import com.android.identity.crypto.Algorithm;
import com.android.identity.mdoc.TestVectors;
import com.android.identity.credential.CredentialRequest;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.mso.MobileSecurityObjectParser;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.mdoc.request.DeviceRequestParser;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.random.RandomKt;

public class MdocUtilTest {
    private static final String TAG = "MdocUtilTest";

    @Before
    public void setup() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @Test
    public void testGenerateIssuerNameSpaces() {
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .build();

        Map<String, Map<String, byte[]>> overrides = new HashMap<>();
        Map<String, byte[]> overridesForNs1 = new HashMap<>();
        overridesForNs1.put("foo3", Cbor.encode(new Tstr("bar3_override")));
        overrides.put("ns1", overridesForNs1);

        Map<String, List<String>> exceptions = new HashMap<>();
        exceptions.put("ns1", Arrays.asList("foo3"));
        exceptions.put("ns2", Arrays.asList("bar2"));

        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nameSpacedData,
                RandomKt.Random(42),
                16,
                overrides);

        Assert.assertEquals(2, issuerNameSpaces.size());

        List<byte[]> ns1Values = issuerNameSpaces.get("ns1");
        Assert.assertEquals(3, ns1Values.size());
        List<byte[]> ns2Values = issuerNameSpaces.get("ns2");
        Assert.assertEquals(2, ns2Values.size());

        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 0,\n" +
                        "  \"random\": h'2b714e4520aabd26420972f8d80c48fa',\n" +
                        "  \"elementIdentifier\": \"foo1\",\n" +
                        "  \"elementValue\": \"bar1\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(0),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 4,\n" +
                        "  \"random\": h'46905e101902b697c6132dacdfbf5a4b',\n" +
                        "  \"elementIdentifier\": \"foo2\",\n" +
                        "  \"elementValue\": \"bar2\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(1),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 2,\n" +
                        "  \"random\": h'55b35d23743b5af1e1f2d3976124091d',\n" +
                        "  \"elementIdentifier\": \"foo3\",\n" +
                        "  \"elementValue\": \"bar3_override\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(2),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));

        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 1,\n" +
                        "  \"random\": h'320908313266a9a296f81c7b45ffdecd',\n" +
                        "  \"elementIdentifier\": \"bar1\",\n" +
                        "  \"elementValue\": \"foo1\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns2Values.get(0),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 3,\n" +
                        "  \"random\": h'4d35a35d5f23ec7b62554c512dc211b3',\n" +
                        "  \"elementIdentifier\": \"bar2\",\n" +
                        "  \"elementValue\": \"foo2\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns2Values.get(1),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));

        // Compare with digests above.
        Map<Long, byte[]> digests =
                MdocUtil.calculateDigestsForNameSpace("ns1", issuerNameSpaces, Algorithm.SHA256);
        Assert.assertEquals(3, digests.size());
        Assert.assertEquals("9f10afbca223fcfe0ee9f239e995cfe79e7f845b68981a4a0943706717c64efa",
                Util.toHex(digests.get(0L)));
        Assert.assertEquals("a5e74b031ea380267d39905981ea80c68178229219556ffd72d312a0366a7d63",
                Util.toHex(digests.get(4L)));
        Assert.assertEquals("03f0ac0623c2eaefd76bcbca00df782d84f544cf7ac1b1f9ed46144275e1d47c",
                Util.toHex(digests.get(2L)));

        digests = MdocUtil.calculateDigestsForNameSpace("ns2", issuerNameSpaces, Algorithm.SHA256);
        Assert.assertEquals(2, digests.size());
        Assert.assertEquals("fd69be5fcc0df04ae78e147bb3ad95ce4ecff51028322cccf02195f36612a212",
                Util.toHex(digests.get(1L)));
        Assert.assertEquals("47083a3473ddfcf3c8cc00f2035ac41d0b791fc50106be416c068536c249c0dd",
                Util.toHex(digests.get(3L)));

        // Check stripping
        Map<String, List<byte[]>> issuerNameSpacesStripped =
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, exceptions);
        ns1Values = issuerNameSpacesStripped.get("ns1");
        Assert.assertEquals(3, ns1Values.size());
        ns2Values = issuerNameSpacesStripped.get("ns2");
        Assert.assertEquals(2, ns2Values.size());
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 0,\n" +
                        "  \"random\": h'2b714e4520aabd26420972f8d80c48fa',\n" +
                        "  \"elementIdentifier\": \"foo1\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(0),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 4,\n" +
                        "  \"random\": h'46905e101902b697c6132dacdfbf5a4b',\n" +
                        "  \"elementIdentifier\": \"foo2\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(1),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 2,\n" +
                        "  \"random\": h'55b35d23743b5af1e1f2d3976124091d',\n" +
                        "  \"elementIdentifier\": \"foo3\",\n" +
                        "  \"elementValue\": \"bar3_override\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns1Values.get(2),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 1,\n" +
                        "  \"random\": h'320908313266a9a296f81c7b45ffdecd',\n" +
                        "  \"elementIdentifier\": \"bar1\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns2Values.get(0),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
        Assert.assertEquals("24(<< {\n" +
                        "  \"digestID\": 3,\n" +
                        "  \"random\": h'4d35a35d5f23ec7b62554c512dc211b3',\n" +
                        "  \"elementIdentifier\": \"bar2\",\n" +
                        "  \"elementValue\": \"foo2\"\n" +
                        "} >>)",
                Cbor.toDiagnostics(
                        ns2Values.get(1),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));
    }

    @Test
    public void testGetDigestsForNameSpaceInTestVectors() {
        DataItem deviceResponse = Cbor.decode(Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE));
        DataItem documentDataItem = deviceResponse.get("documents").get(0);

        DataItem issuerSigned = documentDataItem.get("issuerSigned");
        DataItem issuerAuthDataItem = issuerSigned.get("issuerAuth");
        CoseSign1 issuerAuthSignature = issuerAuthDataItem.getAsCoseSign1();

        DataItem mobileSecurityObject = Cbor.decode(issuerAuthSignature.getPayload())
                .getAsTaggedEncodedCbor();
        byte[] encodedMobileSecurityObject = Cbor.encode(mobileSecurityObject);

        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMobileSecurityObject).parse();

        DataItem nameSpaces = issuerSigned.get("nameSpaces");
        List<DataItem> arrayOfIssuerSignedItemBytes = nameSpaces.get("org.iso.18013.5.1").getAsArray();
        List<byte[]> issuerNamespacesForMdlNamespace = new ArrayList<>();
        for (DataItem di : arrayOfIssuerSignedItemBytes) {
            issuerNamespacesForMdlNamespace.add(Cbor.encode(di));
        }
        Map<String, List<byte[]>> issuerNameSpacesFromTestVector = new LinkedHashMap<>();
        issuerNameSpacesFromTestVector.put("org.iso.18013.5.1", issuerNamespacesForMdlNamespace);

        Map<Long, byte[]> digestsCalculatedFromResponseInTestVector = MdocUtil.calculateDigestsForNameSpace(
                "org.iso.18013.5.1",
                issuerNameSpacesFromTestVector,
                Algorithm.SHA256);

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
        byte[] encodedSessionTranscript = Cbor.encode(
                Cbor.decode(encodedSessionTranscriptBytes).getAsTaggedEncodedCbor()
        );

        DeviceRequestParser parser = new DeviceRequestParser();
        parser.setDeviceRequest(Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST));
        parser.setSessionTranscript(encodedSessionTranscript);
        DeviceRequestParser.DeviceRequest request = parser.parse();

        CredentialRequest.DataElement[] elementsInRequest = {
                new CredentialRequest.DataElement("org.iso.18013.5.1", "family_name", true, false),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "document_number", true, false),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "driving_privileges", true, false),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "issue_date", true, false),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "expiry_date", true, false),
                new CredentialRequest.DataElement("org.iso.18013.5.1", "portrait", false, false),
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
