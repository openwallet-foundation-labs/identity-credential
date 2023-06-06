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

package com.android.identity.credential;

import androidx.annotation.NonNull;

import com.android.identity.util.CborUtil;

import org.junit.Assert;
import org.junit.Test;

public class NameSpacedDataTest {

    static void
    checkNameSpaced(@NonNull NameSpacedData nameSpacedData) {
        Assert.assertEquals(3, nameSpacedData.getNameSpaceNames().size());
        Assert.assertEquals("ns1", nameSpacedData.getNameSpaceNames().get(0));
        Assert.assertEquals("ns2", nameSpacedData.getNameSpaceNames().get(1));
        Assert.assertEquals(3, nameSpacedData.getDataElementNames("ns1").size());
        Assert.assertEquals("bar1", nameSpacedData.getDataElementString("ns1", "foo1"));
        Assert.assertEquals("bar2", nameSpacedData.getDataElementString("ns1", "foo2"));
        Assert.assertEquals("bar3", nameSpacedData.getDataElementString("ns1", "foo3"));
        Assert.assertEquals(2, nameSpacedData.getDataElementNames("ns2").size());
        Assert.assertEquals("foo1", nameSpacedData.getDataElementString("ns2", "bar1"));
        Assert.assertEquals("foo2", nameSpacedData.getDataElementString("ns2", "bar2"));

        Assert.assertEquals("a string", nameSpacedData.getDataElementString("test", "tstr"));
        Assert.assertArrayEquals(new byte[] {1, 2}, nameSpacedData.getDataElementByteString("test", "bstr"));
        Assert.assertEquals(42, nameSpacedData.getDataElementNumber("test", "pos"));
        Assert.assertEquals(-42, nameSpacedData.getDataElementNumber("test", "neg"));
        Assert.assertTrue(nameSpacedData.getDataElementBoolean("test", "true"));
        Assert.assertFalse(nameSpacedData.getDataElementBoolean("test", "false"));

        Assert.assertTrue(nameSpacedData.hasDataElement("ns1", "foo1"));
        Assert.assertFalse(nameSpacedData.hasDataElement("ns1", "does_not_exist"));
        Assert.assertFalse(nameSpacedData.hasDataElement("does_not_exist", "foo1"));
    }

    @Test
    public void testNameSpacedData() {
        NameSpacedData nameSpacedData = new NameSpacedData.Builder()
                .putEntryString("ns1", "foo1", "bar1")
                .putEntryString("ns1", "foo2", "bar2")
                .putEntryString("ns1", "foo3", "bar3")
                .putEntryString("ns2", "bar1", "foo1")
                .putEntryString("ns2", "bar2", "foo2")
                .putEntryString("test", "tstr", "a string")
                .putEntryByteString("test", "bstr", new byte[] {1, 2})
                .putEntryNumber("test", "pos", 42)
                .putEntryNumber("test", "neg", -42)
                .putEntryBoolean("test", "true", true)
                .putEntryBoolean("test", "false", false)
                .build();
        byte[] asCbor = nameSpacedData.encodeAsCbor();
        Assert.assertEquals("{\n" +
                        "  \"ns1\": {\n" +
                        "    \"foo1\": 24(<< \"bar1\" >>),\n" +
                        "    \"foo2\": 24(<< \"bar2\" >>),\n" +
                        "    \"foo3\": 24(<< \"bar3\" >>)\n" +
                        "  },\n" +
                        "  \"ns2\": {\n" +
                        "    \"bar1\": 24(<< \"foo1\" >>),\n" +
                        "    \"bar2\": 24(<< \"foo2\" >>)\n" +
                        "  },\n" +
                        "  \"test\": {\n" +
                        "    \"tstr\": 24(<< \"a string\" >>),\n" +
                        "    \"bstr\": 24(<< h'0102' >>),\n" +
                        "    \"pos\": 24(<< 42 >>),\n" +
                        "    \"neg\": 24(<< -42 >>),\n" +
                        "    \"true\": 24(<< true >>),\n" +
                        "    \"false\": 24(<< false >>)\n" +
                        "  }\n" +
                        "}",
                CborUtil.toDiagnostics(asCbor,
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT
                                | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));

        checkNameSpaced(nameSpacedData);

        NameSpacedData decoded = NameSpacedData.fromEncodedCbor(asCbor);
        checkNameSpaced(decoded);
    }

}
