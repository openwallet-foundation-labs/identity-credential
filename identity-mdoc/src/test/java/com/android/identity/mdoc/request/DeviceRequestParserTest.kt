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
package com.android.identity.mdoc.request

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.mdoc.TestVectors
import com.android.identity.util.fromHex
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

class DeviceRequestParserTest {
    @Before
    fun setUp() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testDeviceRequestParserWithVectors() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex
        val encodedSessionTranscript =
            Cbor.encode(Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor)
        val parser =
            DeviceRequestParser(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST.fromHex,
                encodedSessionTranscript,
            )
        val request = parser.parse()
        Assert.assertEquals("1.0", request.version)
        val docRequests = request.docRequests
        Assert.assertEquals(1, docRequests.size.toLong())
        val dr = docRequests.iterator().next()
        Assert.assertEquals(MDL_DOCTYPE, dr.docType)
        Assert.assertArrayEquals(
            TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST.fromHex,
            dr.itemsRequest,
        )
        val readerCertChain = dr.readerCertificateChain
        Assert.assertEquals(1, readerCertChain!!.certificates.size.toLong())
        Assert.assertArrayEquals(
            TestVectors.ISO_18013_5_ANNEX_D_READER_CERT.fromHex,
            readerCertChain.certificates[0].encodedCertificate,
        )
        Assert.assertArrayEquals(
            TestVectors.ISO_18013_5_ANNEX_D_READER_AUTH.fromHex,
            dr.readerAuth,
        )
        Assert.assertTrue(dr.readerAuthenticated)
        Assert.assertArrayEquals(arrayOf(MDL_NAMESPACE), dr.namespaces.toTypedArray())
        Assert.assertEquals(6, dr.getEntryNames(MDL_NAMESPACE).size.toLong())
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "family_name"))
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "document_number"))
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "driving_privileges"))
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "issue_date"))
        Assert.assertTrue(dr.getIntentToRetain(MDL_NAMESPACE, "expiry_date"))
        Assert.assertFalse(dr.getIntentToRetain(MDL_NAMESPACE, "portrait"))
        try {
            dr.getEntryNames("ns-was-not-requested")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            dr.getIntentToRetain("ns-was-not-requested", "elem-was-not-requested")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            dr.getIntentToRetain(MDL_NAMESPACE, "elem-was-not-requested")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun testDeviceRequestParserWithVectorsMalformedReaderSignature() {
        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex
        val encodedSessionTranscript =
            Cbor.encode(Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor)

        // We know the COSE_Sign1 signature for reader authentication is at index 655 and
        // starts with 1f340006... Poison that so we can check whether signature verification
        // detects it...
        val encodedDeviceRequest = TestVectors.ISO_18013_5_ANNEX_D_DEVICE_REQUEST.fromHex
        Assert.assertEquals(0x1f.toByte().toLong(), encodedDeviceRequest[655].toLong())
        encodedDeviceRequest[655] = 0x1e
        val parser =
            DeviceRequestParser(
                encodedDeviceRequest,
                encodedSessionTranscript,
            )
        val request = parser.parse()
        Assert.assertEquals("1.0", request.version)
        val docRequests = request.docRequests
        Assert.assertEquals(1, docRequests.size.toLong())
        val dr = docRequests.iterator().next()
        Assert.assertEquals(MDL_DOCTYPE, dr.docType)
        Assert.assertArrayEquals(
            TestVectors.ISO_18013_5_ANNEX_D_ITEMS_REQUEST.fromHex,
            dr.itemsRequest,
        )
        val readerCertChain = dr.readerCertificateChain
        Assert.assertEquals(1, readerCertChain!!.certificates.size.toLong())
        Assert.assertArrayEquals(
            TestVectors.ISO_18013_5_ANNEX_D_READER_CERT.fromHex,
            readerCertChain.certificates[0].encodedCertificate,
        )
        Assert.assertFalse(dr.readerAuthenticated)
    }

    fun testDeviceRequestParserReaderAuthHelper(curve: EcCurve) {
        val encodedSessionTranscript = Cbor.encode(Bstr(byteArrayOf(0x01, 0x02)))
        val mdlItemsToRequest: MutableMap<String, Map<String, Boolean>> = HashMap()
        val mdlNsItems: MutableMap<String, Boolean> = HashMap()
        mdlNsItems["family_name"] = true
        mdlNsItems["portrait"] = false
        mdlItemsToRequest[MDL_NAMESPACE] = mdlNsItems
        val readerKey = Crypto.createEcPrivateKey(curve)
        val trustPoint = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil =
            Instant.fromEpochMilliseconds(
                validFrom.toEpochMilliseconds() + 5L * 365 * 24 * 60 * 60 * 1000,
            )
        val certificate =
            Crypto.createX509v3Certificate(
                readerKey.publicKey,
                trustPoint,
                null,
                Algorithm.ES256,
                "42",
                "CN=Some Reader Key",
                "CN=Some Reader Authority",
                validFrom,
                validUntil, setOf(), listOf(),
            )
        val readerCertChain = CertificateChain(listOf(certificate))
        val mdlRequestInfo: MutableMap<String, ByteArray> = HashMap()
        mdlRequestInfo["foo"] = Cbor.encode(Tstr("bar"))
        mdlRequestInfo["bar"] = Cbor.encode(42.toDataItem)
        val encodedDeviceRequest =
            DeviceRequestGenerator(encodedSessionTranscript)
                .addDocumentRequest(
                    MDL_DOCTYPE,
                    mdlItemsToRequest,
                    mdlRequestInfo,
                    readerKey,
                    readerKey.curve.defaultSigningAlgorithm,
                    readerCertChain,
                )
                .generate()
        val deviceRequest =
            DeviceRequestParser(
                encodedDeviceRequest,
                encodedSessionTranscript,
            ).parse()
        Assert.assertEquals("1.0", deviceRequest.version)
        val documentRequests = deviceRequest.docRequests
        Assert.assertTrue(documentRequests.get(0).readerAuthenticated)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_P256() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P256)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_P384() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P384)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_P521() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.P521)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_brainpoolP256r1() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP256R1)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_brainpoolP320r1() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP320R1)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_brainpoolP384r1() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP384R1)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_brainpoolP512r1() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.BRAINPOOLP512R1)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_Ed25519() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.ED25519)
    }

    @Test
    fun testDeviceRequestParserReaderAuth_Ed448() {
        testDeviceRequestParserReaderAuthHelper(EcCurve.ED448)
    }

    // TODO: Have a request signed by an unsupported curve and make sure DeviceRequestParser
    //   fails gracefully.. that is, should successfully parse the request message but the
    //   getReaderAuthenticated() method should return false.
    //

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }
}
