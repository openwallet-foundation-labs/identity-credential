package com.android.identity.util

import kotlin.test.Test
import kotlin.test.assertEquals

class X509ViewDataKtTest {

    @Test
    fun testBases() {
        // Test case 1: Base 10
        val bytes1 = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val expected1 = "305419896"
        assertEquals(expected1, bytes1.unsignedBigIntToString())

        // Test case 2: Base 16
        val bytes2 = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val expected2 = "12345678"
        assertEquals(expected2, bytes2.unsignedBigIntToString(16))

        // Test case 3: Base 2
        val bytes3 = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00)
        val expected3 = "10000000000000000000000000000000"
        assertEquals(expected3, bytes3.unsignedBigIntToString(2))
    }

    @Test
    fun testEmptyArray() {
        val bytes = byteArrayOf()
        val expected = ""
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testSingleByte() {
        val bytes = byteArrayOf(0x0A)
        val expected = "10"
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testMultiByte() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val expected = "4328719365"
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testLarge() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expected = "4294967295"
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testZero() {
        val bytes = byteArrayOf(0x00)
        val expected = "0"
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testZeroesArray() {
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val expected = "0"
        assertEquals(expected, bytes.unsignedBigIntToString())
    }

    @Test
    fun testDecodeUnsignedBigEndianToString_veryLargeNumber() {
        val byteArray = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        val expected = "115792089237316195423570985008687907853269984665640564039457584007913129639935" // 2^256 - 1
        val actual = byteArray.unsignedBigIntToString()
        assertEquals(expected, actual)
    }

    @Test
    fun testDecodeUnsignedBigEndianToString_20Bytes() {
        val byteArray = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        val expected = "1461501637330902918203684832716283019655932542975" // 2^160 - 1
        val actual = byteArray.unsignedBigIntToString()
        assertEquals(expected, actual)
    }
}
