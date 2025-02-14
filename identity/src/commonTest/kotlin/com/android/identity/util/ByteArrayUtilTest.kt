package com.android.identity.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteArrayUtilTest {

    // PutXXX methods.
    @Test
    fun putInt8_withinRange() {
        val byteArray = ByteArray(1)
        byteArray.putInt8(Byte.MIN_VALUE.toInt())
        assertEquals(Byte.MIN_VALUE, byteArray[0])

        byteArray.putInt8(0)
        assertEquals(0, byteArray[0])

        byteArray.putInt8(Byte.MAX_VALUE.toInt())
        assertEquals(Byte.MAX_VALUE, byteArray[0])
    }

    @Test
    fun putInt8_outOfRange() {
        val byteArray = ByteArray(1)
        assertFailsWith<IllegalArgumentException> { byteArray.putInt8(Byte.MIN_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { byteArray.putInt8(Byte.MAX_VALUE + 1) }
    }

    @Test
    fun putInt16_withinRange() {
        val byteArray = ByteArray(2)
        byteArray.putInt16(Short.MIN_VALUE.toInt())
        assertEquals(Short.MIN_VALUE, byteArray.getInt16())

        byteArray.putInt16(0)
        assertEquals(0, byteArray.getInt16())

        byteArray.putInt16(Short.MAX_VALUE.toInt())
        assertEquals(Short.MAX_VALUE, byteArray.getInt16())
    }

    @Test
    fun putInt16_outOfRange() {
        val byteArray = ByteArray(2)
        assertFailsWith<IllegalArgumentException> { byteArray.putInt16(Short.MIN_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { byteArray.putInt16(Short.MAX_VALUE + 1) }
    }

    @Test
    fun putInt32_withinRange() {
        val byteArray = ByteArray(4)
        byteArray.putInt32(Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, byteArray.getInt32())

        byteArray.putInt32(0)
        assertEquals(0, byteArray.getInt32())

        byteArray.putInt32(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, byteArray.getInt32())
    }

    @Test
    fun putInt64_withinRange() {
        val byteArray = ByteArray(8)
        byteArray.putInt64(Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, byteArray.getInt64())

        byteArray.putInt64(0)
        assertEquals(0, byteArray.getInt64())

        byteArray.putInt64(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, byteArray.getInt64())
    }

    @Test
    fun putUInt8_withinRange() {
        val byteArray = ByteArray(1)
        byteArray.putUInt8(UByte.MIN_VALUE.toInt())
        assertEquals(UByte.MIN_VALUE, byteArray.getUInt8())

        byteArray.putUInt8(128)
        assertEquals(128u, byteArray.getUInt8())

        byteArray.putUInt8(UByte.MAX_VALUE.toInt())
        assertEquals(UByte.MAX_VALUE, byteArray.getUInt8())
    }

    @Test
    fun putUInt8_outOfRange() {
        val byteArray = ByteArray(1)
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt8(UByte.MIN_VALUE.toInt() - 1) }
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt8(UByte.MAX_VALUE.toInt() + 1) }
    }

    @Test
    fun putUInt16_withinRange() {
        val byteArray = ByteArray(2)
        byteArray.putUInt16(UShort.MIN_VALUE.toInt())
        assertEquals(UShort.MIN_VALUE, byteArray.getUInt16())

        byteArray.putUInt16(32768)
        assertEquals(32768u, byteArray.getUInt16())

        byteArray.putUInt16(UShort.MAX_VALUE.toInt())
        assertEquals(UShort.MAX_VALUE, byteArray.getUInt16())
    }

    @Test
    fun putUInt16_outOfRange() {
        val byteArray = ByteArray(2)
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt16(UShort.MIN_VALUE.toInt() - 1) }
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt16(UShort.MAX_VALUE.toInt() + 1) }
    }

    @Test
    fun putUInt32_withinRange() {
        val byteArray = ByteArray(4)
        byteArray.putUInt32(UInt.MIN_VALUE.toInt())
        assertEquals(UInt.MIN_VALUE, byteArray.getUInt32())

        byteArray.putUInt32(2147483647)
        assertEquals(2147483647u, byteArray.getUInt32())

        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt32(UInt.MAX_VALUE.toInt()) }
    }

    @Test
    fun putUInt32_outOfRange() {
        val byteArray = ByteArray(4)
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt32(-1) }
    }

    @Test
    fun putUInt64_withinRange() {
        val byteArray = ByteArray(8)
        byteArray.putUInt64(ULong.MIN_VALUE.toLong())
        assertEquals(ULong.MIN_VALUE, byteArray.getUInt64())

        byteArray.putUInt64(4294967295)
        assertEquals(4294967295u, byteArray.getUInt64())

        // The Long input should not be of ULong full range.
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt64(ULong.MAX_VALUE.toLong()) }
    }

    @Test
    fun putUInt64_outOfRange() {
        val byteArray = ByteArray(8)
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt64(-1) }
    }

    // GetXXX methods.
    @Test
    fun getInt8_valid() {
        val byteArray = ByteArray(1)
        byteArray[0] = Byte.MIN_VALUE
        assertEquals(Byte.MIN_VALUE, byteArray.getInt8())

        byteArray[0] = 0
        assertEquals(0, byteArray.getInt8())

        byteArray[0] = Byte.MAX_VALUE
        assertEquals(Byte.MAX_VALUE, byteArray.getInt8())
    }

    @Test
    fun getInt16_valid() {
        val byteArray = ByteArray(2)
        byteArray.putInt16(Short.MIN_VALUE.toInt())
        assertEquals(Short.MIN_VALUE, byteArray.getInt16())

        byteArray.putInt16(0)
        assertEquals(0, byteArray.getInt16())

        byteArray.putInt16(Short.MAX_VALUE.toInt())
        assertEquals(Short.MAX_VALUE, byteArray.getInt16())
    }

    @Test
    fun getInt32_valid() {
        val byteArray = ByteArray(4)
        byteArray.putInt32(Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, byteArray.getInt32())

        byteArray.putInt32(0)
        assertEquals(0, byteArray.getInt32())

        byteArray.putInt32(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, byteArray.getInt32())
    }

    @Test
    fun getInt64_valid() {
        val byteArray = ByteArray(8)
        byteArray.putInt64(Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, byteArray.getInt64())

        byteArray.putInt64(0)
        assertEquals(0, byteArray.getInt64())

        byteArray.putInt64(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, byteArray.getInt64())
    }

    @Test
    fun getUInt8_valid() {
        val byteArray = ByteArray(1)
        byteArray.putUInt8(UByte.MIN_VALUE.toInt())
        assertEquals(UByte.MIN_VALUE, byteArray.getUInt8())

        byteArray.putUInt8(128)
        assertEquals(128u, byteArray.getUInt8())

        byteArray.putUInt8(UByte.MAX_VALUE.toInt())
        assertEquals(UByte.MAX_VALUE, byteArray.getUInt8())
    }

    @Test
    fun getUInt16_valid() {
        val byteArray = ByteArray(2)
        byteArray.putUInt16(UShort.MIN_VALUE.toInt())
        assertEquals(UShort.MIN_VALUE, byteArray.getUInt16())

        byteArray.putUInt16(32768)
        assertEquals(32768u, byteArray.getUInt16())

        byteArray.putUInt16(UShort.MAX_VALUE.toInt())
        assertEquals(UShort.MAX_VALUE, byteArray.getUInt16())
    }

    @Test
    fun getUInt32_valid() {
        val byteArray = ByteArray(4)
        byteArray.putUInt32(UInt.MIN_VALUE.toInt())
        assertEquals(UInt.MIN_VALUE, byteArray.getUInt32())

        byteArray.putUInt32(2147483647)
        assertEquals(2147483647u, byteArray.getUInt32())

        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt32(UInt.MAX_VALUE.toInt()) }
    }

    @Test
    fun getUInt64_valid() {
        val byteArray = ByteArray(8)
        byteArray.putUInt64(ULong.MIN_VALUE.toLong())
        assertEquals(ULong.MIN_VALUE, byteArray.getUInt64())

        byteArray.putUInt64(4294967295)
        assertEquals(4294967295u, byteArray.getUInt64())

        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { byteArray.putUInt64(ULong.MAX_VALUE.toLong()) }
    }
    @Test
    fun getInt8_offset() {
        val byteArray = ByteArray(3)
        byteArray[1] = 100
        assertEquals(100, byteArray.getInt8(1))
    }

    @Test
    fun getInt16_offset() {
        val byteArray = ByteArray(4)
        byteArray.putInt16(256, 2)
        assertEquals(256, byteArray.getInt16(2))
    }

    @Test
    fun getInt32_offset() {
        val byteArray = ByteArray(6)
        byteArray.putInt32(1000000, 2)
        assertEquals(1000000, byteArray.getInt32(2))
    }

    @Test
    fun getInt64_offset() {
        val byteArray = ByteArray(10)
        byteArray.putInt64(10000000000, 2)
        assertEquals(10000000000, byteArray.getInt64(2))
    }

    @Test
    fun getUInt8_offset() {
        val byteArray = ByteArray(3)
        byteArray.putUInt8(200, 1)
        assertEquals(200u, byteArray.getUInt8(1))
    }

    @Test
    fun getUInt16_offset() {
        val byteArray = ByteArray(4)
        byteArray.putUInt16(60000, 2)
        assertEquals(60000u, byteArray.getUInt16(2))
    }

    @Test
    fun getUInt32_offset() {
        val byteArray = ByteArray(6)
        byteArray.putUInt32(3000000, 2)
        assertEquals(3000000u, byteArray.getUInt32(2))
    }

    @Test
    fun getUInt64_offset() {
        val byteArray = ByteArray(10)
        byteArray.putUInt64(10000000000, 2)
        assertEquals(10000000000u, byteArray.getUInt64(2))
    }
}