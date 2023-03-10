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

import androidx.core.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class StaticAuthDataTest {

    @Test
    @SmallTest
    public void testStaticAuthData() throws Exception {
        // This test checks that the order of the maps in IssuerSignedItem is preserved
        // when using Utility.encodeStaticAuthData() and that no canonicalization takes
        // place.

        DataItem issuerSignedItem = new CborBuilder()
                .addMap()
                .put("random", new byte[] {0x50, 0x51, 0x52})
                .put("digestID", 42)
                .put("elementIdentifier", "dataElementName")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem = Util.cborEncode(issuerSignedItem);

        DataItem issuerSignedItem2 = new CborBuilder()
                .addMap()
                .put("digestID", 43)
                .put("random", new byte[] {0x53, 0x54, 0x55})
                .put("elementIdentifier", "dataElementName2")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        byte[] encodedIssuerSignedItem2 = Util.cborEncode(issuerSignedItem2);

        Map<String, List<byte[]>> issuerSignedMapping = new HashMap<>();
        issuerSignedMapping.put("org.namespace",
                Arrays.asList(encodedIssuerSignedItem, encodedIssuerSignedItem2));

        byte[] encodedIssuerAuth = Util.cborEncode(new CborBuilder()
                .addArray()
                    .addArray()
                    .end()
                .add(SimpleValue.NULL)
                .add(new byte[] {0x01, 0x02})
                .end()
                .build().get(0));

        byte[] staticAuthData = Utility.encodeStaticAuthData(issuerSignedMapping, encodedIssuerAuth);

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
        Pair<Map<String, List<byte[]>>, byte[]> val = Utility.decodeStaticAuthData(staticAuthData);
        Map<String, List<byte[]>> digestIdMapping = val.first;

        // Check that the IssuerSignedItem instances are correctly decoded and the order
        // of the map matches what was put in above
        Assert.assertEquals(1, digestIdMapping.size());
        List<byte[]> list = digestIdMapping.get("org.namespace");
        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals(
                "{\n" +
                        "  \"random\": h'505152',\n" +
                        "  \"digestID\": 42,\n" +
                        "  \"elementIdentifier\": \"dataElementName\",\n" +
                        "  \"elementValue\": null\n" +
                        "}",
                CborUtil.toDiagnostics(list.get(0), CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
        Assert.assertEquals(
                "{\n" +
                        "  \"digestID\": 43,\n" +
                        "  \"random\": h'535455',\n" +
                        "  \"elementIdentifier\": \"dataElementName2\",\n" +
                        "  \"elementValue\": null\n" +
                        "}",
                CborUtil.toDiagnostics(list.get(1), CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }
}
