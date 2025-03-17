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
package org.multipaz.mdoc.mso

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StaticAuthDataTest {
    private fun createValidDigestIdMapping(): Map<String, List<ByteArray>> {
        val issuerSignedItemMetadata = buildCborMap {
            put("random", byteArrayOf(0x50, 0x51, 0x52))
            put("digestID", 42)
            put("elementIdentifier", "dataElementName")
            put("elementValue", Simple.NULL)
        }
        val isiMetadataBytes: DataItem = Tagged(24, Bstr(Cbor.encode(issuerSignedItemMetadata)))
        val encodedIsiMetadataBytes = Cbor.encode(isiMetadataBytes)
        val issuerSignedItemMetadata2 = buildCborMap {
            put("digestID", 43)
            put("random", byteArrayOf(0x53, 0x54, 0x55))
            put("elementIdentifier", "dataElementName2")
            put("elementValue", Simple.NULL)
        }
        val isiMetadata2Bytes: DataItem = Tagged(24, Bstr(Cbor.encode(issuerSignedItemMetadata2)))
        val encodedIsiMetadata2Bytes = Cbor.encode(isiMetadata2Bytes)
        val issuerSignedItemMetadata3 = buildCborMap {
            put("digestID", 44)
            put("random", byteArrayOf(0x53, 0x54, 0x55))
            put("elementIdentifier", "portrait")
            put("elementValue", Cbor.encode(Bstr(byteArrayOf(0x20, 0x21, 0x22, 0x23))))
        }
        val isiMetadata3Bytes: DataItem = Tagged(24, Bstr(Cbor.encode(issuerSignedItemMetadata3)))
        val encodedIsiMetadata3Bytes = Cbor.encode(isiMetadata3Bytes)
        val issuerSignedMapping = mutableMapOf<String, List<ByteArray>>()
        issuerSignedMapping["org.namespace"] = listOf(
            encodedIsiMetadataBytes,
            encodedIsiMetadata2Bytes,
            encodedIsiMetadata3Bytes
        )
        return issuerSignedMapping
    }

    private fun createValidIssuerAuth(): ByteArray {
        return Cbor.encode(
            buildCborArray {
                addArray()
                end()
                add(Simple.NULL)
                add(byteArrayOf(0x01, 0x02))
            }
        )
    }

    @Test
    fun testStaticAuthData() {
        // This test checks that the order of the maps in IssuerSignedItem is preserved
        // when and that no canonicalization takes place.
        val issuerSignedMapping = createValidDigestIdMapping()
        val encodedIssuerAuth = createValidIssuerAuth()
        val staticAuthData = StaticAuthDataGenerator(issuerSignedMapping, encodedIssuerAuth).generate()
        assertEquals(
            """{
  "digestIdMapping": {
    "org.namespace": [
      24(<< {
        "random": h'505152',
        "digestID": 42,
        "elementIdentifier": "dataElementName",
        "elementValue": null
      } >>),
      24(<< {
        "digestID": 43,
        "random": h'535455',
        "elementIdentifier": "dataElementName2",
        "elementValue": null
      } >>),
      24(<< {
        "digestID": 44,
        "random": h'535455',
        "elementIdentifier": "portrait",
        "elementValue": h'4420212223'
      } >>)
    ]
  },
  "issuerAuth": [
    [],
    null,
    h'0102'
  ]
}""",
            Cbor.toDiagnostics(
                staticAuthData,
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )

        // Now check we can decode it
        val decodedStaticAuthData = StaticAuthDataParser(staticAuthData).parse()
        val digestIdMapping = decodedStaticAuthData.digestIdMapping

        // Check that the IssuerSignedItem instances are correctly decoded and the order
        // of the map matches what was put in above
        assertEquals(1, digestIdMapping.size.toLong())
        val list = digestIdMapping["org.namespace"]!!
        assertEquals(3, list.size.toLong())
        assertEquals(
            """24(<< {
  "random": h'505152',
  "digestID": 42,
  "elementIdentifier": "dataElementName",
  "elementValue": null
} >>)""",
            Cbor.toDiagnostics(
                list[0],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        assertEquals(
            """24(<< {
  "digestID": 43,
  "random": h'535455',
  "elementIdentifier": "dataElementName2",
  "elementValue": null
} >>)""",
            Cbor.toDiagnostics(
                list[1],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        val issuerAuth = decodedStaticAuthData.issuerAuth
        assertContentEquals(encodedIssuerAuth, issuerAuth)
    }

    @Test
    fun testStaticAuthDataExceptions() {
        assertFailsWith<IllegalArgumentException>() {
            StaticAuthDataGenerator(mapOf(), createValidIssuerAuth()).generate()
        }
    }
}
