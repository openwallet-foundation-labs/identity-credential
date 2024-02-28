/*
 * Copyright 2022gn The Android Open Source Project
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
package com.android.identity.mdoc.response

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPrivateKeyDoubleCoordinate
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.internal.Util
import com.android.identity.mdoc.TestVectors
import com.android.identity.util.Constants
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security
import java.security.cert.CertificateEncodingException

class DeviceResponseParserTest {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    @Throws(CertificateEncodingException::class)
    fun testDeviceResponseParserWithVectors() {

        // NOTE: This tests tests the MAC verification path of DeviceResponseParser, the
        // ECDSA verification path is tested in DeviceResponseGeneratorTest by virtue of
        // SUtil.getIdentityCredentialStore() defaulting to the Jetpack.
        val encodedDeviceResponse = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE
        )

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        Assert.assertEquals("1.0", dr.version)
        val documents = dr.documents
        Assert.assertEquals(1, documents.size.toLong())
        val d = documents[0]

        // Check ValidityInfo is correctly parsed, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        // 2020-10-01T13:30:02Z == 1601559002000
        Assert.assertEquals(1601559002000L, d.validityInfoSigned.toEpochMilli())
        Assert.assertEquals(1601559002000L, d.validityInfoValidFrom.toEpochMilli())
        // 2021-10-01T13:30:02Z == 1601559002000
        Assert.assertEquals(1633095002000L, d.validityInfoValidUntil.toEpochMilli())
        Assert.assertNull(d.validityInfoExpectedUpdate)

        // Check DeviceKey is correctly parsed
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y)
        )
        Assert.assertEquals(deviceKeyFromVector, d.deviceKey)

        // Test example is using a MAC.
        Assert.assertFalse(d.deviceSignedAuthenticatedViaSignature)

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        Assert.assertTrue(d.issuerSignedAuthenticated)
        Assert.assertTrue(d.deviceSignedAuthenticated)
        Assert.assertEquals(MDL_DOCTYPE, d.docType)
        Assert.assertEquals(0, d.deviceNamespaces.size.toLong())
        Assert.assertEquals(1, d.issuerNamespaces.size.toLong())
        Assert.assertEquals(MDL_NAMESPACE, d.issuerNamespaces.iterator().next())
        Assert.assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size.toLong())
        Assert.assertEquals(
            "Doe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )
        Assert.assertEquals(
            "123456789",
            d.getIssuerEntryString(MDL_NAMESPACE, "document_number")
        )
        Assert.assertEquals(
            "1004(\"2019-10-20\")",
            Cbor.toDiagnostics(
                d.getIssuerEntryData(MDL_NAMESPACE, "issue_date"),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
            "1004(\"2024-10-20\")",
            Cbor.toDiagnostics(
                d.getIssuerEntryData(MDL_NAMESPACE, "expiry_date"),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
        Assert.assertEquals(
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
        Assert.assertArrayEquals(
            Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE_PORTRAIT_DATA
            ),
            d.getIssuerEntryByteString(MDL_NAMESPACE, "portrait")
        )

        // Check the issuer-signed items all validated (digest matches what's in MSO)
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"))
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "document_number"))
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "issue_date"))
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "expiry_date"))
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "driving_privileges"))
        Assert.assertTrue(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "portrait"))
        Assert.assertEquals(0, d.numIssuerEntryDigestMatchFailures.toLong())

        // Check the returned issuer certificate matches.
        //
        val (certificates) = d.issuerCertificateChain
        Assert.assertEquals(1, certificates.size.toLong())
        val issuerCert = certificates[0].javaX509Certificate
        Assert.assertEquals("C=US, CN=utopia ds", issuerCert.subjectX500Principal.toString())
        Assert.assertEquals("C=US, CN=utopia iaca", issuerCert.issuerX500Principal.toString())
        Assert.assertArrayEquals(
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DS_CERT),
            issuerCert.encoded
        )
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedIssuerItem() {
        val encodedDeviceResponse = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE
        )

        // We know that the value for family_name in IssuerSignedItem is at offset 200.
        // Change this from "Doe" to "Foe" to force validation of that item to fail.
        //
        Assert.assertEquals(0x44, encodedDeviceResponse[200].toLong())
        encodedDeviceResponse[200] = 0x46.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        Assert.assertEquals("1.0", dr.version)
        val documents = dr.documents
        Assert.assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]
        Assert.assertTrue(d.issuerSignedAuthenticated)
        Assert.assertTrue(d.deviceSignedAuthenticated)

        // We changed "Doe" to "Foe". Check that it doesn't validate.
        //
        Assert.assertEquals(
            "Foe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )
        Assert.assertFalse(d.getIssuerEntryDigestMatch(MDL_NAMESPACE, "family_name"))
        Assert.assertEquals(1, d.numIssuerEntryDigestMatchFailures.toLong())
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedDeviceSigned() {
        val encodedDeviceResponse = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE
        )

        // We know that the 32 bytes for the MAC in DeviceMac CBOR starts at offset 3522 and
        // starts with E99521A8. Poison that to cause DeviceSigned to not authenticate.
        Assert.assertEquals(0xe9.toByte().toLong(), encodedDeviceResponse[3522].toLong())
        encodedDeviceResponse[3522] = 0xe8.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        Assert.assertEquals("1.0", dr.version)
        val documents = dr.documents
        Assert.assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]
        Assert.assertTrue(d.issuerSignedAuthenticated)

        // Validation of DeviceSigned should fail.
        Assert.assertFalse(d.deviceSignedAuthenticated)
    }

    @Test
    fun testDeviceResponseParserWithVectorsMalformedIssuerSigned() {
        val encodedDeviceResponse = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE
        )

        // We know that issuer signature starts at offset 3398 and starts with 59E64205DF1E.
        // Poison that to cause IssuerSigned to not authenticate.
        Assert.assertEquals(0x59.toByte().toLong(), encodedDeviceResponse[3398].toLong())
        encodedDeviceResponse[3398] = 0x5a.toByte()

        // Strip the #6.24 tag since our APIs expects just the bytes of SessionTranscript.
        val encodedSessionTranscriptBytes = Util.fromHex(
            TestVectors.ISO_18013_5_ANNEX_D_SESSION_TRANSCRIPT_BYTES
        )
        val encodedSessionTranscript = Cbor.encode(
            Cbor.decode(encodedSessionTranscriptBytes).asTaggedEncodedCbor
        )
        val eReaderKey: EcPrivateKey = EcPrivateKeyDoubleCoordinate(
            EcCurve.P256,
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_D),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_X),
            Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_EPHEMERAL_READER_KEY_Y)
        )
        val dr = DeviceResponseParser(
            encodedDeviceResponse,
            encodedSessionTranscript
        )
            .setEphemeralReaderKey(eReaderKey)
            .parse()
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK, dr.status)
        Assert.assertEquals("1.0", dr.version)
        val documents = dr.documents
        Assert.assertEquals(1, documents.size.toLong())

        // Check the returned issuer data, these values are all from
        // ISO/IEC 18013-5 Annex D.4.1.2 mdoc response.
        //
        val d = documents[0]

        // Validation of IssuerSigned should fail.
        Assert.assertFalse(d.issuerSignedAuthenticated)

        // Check we're still able to read the data, though...
        Assert.assertEquals(MDL_DOCTYPE, d.docType)
        Assert.assertEquals(0, d.deviceNamespaces.size.toLong())
        Assert.assertEquals(1, d.issuerNamespaces.size.toLong())
        Assert.assertEquals(MDL_NAMESPACE, d.issuerNamespaces.iterator().next())
        Assert.assertEquals(6, d.getIssuerEntryNames(MDL_NAMESPACE).size.toLong())
        Assert.assertEquals(
            "Doe",
            d.getIssuerEntryString(MDL_NAMESPACE, "family_name")
        )

        // DeviceSigned is fine.
        Assert.assertTrue(d.deviceSignedAuthenticated)
    }

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }
}
