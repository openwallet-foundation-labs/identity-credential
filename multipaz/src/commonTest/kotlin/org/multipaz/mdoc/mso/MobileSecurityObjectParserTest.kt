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
package org.multipaz.mdoc.mso

import org.multipaz.cbor.Cbor
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.mdoc.TestVectors
import org.multipaz.util.fromHex
import org.multipaz.util.toHex
import kotlin.time.Instant
import org.multipaz.crypto.Algorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileSecurityObjectParserTest {
    @Test
    fun testMSOParserWithVectors() {
        val deviceResponse =
            Cbor.decode(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE.fromHex())
        val documentDataItem = deviceResponse["documents"][0]
        val issuerSigned = documentDataItem["issuerSigned"]
        val issuerAuthDataItem = issuerSigned["issuerAuth"]
        val mobileSecurityObjectBytes = Cbor.decode(issuerAuthDataItem.asCoseSign1.payload!!)
        val mobileSecurityObject = mobileSecurityObjectBytes.asTaggedEncodedCbor
        val encodedMobileSecurityObject = Cbor.encode(mobileSecurityObject)

        // the response above and all the following constants are from ISO 18013-5 D.4.1.2 mdoc
        // response - the goal is to check that the parser returns the expected values
        val mso = MobileSecurityObjectParser(encodedMobileSecurityObject).parse()
        assertEquals("1.0", mso.version)
        assertEquals(Algorithm.SHA256, mso.digestAlgorithm)
        assertEquals("org.iso.18013.5.1.mDL", mso.docType)
        assertEquals(
            setOf("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
            mso.valueDigestNamespaces
        )
        assertNull(mso.getDigestIDs("abc"))
        val isoDigestIDs = mso.getDigestIDs("org.iso.18013.5.1")
        assertEquals(
            setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
            isoDigestIDs!!.keys
        )
        assertEquals(
            "75167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf",
            isoDigestIDs[0L]!!.toHex()
        )
        assertEquals(
            "67e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed4571",
            isoDigestIDs[1L]!!.toHex()
        )
        assertEquals(
            "3394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae",
            isoDigestIDs[2L]!!.toHex()
        )
        assertEquals(
            "2e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db555",
            isoDigestIDs[3L]!!.toHex()
        )
        assertEquals(
            "ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59",
            isoDigestIDs[4L]!!.toHex()
        )
        assertEquals(
            "fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d",
            isoDigestIDs[5L]!!.toHex()
        )
        assertEquals(
            "7d83e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533",
            isoDigestIDs[6L]!!.toHex()
        )
        assertEquals(
            "f0549a145f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936",
            isoDigestIDs[7L]!!.toHex()
        )
        assertEquals(
            "b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f66",
            isoDigestIDs[8L]!!.toHex()
        )
        assertEquals(
            "0b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c",
            isoDigestIDs[9L]!!.toHex()
        )
        assertEquals(
            "c98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c881",
            isoDigestIDs[10L]!!.toHex()
        )
        assertEquals(
            "b57dd036782f7b14c6a30faaaae6ccd5054ce88bdfa51a016ba75eda1edea948",
            isoDigestIDs[11L]!!.toHex()
        )
        assertEquals(
            "651f8736b18480fe252a03224ea087b5d10ca5485146c67c74ac4ec3112d4c3a",
            isoDigestIDs[12L]!!.toHex()
        )
        val isoUSDigestIDs = mso.getDigestIDs("org.iso.18013.5.1.US")
        assertEquals(setOf(0L, 1L, 2L, 3L), isoUSDigestIDs!!.keys)
        assertEquals(
            "d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c",
            isoUSDigestIDs[0L]!!.toHex()
        )
        assertEquals(
            "4d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd2917e34",
            isoUSDigestIDs[1L]!!.toHex()
        )
        assertEquals(
            "8b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544",
            isoUSDigestIDs[2L]!!.toHex()
        )
        assertEquals(
            "c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a87",
            isoUSDigestIDs[3L]!!.toHex()
        )
        val deviceKeyFromVector = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex(),
            TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex()
        )
        assertEquals(deviceKeyFromVector, mso.deviceKey)
        assertNull(mso.deviceKeyAuthorizedNameSpaces)
        assertNull(mso.deviceKeyAuthorizedDataElements)
        assertNull(mso.deviceKeyInfo)
        assertEquals(Instant.fromEpochMilliseconds(1601559002000L), mso.signed)
        assertEquals(Instant.fromEpochMilliseconds(1601559002000L), mso.validFrom)
        assertEquals(Instant.fromEpochMilliseconds(1633095002000L), mso.validUntil)
        assertNull(mso.expectedUpdate)
    }
}
