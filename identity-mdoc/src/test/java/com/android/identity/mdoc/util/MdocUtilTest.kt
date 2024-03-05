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
package com.android.identity.mdoc.util

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.cbor.Tstr
import com.android.identity.credential.CredentialRequest.DataElement
import com.android.identity.credential.NameSpacedData
import com.android.identity.crypto.Algorithm
import com.android.identity.internal.Util
import com.android.identity.mdoc.TestVectors
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.util.MdocUtil.calculateDigestsForNameSpace
import com.android.identity.mdoc.util.MdocUtil.generateCredentialRequest
import com.android.identity.mdoc.util.MdocUtil.generateIssuerNameSpaces
import com.android.identity.mdoc.util.MdocUtil.stripIssuerNameSpaces
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security
import kotlin.random.Random

class MdocUtilTest {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testGenerateIssuerNameSpaces() {
        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString("ns1", "foo1", "bar1")
            .putEntryString("ns1", "foo2", "bar2")
            .putEntryString("ns1", "foo3", "bar3")
            .putEntryString("ns2", "bar1", "foo1")
            .putEntryString("ns2", "bar2", "foo2")
            .build()
        val overrides: MutableMap<String, Map<String, ByteArray>> = HashMap()
        val overridesForNs1: MutableMap<String, ByteArray> = HashMap()
        overridesForNs1["foo3"] = Cbor.encode(Tstr("bar3_override"))
        overrides["ns1"] = overridesForNs1
        val exceptions: MutableMap<String, List<String>> = HashMap()
        exceptions["ns1"] = mutableListOf("foo3")
        exceptions["ns2"] = mutableListOf("bar2")
        val issuerNameSpaces = generateIssuerNameSpaces(
            nameSpacedData,
            Random(42),
            16,
            overrides
        )
        Assert.assertEquals(2, issuerNameSpaces.size.toLong())
        var ns1Values = issuerNameSpaces["ns1"]!!
        Assert.assertEquals(3, ns1Values.size.toLong())
        var ns2Values = issuerNameSpaces["ns2"]!!
        Assert.assertEquals(2, ns2Values.size.toLong())
        Assert.assertEquals(
            """24(<< {
  "digestID": 0,
  "random": h'2b714e4520aabd26420972f8d80c48fa',
  "elementIdentifier": "foo1",
  "elementValue": "bar1"
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[0],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 4,
  "random": h'46905e101902b697c6132dacdfbf5a4b',
  "elementIdentifier": "foo2",
  "elementValue": "bar2"
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[1],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 2,
  "random": h'55b35d23743b5af1e1f2d3976124091d',
  "elementIdentifier": "foo3",
  "elementValue": "bar3_override"
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[2],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 1,
  "random": h'320908313266a9a296f81c7b45ffdecd',
  "elementIdentifier": "bar1",
  "elementValue": "foo1"
} >>)""",
            Cbor.toDiagnostics(
                ns2Values[0],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 3,
  "random": h'4d35a35d5f23ec7b62554c512dc211b3',
  "elementIdentifier": "bar2",
  "elementValue": "foo2"
} >>)""",
            Cbor.toDiagnostics(
                ns2Values[1],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )

        // Compare with digests above.
        var digests =
            calculateDigestsForNameSpace("ns1", issuerNameSpaces, Algorithm.SHA256)
        Assert.assertEquals(3, digests.size.toLong())
        Assert.assertEquals(
            "9f10afbca223fcfe0ee9f239e995cfe79e7f845b68981a4a0943706717c64efa",
            digests[0L]!!.toHex
        )
        Assert.assertEquals(
            "a5e74b031ea380267d39905981ea80c68178229219556ffd72d312a0366a7d63",
            digests[4L]!!.toHex
        )
        Assert.assertEquals(
            "03f0ac0623c2eaefd76bcbca00df782d84f544cf7ac1b1f9ed46144275e1d47c",
            digests[2L]!!.toHex
        )
        digests = calculateDigestsForNameSpace("ns2", issuerNameSpaces, Algorithm.SHA256)
        Assert.assertEquals(2, digests.size.toLong())
        Assert.assertEquals(
            "fd69be5fcc0df04ae78e147bb3ad95ce4ecff51028322cccf02195f36612a212",
            digests[1L]!!.toHex
        )
        Assert.assertEquals(
            "47083a3473ddfcf3c8cc00f2035ac41d0b791fc50106be416c068536c249c0dd",
            digests[3L]!!.toHex
        )

        // Check stripping
        val issuerNameSpacesStripped = stripIssuerNameSpaces(issuerNameSpaces, exceptions)
        ns1Values = issuerNameSpacesStripped["ns1"]!!
        Assert.assertEquals(3, ns1Values.size.toLong())
        ns2Values = issuerNameSpacesStripped["ns2"]!!
        Assert.assertEquals(2, ns2Values.size.toLong())
        Assert.assertEquals(
            """24(<< {
  "digestID": 0,
  "random": h'2b714e4520aabd26420972f8d80c48fa',
  "elementIdentifier": "foo1",
  "elementValue": null
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[0],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 4,
  "random": h'46905e101902b697c6132dacdfbf5a4b',
  "elementIdentifier": "foo2",
  "elementValue": null
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[1],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 2,
  "random": h'55b35d23743b5af1e1f2d3976124091d',
  "elementIdentifier": "foo3",
  "elementValue": "bar3_override"
} >>)""",
            Cbor.toDiagnostics(
                ns1Values[2],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 1,
  "random": h'320908313266a9a296f81c7b45ffdecd',
  "elementIdentifier": "bar1",
  "elementValue": null
} >>)""",
            Cbor.toDiagnostics(
                ns2Values[0],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            """24(<< {
  "digestID": 3,
  "random": h'4d35a35d5f23ec7b62554c512dc211b3',
  "elementIdentifier": "bar2",
  "elementValue": "foo2"
} >>)""",
            Cbor.toDiagnostics(
                ns2Values[1],
                setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
            )
        )
    }

    @Test
    fun testGetDigestsForNameSpaceInTestVectors() {
        val deviceResponse = Cbor.decode(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex
        )
        val documentDataItem = deviceResponse["documents"][0]
        val issuerSigned = documentDataItem["issuerSigned"]
        val issuerAuthDataItem = issuerSigned["issuerAuth"]
        val (_, _, _, payload) = issuerAuthDataItem.asCoseSign1
        val mobileSecurityObject = Cbor.decode(payload!!)
            .asTaggedEncodedCbor
        val encodedMobileSecurityObject = Cbor.encode(mobileSecurityObject)
        val mso = MobileSecurityObjectParser(encodedMobileSecurityObject).parse()
        val nameSpaces = issuerSigned["nameSpaces"]
        val arrayOfIssuerSignedItemBytes = nameSpaces["org.iso.18013.5.1"].asArray
        val issuerNamespacesForMdlNamespace: MutableList<ByteArray> = ArrayList()
        for (di in arrayOfIssuerSignedItemBytes) {
            issuerNamespacesForMdlNamespace.add(Cbor.encode(di))
        }
        val issuerNameSpacesFromTestVector: MutableMap<String, List<ByteArray>> = LinkedHashMap()
        issuerNameSpacesFromTestVector["org.iso.18013.5.1"] = issuerNamespacesForMdlNamespace
        val digestsCalculatedFromResponseInTestVector = calculateDigestsForNameSpace(
            "org.iso.18013.5.1",
            issuerNameSpacesFromTestVector,
            Algorithm.SHA256
        )
        val digestsListedInMsoInTestVector = mso.getDigestIDs("org.iso.18013.5.1")

        // Note: Because of selective disclosure, the response doesn't contain all the data
        // elements listed in the MSO... and we can only test what's in the response. So we
        // need to start from there
        //
        for (digestId in digestsCalculatedFromResponseInTestVector.keys) {
            val calculatedDigest = digestsCalculatedFromResponseInTestVector[digestId]
            val digestInMso = digestsListedInMsoInTestVector!![digestId]
            Assert.assertArrayEquals(calculatedDigest, digestInMso)
        }
    }

    @Test
    fun testGenerateCredentialRequest() {
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val parser = DeviceRequestParser(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST.fromHex,
            encodedSessionTranscript
        )
        val request = parser.parse()
        val elementsInRequest = arrayOf(
            DataElement("org.iso.18013.5.1", "family_name", true, false),
            DataElement("org.iso.18013.5.1", "document_number", true, false),
            DataElement("org.iso.18013.5.1", "driving_privileges", true, false),
            DataElement("org.iso.18013.5.1", "issue_date", true, false),
            DataElement("org.iso.18013.5.1", "expiry_date", true, false),
            DataElement("org.iso.18013.5.1", "portrait", false, false)
        )
        val cr = generateCredentialRequest(request.documentRequests[0])
        Assert.assertEquals(elementsInRequest.size.toLong(), cr.requestedDataElements.size.toLong())
        var n = 0
        for ((nameSpaceName, dataElementName, intentToRetain) in cr.requestedDataElements) {
            val (nameSpaceName1, dataElementName1, intentToRetain1) = elementsInRequest[n++]
            Assert.assertEquals(nameSpaceName, nameSpaceName1)
            Assert.assertEquals(dataElementName, dataElementName1)
            Assert.assertEquals(intentToRetain, intentToRetain1)
        }
    }
}
