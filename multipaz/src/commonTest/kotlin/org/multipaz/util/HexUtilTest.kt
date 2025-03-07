package org.multipaz.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HexUtilTest {

    @Test
    fun toHex() {
        assertEquals("", HexUtil.toHex(byteArrayOf()))
        assertEquals(
            "00ff13ab0b",
            HexUtil.toHex(byteArrayOf(0x00, 0xff.toByte(), 0x13, 0xAB.toByte(), 0x0B))
        )
    }

    @Test
    fun toHexDecodeAsString() {
        assertEquals(
            " (\"\")",
            HexUtil.toHex(
                byteArrayOf(),
                byteDivider = " ",
                decodeAsString = true
            )
        )
        assertEquals(
            "00 ff 13 ab 0b 41 42 43 (\".�.�.ABC\")",
            HexUtil.toHex(
                byteArrayOf(0x00, 0xff.toByte(), 0x13, 0xab.toByte(), 0x0b, 0x41, 0x42, 0x43),
                byteDivider = " ",
                decodeAsString = true
            )
        )
    }

    @Test
    fun fromHex() {
        assertContentEquals(ByteArray(0), HexUtil.fromHex(""))
        assertContentEquals(
            byteArrayOf(0x00, 0xFF.toByte(), 0x13, 0xAB.toByte(), 0x0B),
            HexUtil.fromHex("00ff13ab0b")
        )
        assertContentEquals(
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
            assertContentEquals(bytes, HexUtil.fromHex(HexUtil.toHex(bytes)))
        }
    }

    @Test
    fun fromHexThrows() {
        assertFailsWith(IllegalArgumentException::class) { HexUtil.fromHex("0") }
        assertFailsWith(IllegalArgumentException::class) { HexUtil.fromHex("XX") }
    }

    @Test
    fun extensions() {
        assertContentEquals(
            "deadbeef".fromHex(),
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())
        )
        assertEquals(
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()).toHex(),
            "deadbeef"
        )
    }

}