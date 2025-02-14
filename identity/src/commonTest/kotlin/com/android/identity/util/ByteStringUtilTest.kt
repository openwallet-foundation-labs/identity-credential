package com.android.identity.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.io.bytestring.buildByteString

/** Tests are leveraging tested implementation of same extensions for the ByteArray. */
class ByteStringBuilderExtensionsTest {
    // Signed.
    @Test
    fun appendInt8_withinRange() {
        assertDoesNotThrow { buildByteString { appendInt8(Byte.MIN_VALUE.toInt()) } }
        assertDoesNotThrow { buildByteString { appendInt8(0) } }
        assertDoesNotThrow { buildByteString { appendInt8(Byte.MAX_VALUE.toInt()) } }
    }

    @Test
    fun appendInt8_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendInt8(Byte.MIN_VALUE.toInt() - 1) } }
        assertFailsWith<IllegalArgumentException> { buildByteString { appendInt8(Byte.MAX_VALUE.toInt() + 1) } }
    }

    @Test
    fun appendInt8_readBack() {
        val byteString = buildByteString { appendInt8(100) }
        assertEquals(100, byteString.toByteArray().getInt8())
    }

    @Test
    fun appendInt16_withinRange() {
        assertDoesNotThrow { buildByteString { appendInt16(Short.MIN_VALUE.toInt()) } }
        assertDoesNotThrow { buildByteString { appendInt16(0) } }
        assertDoesNotThrow { buildByteString { appendInt16(Short.MAX_VALUE.toInt()) } }
    }

    @Test
    fun appendInt16_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendInt16(Short.MIN_VALUE.toInt() - 1) } }
        assertFailsWith<IllegalArgumentException> { buildByteString { appendInt16(Short.MAX_VALUE.toInt() + 1) } }
    }

    @Test
    fun appendInt16_readBack() {
        val byteString = buildByteString { appendInt16(256) }
        assertEquals(256, byteString.toByteArray().getInt16())
    }

    @Test
    fun appendInt32_valid() {
        val byteString = buildByteString { appendInt32(Int.MIN_VALUE) }
        assertEquals(Int.MIN_VALUE, byteString.toByteArray().getInt32())

        val byteString2 = buildByteString { appendInt32(0) }
        assertEquals(0, byteString2.toByteArray().getInt32())

        val byteString3 = buildByteString { appendInt32(Int.MAX_VALUE) }
        assertEquals(Int.MAX_VALUE, byteString3.toByteArray().getInt32())
    }

    @Test
    fun appendInt64_valid() {
        val byteString = buildByteString { appendInt64(Long.MIN_VALUE) }
        assertEquals(Long.MIN_VALUE, byteString.toByteArray().getInt64())

        val byteString2 = buildByteString { appendInt64(0) }
        assertEquals(0, byteString2.toByteArray().getInt64())

        val byteString3 = buildByteString { appendInt64(Long.MAX_VALUE) }
        assertEquals(Long.MAX_VALUE, byteString3.toByteArray().getInt64())
    }

    @Test
    fun appendByte_withinRange() {
        assertDoesNotThrow { buildByteString { appendByte(Byte.MIN_VALUE.toInt()) } }
        assertDoesNotThrow { buildByteString { appendByte(0) } }
        assertDoesNotThrow { buildByteString { appendByte(Byte.MAX_VALUE.toInt()) } }
    }

    @Test
    fun appendByte_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendByte(Byte.MIN_VALUE.toInt() - 1) } }
        assertFailsWith<IllegalArgumentException> { buildByteString { appendByte(Byte.MAX_VALUE.toInt() + 1) } }
    }

    // Unsigned.
    @Test
    fun appendUInt8_withinRange() {
        assertDoesNotThrow { buildByteString { appendUInt8(UByte.MIN_VALUE.toInt()) } }
        assertDoesNotThrow { buildByteString { appendUInt8(128) } }
        assertDoesNotThrow { buildByteString { appendUInt8(UByte.MAX_VALUE.toInt()) } }
    }

    @Test
    fun appendUInt8_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt8(UByte.MIN_VALUE.toInt() - 1) } }
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt8(UByte.MAX_VALUE.toInt() + 1) } }
    }

    @Test
    fun appendUInt8_readBack() {
        val byteString = buildByteString { appendUInt8(200) }
        assertEquals(200u, byteString.toByteArray().getUInt8())
    }

    @Test
    fun appendUInt16_withinRange() {
        assertDoesNotThrow { buildByteString { appendUInt16(UShort.MIN_VALUE.toInt()) } }
        assertDoesNotThrow { buildByteString { appendUInt16(32768) } }
        assertDoesNotThrow { buildByteString { appendUInt16(UShort.MAX_VALUE.toInt()) } }
    }

    @Test
    fun appendUInt16_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt16(UShort.MIN_VALUE.toInt() - 1) } }
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt16(UShort.MAX_VALUE.toInt() + 1) } }
    }

    @Test
    fun appendUInt16_readBack() {
        val byteString = buildByteString { appendUInt16(50000) }
        assertEquals(50000u, byteString.toByteArray().getUInt16())
    }

    @Test
    fun appendUInt32_valid() {
        val byteString = buildByteString { appendUInt32(UInt.MIN_VALUE.toInt()) }
        assertEquals(UInt.MIN_VALUE, byteString.toByteArray().getUInt32())

        val byteString2 = buildByteString { appendUInt32(2147483647) }
        assertEquals(2147483647u, byteString2.toByteArray().getUInt32())

        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt32(UInt.MAX_VALUE.toInt()) }}
    }

    @Test
    fun appendUInt32_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt32(-1) } }
    }

    @Test
    fun appendUInt64_valid() {
        val byteString = buildByteString { appendUInt64(ULong.MIN_VALUE.toLong()) }
        assertEquals(ULong.MIN_VALUE, byteString.toByteArray().getUInt64())

        val byteString2 = buildByteString { appendUInt64(4294967295) }
        assertEquals(4294967295u, byteString2.toByteArray().getUInt64())

        // The Long input should not be of ULong full range.
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt64(ULong.MAX_VALUE.toLong()) } }
    }

    @Test
    fun appendUInt64_outOfRange() {
        assertFailsWith<IllegalArgumentException> { buildByteString { appendUInt64(-1) } }
    }
}

// Helper function to assert that a block of code does not throw an exception
fun assertDoesNotThrow(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        kotlin.test.fail("Expected no exception, but got: $e")
    }
}

