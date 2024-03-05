package com.android.identity.util

import org.junit.Assert
import org.junit.Test
import kotlin.random.Random

class HexUtilTest {

    @Test
    fun toHex() {
        Assert.assertEquals("", HexUtil.toHex(byteArrayOf()))
        Assert.assertEquals(
            "00ff13ab0b",
            HexUtil.toHex(byteArrayOf(0x00, 0xff.toByte(), 0x13, 0xAB.toByte(), 0x0B))
        )
    }

    @Test
    fun fromHex() {
        Assert.assertArrayEquals(ByteArray(0), HexUtil.fromHex(""))
        Assert.assertArrayEquals(
            byteArrayOf(0x00, 0xFF.toByte(), 0x13, 0xAB.toByte(), 0x0B),
            HexUtil.fromHex("00ff13ab0b")
        )
        Assert.assertArrayEquals(
            byteArrayOf(0x00, 0xFF.toByte(), 0x13, 0xAB.toByte(), 0x0B),
            HexUtil.fromHex("00FF13AB0B")
        )
    }

    @Test
    fun toHexFromHexRoundTrip() {
        val random = Random(31337) // deterministic but arbitrary
        for (numBytes in intArrayOf(0, 1, 2, 10, 50000)) {
            val bytes = ByteArray(numBytes)
            random.nextBytes(bytes)
            Assert.assertArrayEquals(bytes, HexUtil.fromHex(HexUtil.toHex(bytes)))
        }
    }

    @Test
    fun fromHexThrows() {
        Assert.assertThrows(IllegalArgumentException::class.java) { HexUtil.fromHex("0") }
        Assert.assertThrows(IllegalArgumentException::class.java) { HexUtil.fromHex("XX") }
    }

    @Test
    fun extensions() {
        Assert.assertArrayEquals(
            "deadbeef".fromHex,
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        )
        Assert.assertEquals(
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()).toHex,
            "deadbeef"
        )
    }

}