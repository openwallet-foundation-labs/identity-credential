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
package com.android.identity.credential

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import org.junit.Assert
import org.junit.Test

class NameSpacedDataTest {
    @Test
    fun testNameSpacedData() {
        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString("ns1", "foo1", "bar1")
            .putEntryString("ns1", "foo2", "bar2")
            .putEntryString("ns1", "foo3", "bar3")
            .putEntryString("ns2", "bar1", "foo1")
            .putEntryString("ns2", "bar2", "foo2")
            .putEntryString("test", "tstr", "a string")
            .putEntryByteString("test", "bstr", byteArrayOf(1, 2))
            .putEntryNumber("test", "pos", 42)
            .putEntryNumber("test", "neg", -42)
            .putEntryBoolean("test", "true", true)
            .putEntryBoolean("test", "false", false)
            .build()
        val asCbor = nameSpacedData.encodeAsCbor()
        Assert.assertEquals(
            """{
  "ns1": {
    "foo1": 24(<< "bar1" >>),
    "foo2": 24(<< "bar2" >>),
    "foo3": 24(<< "bar3" >>)
  },
  "ns2": {
    "bar1": 24(<< "foo1" >>),
    "bar2": 24(<< "foo2" >>)
  },
  "test": {
    "tstr": 24(<< "a string" >>),
    "bstr": 24(<< h'0102' >>),
    "pos": 24(<< 42 >>),
    "neg": 24(<< -42 >>),
    "true": 24(<< true >>),
    "false": 24(<< false >>)
  }
}""",
            Cbor.toDiagnostics(
                asCbor,
                setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR)
            )
        )
        checkNameSpaced(nameSpacedData)
        val decoded = NameSpacedData.fromEncodedCbor(asCbor)
        checkNameSpaced(decoded)
    }

    @Test
    fun testBuilderWithCopy() {
        val foo = NameSpacedData.Builder()
            .putEntryString("ns1", "de1", "foo")
            .putEntryString("ns1", "de2", "bar")
            .build()
        Assert.assertEquals(
            "{\n" +
                    "  \"ns1\": {\n" +
                    "    \"de1\": 24(<< \"foo\" >>),\n" +
                    "    \"de2\": 24(<< \"bar\" >>)\n" +
                    "  }\n" +
                    "}",
            Cbor.toDiagnostics(
                foo.toCbor(),
                setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR)
            )
        )

        val fooModified = NameSpacedData.Builder(foo)
            .putEntryString("ns1", "de2", "foobar")
            .build()
        Assert.assertEquals(
            "{\n" +
                    "  \"ns1\": {\n" +
                    "    \"de1\": 24(<< \"foo\" >>),\n" +
                    "    \"de2\": 24(<< \"foobar\" >>)\n" +
                    "  }\n" +
                    "}",
            Cbor.toDiagnostics(
                fooModified.toCbor(),
                setOf(DiagnosticOption.PRETTY_PRINT, DiagnosticOption.EMBEDDED_CBOR)
            )
        )
    }

    private fun checkNameSpaced(nameSpacedData: NameSpacedData) {
        Assert.assertEquals(3, nameSpacedData.nameSpaceNames.size.toLong())
        Assert.assertEquals("ns1", nameSpacedData.nameSpaceNames[0])
        Assert.assertEquals("ns2", nameSpacedData.nameSpaceNames[1])
        Assert.assertEquals(3, nameSpacedData.getDataElementNames("ns1").size.toLong())
        Assert.assertEquals("bar1", nameSpacedData.getDataElementString("ns1", "foo1"))
        Assert.assertEquals("bar2", nameSpacedData.getDataElementString("ns1", "foo2"))
        Assert.assertEquals("bar3", nameSpacedData.getDataElementString("ns1", "foo3"))
        Assert.assertEquals(2, nameSpacedData.getDataElementNames("ns2").size.toLong())
        Assert.assertEquals("foo1", nameSpacedData.getDataElementString("ns2", "bar1"))
        Assert.assertEquals("foo2", nameSpacedData.getDataElementString("ns2", "bar2"))
        Assert.assertEquals("a string", nameSpacedData.getDataElementString("test", "tstr"))
        Assert.assertArrayEquals(
            byteArrayOf(1, 2),
            nameSpacedData.getDataElementByteString("test", "bstr")
        )
        Assert.assertEquals(42, nameSpacedData.getDataElementNumber("test", "pos"))
        Assert.assertEquals(-42, nameSpacedData.getDataElementNumber("test", "neg"))
        Assert.assertTrue(nameSpacedData.getDataElementBoolean("test", "true"))
        Assert.assertFalse(nameSpacedData.getDataElementBoolean("test", "false"))
        Assert.assertTrue(nameSpacedData.hasDataElement("ns1", "foo1"))
        Assert.assertFalse(nameSpacedData.hasDataElement("ns1", "does_not_exist"))
        Assert.assertFalse(nameSpacedData.hasDataElement("does_not_exist", "foo1"))
    }
}
