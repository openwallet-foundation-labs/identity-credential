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
package org.multipaz.mdoc.response

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPrivateKeyDoubleCoordinate
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.mdoc.TestVectors
import org.multipaz.util.Constants
import org.multipaz.util.fromHex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceResponseParserTest {
    @Test
    fun testDeviceResponseParserWithVectors() {

        // NOTE: This tests tests the MAC verification path of DeviceResponseParser, the
        // ECDSA verification path is tested in DeviceResponseGeneratorTest by virtue of
        // SUtil.getIdentityCredentialStore() defaulting to the Jetpack.
        val encodedDeviceResponse = TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex()
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y.fromHex()
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        assertEquals("1.0", dr.version)
        val documents = dr.documents
        assertEquals(1, documents.size.toLong())
        val d = documents[0]

        // Check ValidityInfo is correctly parsed, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        // 2020-10-01T13:30:02Z == 1601559002000
        assertEquals(1601559002000L, d.validityInfoSigned.toEpochMilliseconds())
        assertEquals(1601559002000L, d.validityInfoValidFrom.toEpochMilliseconds())
        // 2021-10-01T13:30:02Z == 1601559002000
        assertEquals(1633095002000L, d.validityInfoValidUntil.toEpochMilliseconds())
        assertNull(d.validityInfoExpectedUpdate)

        // Check DeviceKey is correctly parsed
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex()
        )
        assertEquals(deviceKeyFromVector, d.deviceKey)

        // Test example is using a MAC.
        assertFalse(d.deviceSignedAuthenticatedViaSignature)

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        assertTrue(d.issuerSignedAuthenticated)
        assertTrue(d.deviceSignedAuthenticated)
        assertEquals(MDL_DOCTYPE, d.docType)
        assertEquals(0, d.deviceNamespaces.size.toLong())
        assertEquals(1, d.issuerNamespaces.size.toLong())
        assertEquals(MDL_NAMESPACE, d.issuerNamespaces.iterator().next())
        assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size.toLong())
        assertEquals(
            "Doe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )
        assertEquals(
            "123456789",
            d.getIssuerEntryString(MDL_NAMESPACE, "document_number")
        )
        assertEquals(
            "1004(\"2019-10-20\")",
            Cbor.toDiagnostics(
                d.getIssuerEntryData(MDL_NAMESPACE, "issue_date"),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
        assertEquals(
            "1004(\"2024-10-20\")",
            Cbor.toDiagnostics(
                d.getIssuerEntryData(MDL_NAMESPACE, "expiry_date"),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
        assertEquals(
            """[
  {
    "vehicle_category_code": "A",
    "issue_date": 1004("2018-08-09"),
    "expiry_date": 1004("2024-10-20")
  },
  {
    "vehicle_category_code": "B",
    "issue_date": 1004("2017-02-23"),
    "expiry_date": 1004("2024-10-20")
  }
]""",
            Cbor.toDiagnostics(
                d.getIssuerEntryData(MDL_NAMESPACE, "driving_privileges"),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
        assertContentEquals(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE_PORTRAIT_DATA.fromHex(),
            d.getIssuerEntryByteString(MDL_NAMESPACE, "portrait")
        )

        // Check the issuer-signed items all validated (digest matches what's in MSO)
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"))
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "document_number"))
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "issue_date"))
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "expiry_date"))
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "driving_privileges"))
        assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "portrait"))
        assertEquals(0, d.numIssuerEntryDigestMatchFailures.toLong())

        // Check the returned issuer certificate matches.
        //
        val (certificates) = d.issuerCertificateChain
        assertEquals(1, certificates.size.toLong())
        assertContentEquals(
            TestVectors.ISO_18013_5_ANNEX_D_DS_CERT.fromHex(),
            certificates[0].encodedCertificate
        )
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedIssuerItem() {
        val encodedDeviceResponse =
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex()

        // We know that the value for family_name in IssuerSignedItem is at offset 200.
        // Change this from "Doe" to "Foe" to force validation of that item to fail.
        //
        assertEquals(0x44, encodedDeviceResponse[200].toLong())
        encodedDeviceResponse[200] = 0x46.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex()
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y.fromHex()
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        assertEquals("1.0", dr.version)
        val documents = dr.documents
        assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]
        assertTrue(d.issuerSignedAuthenticated)
        assertTrue(d.deviceSignedAuthenticated)

        // We changed "Doe" to "Foe". Check that it doesn't validate.
        //
        assertEquals(
            "Foe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )
        assertFalse(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"))
        assertEquals(1, d.numIssuerEntryDigestMatchFailures.toLong())
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedDeviceSigned() {
        val encodedDeviceResponse = TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex()

        // We know that the 32 bytes for the MAC in DeviceMac CBOR starts at offset 3522 and
        // starts with E99521A8. Poison that to cause DeviceSigned to not authenticate.
        assertEquals(0xe9.toByte().toLong(), encodedDeviceResponse[3522].toLong())
        encodedDeviceResponse[3522] = 0xe8.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex()
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y.fromHex()
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        assertEquals("1.0", dr.version)
        val documents = dr.documents
        assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]
        assertTrue(d.issuerSignedAuthenticated)

        // Validation of DeviceSigned should fail.
        assertFalse(d.deviceSignedAuthenticated)
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedIssuerSigned() {
        val encodedDeviceResponse = TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex()

        // We know that issuer signature starts at offset 3398 and starts with 59E64205DF1E.
        // Poison that to cause IssuerSigned to not authenticate.
        assertEquals(0x59.toByte().toLong(), encodedDeviceResponse[3398].toLong())
        encodedDeviceResponse[3398] = 0x5a.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes =
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES.fromHex()
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y.fromHex()
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        assertEquals("1.0", dr.version)
        val documents = dr.documents
        assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]

        // Validation of IssuerSigned should fail.
        assertFalse(d.issuerSignedAuthenticated)

        // Check we're still able to read the data, though...
        assertEquals(MDL_DOCTYPE, d.docType)
        assertEquals(0, d.deviceNamespaces.size.toLong())
        assertEquals(1, d.issuerNamespaces.size.toLong())
        assertEquals(MDL_NAMESPACE, d.issuerNamespaces.iterator().next())
        assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size.toLong())
        assertEquals(
            "Doe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )

        // DeviceSigned is fine.
        assertTrue(d.deviceSignedAuthenticated)
    }

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }
}