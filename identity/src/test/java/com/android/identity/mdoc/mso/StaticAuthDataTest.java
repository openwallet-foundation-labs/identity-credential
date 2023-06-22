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

package com.android.identity.mdoc.mso;

import com.android.identity.util.CborUtil;
import com.android.identity.internal.Util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;

@SuppressWarnings("deprecation")
public class StaticAuthDataTest {

    private Map<String, List<byte[]>> createValidDigestIdMapping() {
        DataItem issuerSignedItemMetadata = new CborBuilder()
                .addMap()
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("digestID", 42)
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        DataItem isiMetadataBytes =
                Util.cborBuildTaggedByteString(Util.cborEncode(issuerSignedItemMetadata));
        byte[] encodedIsiMetadataBytes = Util.cborEncode(isiMetadataBytes);

        DataItem issuerSignedItemMetadata2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        DataItem isiMetadata2Bytes =
                Util.cborBuildTaggedByteString(Util.cborEncode(issuerSignedItemMetadata2));
        byte[] encodedIsiMetadata2Bytes = Util.cborEncode(isiMetadata2Bytes);

        Map<String, List<byte[]>> issuerSignedMapping = new HashMap<>();
        issuerSignedMapping.put("org.namespace",
                Arrays.asList(encodedIsiMetadataBytes, encodedIsiMetadata2Bytes));
        return issuerSignedMapping;
    }

    private Map<String, List<byte[]>> createInvalidElementValueDigestIdMapping() {
        DataItem issuerSignedItemMetadata = new CborBuilder()
                .addMap()
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("digestID", 42)
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        DataItem isiMetadataBytes =
                Util.cborBuildTaggedByteString(Util.cborEncode(issuerSignedItemMetadata));
        byte[] encodedIsiMetadataBytes = Util.cborEncode(isiMetadataBytes);

        DataItem issuerSignedItemMetadata2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.TRUE)
                .end()
                .build().get(0);
        DataItem isiMetadata2Bytes =
                Util.cborBuildTaggedByteString(Util.cborEncode(issuerSignedItemMetadata2));
        byte[] encodedIsiMetadata2Bytes = Util.cborEncode(isiMetadata2Bytes);

        Map<String, List<byte[]>> issuerSignedMapping = new HashMap<>();
        issuerSignedMapping.put("org.namespace",
                Arrays.asList(encodedIsiMetadataBytes, encodedIsiMetadata2Bytes));
        return issuerSignedMapping;
    }

    private Map<String, List<byte[]>> createInvalidFormatDigestIdMapping() {
        DataItem issuerSignedItemMetadata = new CborBuilder()
                .addMap()
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("digestID", 42)
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIsiMetadata = Util.cborEncode(issuerSignedItemMetadata);

        DataItem issuerSignedItemMetadata2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIsiMetadata2 = Util.cborEncode(issuerSignedItemMetadata2);

        Map<String, List<byte[]>> issuerSignedMapping = new HashMap<>();
        issuerSignedMapping.put("org.namespace",
                Arrays.asList(encodedIsiMetadata, encodedIsiMetadata2));
        return issuerSignedMapping;
    }

    private byte[] createValidIssuerAuth() {
        byte[] encodedIssuerAuth = Util.cborEncode(new CborBuilder()
                .addArray()
                .addArray()
                .end()
                .add(SimpleValue.NULL)
                .add(new byte[] {0x01, 0x02})
                .end()
                .build().get(0));
        return encodedIssuerAuth;
    }

    @Test
    public void testStaticAuthData() {
        // This test checks that the order of the maps in IssuerSignedItem is preserved
        // when using Utility.encodeStaticAuthData() and that no canonicalization takes
        // place.

        Map<String, List<byte[]>> issuerSignedMapping = createValidDigestIdMapping();

        byte[] encodedIssuerAuth = createValidIssuerAuth();

        byte[] staticAuthData =
                new StaticAuthDataGenerator(issuerSignedMapping, encodedIssuerAuth).generate();

        Assert.assertEquals(
                "{\n" +
                        "  \"digestIdMapping\": {\n" +
                        "    \"org.namespace\": [\n" +
                        "      24(<< {\n" +
                        "        \"random\": h'505152',\n" +
                        "        \"digestID\": 42,\n" +
                        "        \"elementIdentifier\": \"dataElementName\",\n" +
                        "        \"elementValue\": null\n" +
                        "      } >>),\n" +
                        "      24(<< {\n" +
                        "        \"digestID\": 43,\n" +
                        "        \"random\": h'535455',\n" +
                        "        \"elementIdentifier\": \"dataElementName2\",\n" +
                        "        \"elementValue\": null\n" +
                        "      } >>)\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"issuerAuth\": [\n" +
                        "    [],\n" +
                        "    null,\n" +
                        "    h'0102'\n" +
                        "  ]\n" +
                        "}",
                CborUtil.toDiagnostics(
                        staticAuthData,
                        CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR + CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));

        // Now check we can decode it
        StaticAuthDataParser.StaticAuthData decodedStaticAuthData = new StaticAuthDataParser(staticAuthData).parse();
        Map<String, List<byte[]>> digestIdMapping = decodedStaticAuthData.getDigestIdMapping();

        // Check that the IssuerSignedItem instances are correctly decoded and the order
        // of the map matches what was put in above
        Assert.assertEquals(1, digestIdMapping.size());
        List<byte[]> list = digestIdMapping.get("org.namespace");
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals(
                "24(<< {\n" +
                        "  \"random\": h'505152',\n" +
                        "  \"digestID\": 42,\n" +
                        "  \"elementIdentifier\": \"dataElementName\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                CborUtil.toDiagnostics(list.get(0), CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                        + CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        Assert.assertEquals(
                "24(<< {\n" +
                        "  \"digestID\": 43,\n" +
                        "  \"random\": h'535455',\n" +
                        "  \"elementIdentifier\": \"dataElementName2\",\n" +
                        "  \"elementValue\": null\n" +
                        "} >>)",
                CborUtil.toDiagnostics(list.get(1), CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                        + CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));

        byte[] issuerAuth = decodedStaticAuthData.getIssuerAuth();
        Assert.assertArrayEquals(encodedIssuerAuth, issuerAuth);
    }

    @Test
    public void testStaticAuthDataExceptions() {
        Assert.assertThrows("expect exception for empty digestIDMapping",
                IllegalArgumentException.class,
                () -> new StaticAuthDataGenerator(new HashMap<>(), createValidIssuerAuth())
                        .generate());

        Assert.assertThrows("expect exception for invalid digestIDMapping, where " +
                        "elementValue is non-NULL",
                IllegalArgumentException.class,
                () -> new StaticAuthDataGenerator(createInvalidElementValueDigestIdMapping(),
                        createValidIssuerAuth()).generate());

        Assert.assertThrows("expect exception for invalid digestIDMapping, where " +
                        "issuerSignedItemMetadata is not encoded as tagged bytestring",
                IllegalArgumentException.class,
                () -> new StaticAuthDataGenerator(createInvalidFormatDigestIdMapping(),
                        createValidIssuerAuth()).generate());
    }

}
