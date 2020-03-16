/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.cbordata.security

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P256
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P384
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P521
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP256r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP320r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP384r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP512r1
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigInteger

class CoseKeyTest {
    private val coseKeyData = byteArrayOf(
        0xA4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x3A.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x42.toByte(), 0x83.toByte(), 0x9F.toByte(), 0x8A.toByte(), 0xE3.toByte(), 0xBA.toByte(), 0x44.toByte(), 0x85.toByte(), 0x19.toByte(), 0x03.toByte(), 0x0D.toByte(), 0xB0.toByte(), 0xF0.toByte(), 0x0E.toByte(), 0xEC.toByte(), 0xCD.toByte(), 0x26.toByte(), 0x27.toByte(), 0xE5.toByte(), 0x96.toByte(), 0x01.toByte(), 0xC5.toByte(), 0x97.toByte(), 0x11.toByte(), 0xAD.toByte(), 0x65.toByte(), 0x8E.toByte(), 0x3D.toByte(), 0xB3.toByte(), 0x32.toByte(), 0xBF.toByte(), 0x89.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x7B.toByte(), 0x78.toByte(), 0x4E.toByte(), 0x80.toByte(), 0xDE.toByte(), 0xCD.toByte(), 0xED.toByte(), 0xA4.toByte(), 0x73.toByte(), 0xA9.toByte(), 0x06.toByte(), 0x77.toByte(), 0x78.toByte(), 0x8A.toByte(), 0x9B.toByte(), 0x76.toByte(), 0x56.toByte(), 0x70.toByte(), 0x2C.toByte(), 0x58.toByte(), 0x37.toByte(), 0xD6.toByte(), 0x27.toByte(), 0xC9.toByte(), 0x55.toByte(), 0xBE.toByte(), 0xB5.toByte(), 0x0D.toByte(), 0xAB.toByte(), 0xCA.toByte(), 0xC5.toByte(), 0xD7.toByte()
    )
    private val keyType = 2
    private val xCoordinate = byteArrayOf(0x42.toByte(), 0x83.toByte(), 0x9F.toByte(), 0x8A.toByte(), 0xE3.toByte(), 0xBA.toByte(), 0x44.toByte(), 0x85.toByte(), 0x19.toByte(), 0x03.toByte(), 0x0D.toByte(), 0xB0.toByte(), 0xF0.toByte(), 0x0E.toByte(), 0xEC.toByte(), 0xCD.toByte(), 0x26.toByte(), 0x27.toByte(), 0xE5.toByte(), 0x96.toByte(), 0x01.toByte(), 0xC5.toByte(), 0x97.toByte(), 0x11.toByte(), 0xAD.toByte(), 0x65.toByte(), 0x8E.toByte(), 0x3D.toByte(), 0xB3.toByte(), 0x32.toByte(), 0xBF.toByte(), 0x89.toByte())
    private val yCoordinate = byteArrayOf(0x7B.toByte(), 0x78.toByte(), 0x4E.toByte(), 0x80.toByte(), 0xDE.toByte(), 0xCD.toByte(), 0xED.toByte(), 0xA4.toByte(), 0x73.toByte(), 0xA9.toByte(), 0x06.toByte(), 0x77.toByte(), 0x78.toByte(), 0x8A.toByte(), 0x9B.toByte(), 0x76.toByte(), 0x56.toByte(), 0x70.toByte(), 0x2C.toByte(), 0x58.toByte(), 0x37.toByte(), 0xD6.toByte(), 0x27.toByte(), 0xC9.toByte(), 0x55.toByte(), 0xBE.toByte(), 0xB5.toByte(), 0x0D.toByte(), 0xAB.toByte(), 0xCA.toByte(), 0xC5.toByte(), 0xD7.toByte())

    @Test
    fun testEncodeCoseKey() {
        val builder = CoseKey.Builder()
        builder.setKeyType(keyType)
        builder.setCurve(brainpoolP256r1.value.toInt(), xCoordinate, yCoordinate, null)
        val cKey = builder.build().encode()

        Assert.assertArrayEquals(coseKeyData, cKey)
    }

    @Test
    fun testEncodeCoseKey_KeyTypeTstr() {
        val expectedCKeyData = byteArrayOf(
            0xA1.toByte(), 0x01.toByte(), 0x63.toByte(), 0x74.toByte(), 0x62.toByte(), 0x64.toByte()
        )
        val builder = CoseKey.Builder()
        builder.setKeyType("tbd")
        val cKey = builder.build().encode()

        Assert.assertArrayEquals(expectedCKeyData, cKey)
    }

    @Test
    fun testDecodeCoseKey() {
        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType)
        Assert.assertTrue(cKey.curve?.id == brainpoolP256r1.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    // ** CoseKey tests for curves which support point compression ** //

    @Test
    fun testDecodeCoseKeyCompressedP256() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = P256.value.toLong()

        val xCoordinate =
            Hex.decode("72DA7197 6234CE83 3A690742 5867B82E 074D44EF 907DFB4B 3E21C1C2 256EBCD1")
        val yCoordinate =
            Hex.decode("5A7DED52 FCBB097A 4ED250E0 36C7B9C8 C7004C4E EDC4F068 CD7BF8D3 F900E3B4")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == P256.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedP384() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = P384.value.toLong()

        val xCoordinate =
            Hex.decode("AA87CA22 BE8B0537 8EB1C71E F320AD74 6E1D3B62 8BA79B98 59F741E0 82542A38 5502F25D BF55296C 3A545E38 72760AB7")
        val yCoordinate =
            Hex.decode("3617DE4A 96262C6F 5D9E98BF 9292DC29 F8F41DBD 289A147C E9DA3113 B5F0B8C0 0A60B1CE 1D7E819D 7A431D7C 90EA0E5F")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == P384.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedP521() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = P521.value.toLong()

        val xCoordinate =
            Hex.decode("00C6858E 06B70404 E9CD9E3E CB662395 B4429C64 8139053F B521F828 AF606B4D 3DBAA14B 5E77EFE7 5928FE1D C127A2FF A8DE3348 B3C1856A 429BF97E 7E31C2E5 BD66")
        val yCoordinate =
            Hex.decode("01183929 6A789A3B C0045C8A 5FB42C7D 1BD998F5 4449579B 446817AF BD17273E 662C97EE 72995EF4 2640C550 B9013FAD 0761353C 7086A272 C24088BE 94769FD1 6650")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == P521.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedBrainpoolP256() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = brainpoolP256r1.value.toLong()

        val xCoordinate =
            Hex.decode("78028496 B5ECAAB3 C8B6C12E 45DB1E02 C9E4D26B 4113BC4F 015F60C5 CCC0D206")
        val yCoordinate =
            Hex.decode("A2AE1762 A3831C1D 20F03F8D 1E3C0C39 AFE6F09B 4D44BBE8 0CD10098 7B05F92B")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == brainpoolP256r1.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedBrainpoolP320() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = brainpoolP320r1.value.toLong()

        val xCoordinate =
            Hex.decode("43BD7E9A FB53D8B8 5289BCC4 8EE5BFE6 F20137D1 0A087EB6 E7871E2A 10A599C7 10AF8D0D 39E20611")
        val yCoordinate =
            Hex.decode("14FDD055 45EC1CC8 AB409324 7F77275E 0743FFED 117182EA A9C77877 AAAC6AC7 D35245D1 692E8EE1")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == brainpoolP320r1.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedBrainpoolP384() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = brainpoolP384r1.value.toLong()

        val xCoordinate =
            Hex.decode("45CB26E4 384DAF6F B7768853 07B9A38B 7AD1B5C6 92E0C32F 01253327 78F3B8D3 F50CA358 099B30DE B5EE69A9 5C058B4E")
        val yCoordinate =
            Hex.decode("8173A1C5 4AFFA7E7 81D0E1E1 D12C0DC2 B74F4DF5 8E4A4E3A F7026C5D 32DC530A 2CD89C85 9BB4B4B7 68497F49 AB8CC859")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == brainpoolP384r1.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKeyCompressedBrainpoolP512() {
        val keyType: Long = keyType.toLong()
        val curveId: Long = brainpoolP512r1.value.toLong()

        val xCoordinate =
            Hex.decode("0562E68B 9AF7CBFD 5565C6B1 6883B777 FF11C199 161ECC42 7A39D17E C2166499 389571D6 A994977C 56AD8252 658BA8A1 B72AE42F 4FB75321 51AFC3EF 0971CCDA")
        val yCoordinate =
            Hex.decode("A7CA2D81 91E21776 A89860AF BC1F582F AA308D55 1C1DC613 3AF9F9C3 CAD59998 D7007954 8140B90B 1F311AFB 378AA81F 51B275B2 BE6B7DEE 978EFC73 43EA642E")

        val yCompressed = BigInteger(1, yCoordinate)
            .mod(BigInteger.valueOf(2)) == BigInteger.ONE

        var builder = CborBuilder()
        val map = builder.addMap()
            .put(CoseKey.KEYTYPE_LABEL.value.toLong(), keyType)
            .put(CoseKey.CURVEID_LABEL.value.toLong(), curveId)
            .put(CoseKey.XCOORDINATE_LABEL.value.toLong(), xCoordinate)
            .put(CoseKey.YCOORDINATE_LABEL.value.toLong(), yCompressed)
        builder = map.end()
        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(builder.build())

        val coseKeyData = outputStream.toByteArray()

        val cKey = CoseKey.Builder().decode(coseKeyData).build()

        Assert.assertTrue(cKey.keyType == keyType.toInt())
        Assert.assertTrue(cKey.curve?.id == brainpoolP512r1.value.toInt())
        Assert.assertArrayEquals(xCoordinate, cKey.curve?.xCoordinate)
        Assert.assertArrayEquals(yCoordinate, cKey.curve?.yCoordinate)
        Assert.assertNull(cKey.curve?.privateKey)
    }

    @Test
    fun testDecodeCoseKey_NewData() {
        val ckeyData = byteArrayOf(
            0xA4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0xDC.toByte(), 0x00.toByte(), 0x21.toByte(), 0xB2.toByte(), 0x1D.toByte(), 0x91.toByte(), 0x2C.toByte(), 0xB7.toByte(), 0x03.toByte(), 0x47.toByte(), 0x71.toByte(), 0xA0.toByte(), 0x4F.toByte(), 0xE0.toByte(), 0xD1.toByte(), 0xB2.toByte(), 0x0C.toByte(), 0x83.toByte(), 0x22.toByte(), 0x08.toByte(), 0x40.toByte(), 0x44.toByte(), 0xE2.toByte(), 0x2B.toByte(), 0x0E.toByte(), 0x2C.toByte(), 0xD3.toByte(), 0x75.toByte(), 0x22.toByte(), 0x05.toByte(), 0x58.toByte(), 0xD5.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x30.toByte(), 0xDA.toByte(), 0x7A.toByte(), 0x5C.toByte(), 0xD4.toByte(), 0x18.toByte(), 0x49.toByte(), 0xAD.toByte(), 0xB1.toByte(), 0x67.toByte(), 0xEC.toByte(), 0x99.toByte(), 0xEA.toByte(), 0x6D.toByte(), 0xEF.toByte(), 0xA9.toByte(), 0x4D.toByte(), 0xDD.toByte(), 0x21.toByte(), 0x0E.toByte(), 0xF7.toByte(), 0xFD.toByte(), 0x52.toByte(), 0x6E.toByte(), 0x6F.toByte(), 0x2C.toByte(), 0xC1.toByte(), 0xF1.toByte(), 0xFC.toByte(), 0xBD.toByte(), 0x0B.toByte(), 0xD5.toByte()
        )
        val cKey = CoseKey.Builder().decode(ckeyData).build()

        Assert.assertNotNull(cKey)
        Assert.assertTrue(cKey.keyType == 2)
        Assert.assertTrue(cKey.curve?.id == P256.value.toInt())
        Assert.assertNotNull(cKey.curve?.xCoordinate)
        Assert.assertNotNull(cKey.curve?.yCoordinate)
    }
}