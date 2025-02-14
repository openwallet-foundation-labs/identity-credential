package com.android.identity.util

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BufferExtensionsTest {

    // Get.
    @Test
    fun getInt8_valid() {
        val buffer = Buffer().apply { writeByte(10) }
        assertEquals(10, buffer.getInt8())
    }

    @Test
    fun getInt8_exhausted() {
        val buffer = Buffer()
        assertFailsWith<IllegalStateException> { buffer.getInt8() }
    }

    @Test
    fun getInt8_negative() {
        val buffer = Buffer().apply { writeByte(-10) }
        assertEquals(-10, buffer.getInt8())
    }

    @Test
    fun getInt8_zero() {
        val buffer = Buffer().apply { writeByte(0) }
        assertEquals(0, buffer.getInt8())
    }

    @Test
    fun getInt8_max() {
        val buffer = Buffer().apply { writeByte(Byte.MAX_VALUE) }
        assertEquals(Byte.MAX_VALUE, buffer.getInt8())
    }

    @Test
    fun getInt8_min() {
        val buffer = Buffer().apply { writeByte(Byte.MIN_VALUE) }
        assertEquals(Byte.MIN_VALUE, buffer.getInt8())
    }

    @Test
    fun getInt16_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x0A, 0x0B)) }
        assertEquals(2826, buffer.getInt16())
    }

    @Test
    fun getInt16_notEnoughBytes() {
        val buffer = Buffer().apply { writeByte(0x0A) }
        assertFailsWith<IllegalStateException> { buffer.getInt16() }
    }

    @Test
    fun getInt16_negative() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(-1, buffer.getInt16())
    }

    @Test
    fun getInt16_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00)) }
        assertEquals(0, buffer.getInt16())
    }

    @Test
    fun getInt16_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0x7F)) }
        assertEquals(Short.MAX_VALUE, buffer.getInt16())
    }

    @Test
    fun getInt16_min() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x80.toByte())) }
        assertEquals(Short.MIN_VALUE, buffer.getInt16())
    }

    @Test
    fun getInt32_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04)) }
        assertEquals(67305985, buffer.getInt32())
    }

    @Test
    fun getInt32_notEnoughBytes() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03)) }
        assertFailsWith<IllegalStateException> { buffer.getInt32() }
    }

    @Test
    fun getInt32_negative() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(-1, buffer.getInt32())
    }

    @Test
    fun getInt32_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) }
        assertEquals(0, buffer.getInt32())
    }

    @Test
    fun getInt32_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)) }
        assertEquals(Int.MAX_VALUE, buffer.getInt32())
    }

    @Test
    fun getInt32_min() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte())) }
        assertEquals(Int.MIN_VALUE, buffer.getInt32())
    }

    @Test
    fun getInt64_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)) }
        assertEquals(578437695752307201, buffer.getInt64())
    }

    @Test
    fun getInt64_notEnoughBytes() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)) }
        assertFailsWith<IllegalStateException> { buffer.getInt64() }
    }

    @Test
    fun getInt64_negative() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(-1, buffer.getInt64())
    }

    @Test
    fun getInt64_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)) }
        assertEquals(0, buffer.getInt64())
    }

    @Test
    fun getInt64_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)) }
        assertEquals(Long.MAX_VALUE, buffer.getInt64())
    }

    @Test
    fun getInt64_min() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80.toByte())) }
        assertEquals(Long.MIN_VALUE, buffer.getInt64())
    }

    @Test
    fun getUInt8_valid() {
        val buffer = Buffer().apply { writeByte(10) }
        assertEquals(10u, buffer.getUInt8())
    }

    @Test
    fun getUInt8_exhausted() {
        val buffer = Buffer()
        assertFailsWith<IllegalStateException> { buffer.getUInt8() }
    }

    @Test
    fun getUInt8_zero() {
        val buffer = Buffer().apply { writeByte(0) }
        assertEquals(0u, buffer.getUInt8())
    }

    @Test
    fun getUInt8_max() {
        val buffer = Buffer().apply { writeByte(0xFF.toByte()) }
        assertEquals(UByte.MAX_VALUE, buffer.getUInt8())
    }

    @Test
    fun getUInt16_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x0A, 0x0B)) }
        assertEquals(2826u, buffer.getUInt16())
    }

    @Test
    fun getUInt16_notEnoughBytes() {
        val buffer = Buffer().apply { writeByte(0x0A) }
        assertFailsWith<IllegalStateException> { buffer.getUInt16() }
    }

    @Test
    fun getUInt16_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00)) }
        assertEquals(0u, buffer.getUInt16())
    }

    @Test
    fun getUInt16_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(UShort.MAX_VALUE, buffer.getUInt16())
    }

    @Test
    fun getUInt32_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04)) }
        assertEquals(67305985u, buffer.getUInt32())
    }

    @Test
    fun getUInt32_notEnoughBytes() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03)) }
        assertFailsWith<IllegalStateException> { buffer.getUInt32() }
    }

    @Test
    fun getUInt32_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) }
        assertEquals(0u, buffer.getUInt32())
    }

    @Test
    fun getUInt32_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(UInt.MAX_VALUE, buffer.getUInt32())
    }

    @Test
    fun getUInt64_valid() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)) }
        assertEquals(578437695752307201u, buffer.getUInt64())
    }

    @Test
    fun getUInt64_notEnoughBytes() {
        val buffer = Buffer().apply { write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)) }
        assertFailsWith<IllegalStateException> { buffer.getUInt64() }
    }

    @Test
    fun getUInt64_zero() {
        val buffer = Buffer().apply { write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)) }
        assertEquals(0u, buffer.getUInt64())
    }

    @Test
    fun getUInt64_max() {
        val buffer = Buffer().apply { write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) }
        assertEquals(ULong.MAX_VALUE, buffer.getUInt64())
    }

    @Test
    fun getByte_valid() {
        val buffer = Buffer().apply { writeByte(10) }
        assertEquals(10, buffer.getByte())
    }

    @Test
    fun getByte_exhausted() {
        val buffer = Buffer()
        assertFailsWith<IllegalStateException> { buffer.getByte() }
    }

    @Test
    fun getByte_negative() {
        val buffer = Buffer().apply { writeByte(-10) }
        assertEquals(-10, buffer.getByte())
    }

    @Test
    fun getByte_zero() {
        val buffer = Buffer().apply { writeByte(0) }
        assertEquals(0, buffer.getByte())
    }

    @Test
    fun getByte_max() {
        val buffer = Buffer().apply { writeByte(Byte.MAX_VALUE) }
        assertEquals(Byte.MAX_VALUE, buffer.getByte())
    }

    @Test
    fun getByte_min() {
        val buffer = Buffer().apply { writeByte(Byte.MIN_VALUE) }
        assertEquals(Byte.MIN_VALUE, buffer.getByte())
    }

    // Put.
    @Test
    fun putInt8_valid() {
        val buffer = Buffer()
        buffer.putInt8(10)
        assertEquals(10, buffer.readByte())
    }

    @Test
    fun putInt8_outOfRange_tooHigh() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putInt8(128) }
    }

    @Test
    fun putInt8_outOfRange_tooLow() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putInt8(-129) }
    }

    @Test
    fun putInt8_zero() {
        val buffer = Buffer()
        buffer.putInt8(0)
        assertEquals(0, buffer.readByte())
    }

    @Test
    fun putInt8_max() {
        val buffer = Buffer()
        buffer.putInt8(Byte.MAX_VALUE.toInt())
        assertEquals(Byte.MAX_VALUE, buffer.readByte())
    }

    @Test
    fun putInt8_min() {
        val buffer = Buffer()
        buffer.putInt8(Byte.MIN_VALUE.toInt())
        assertEquals(Byte.MIN_VALUE, buffer.readByte())
    }

    @Test
    fun putInt16_valid() {
        val buffer = Buffer()
        buffer.putInt16(2826)
        assertEquals(byteArrayOf(0x0A, 0x0B).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt16_outOfRange_tooHigh() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putInt16(32768) }
    }

    @Test
    fun putInt16_outOfRange_tooLow() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putInt16(-32769) }
    }

    @Test
    fun putInt16_zero() {
        val buffer = Buffer()
        buffer.putInt16(0)
        assertEquals(byteArrayOf(0x00, 0x00).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt16_max() {
        val buffer = Buffer()
        buffer.putInt16(Short.MAX_VALUE.toInt())
        assertEquals(byteArrayOf(0xFF.toByte(), 0x7F).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt16_min() {
        val buffer = Buffer()
        buffer.putInt16(Short.MIN_VALUE.toInt())
        assertEquals(byteArrayOf(0x00, 0x80.toByte()).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt32_valid() {
        val buffer = Buffer()
        buffer.putInt32(67305985)
        assertEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt32_zero() {
        val buffer = Buffer()
        buffer.putInt32(0)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt32_max() {
        val buffer = Buffer()
        buffer.putInt32(Int.MAX_VALUE)
        assertEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putInt32_min() {
        val buffer = Buffer()
        buffer.putInt32(Int.MIN_VALUE)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte()).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt32_negative() {
        val buffer = Buffer()
        buffer.putInt32(-1)
        assertEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putInt64_valid() {
        val buffer = Buffer()
        buffer.putInt64(578437695752307201)
        assertEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putInt64_zero() {
        val buffer = Buffer()
        buffer.putInt64(0)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putInt64_max() {
        val buffer = Buffer()
        buffer.putInt64(Long.MAX_VALUE)
        assertEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x7F).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putInt64_min() {
        val buffer = Buffer()
        buffer.putInt64(Long.MIN_VALUE)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80.toByte()).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putInt64_negative() {
        val buffer = Buffer()
        buffer.putInt64(-1)
        assertEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt8_valid() {
        val buffer = Buffer()
        buffer.putUInt8(10)
        assertEquals(10, buffer.readByte().toInt())
    }

    @Test
    fun putUInt8_outOfRange_tooHigh() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt8(256) }
    }

    @Test
    fun putUInt8_outOfRange_tooLow() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt8(-1) }
    }

    @Test
    fun putUInt8_zero() {
        val buffer = Buffer()
        buffer.putUInt8(0)
        assertEquals(0, buffer.readByte().toInt())
    }

    @Test
    fun putUInt8_max() {
        val buffer = Buffer()
        buffer.putUInt8(UByte.MAX_VALUE.toInt())
        assertEquals(0xFF.toByte(), buffer.readByte())
    }

    @Test
    fun putUInt16_valid() {
        val buffer = Buffer()
        buffer.putUInt16(2826)
        assertEquals(byteArrayOf(0x0A, 0x0B).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt16_outOfRange_tooHigh() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt16(65536) }
    }

    @Test
    fun putUInt16_outOfRange_tooLow() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt16(-1) }
    }

    @Test
    fun putUInt16_zero() {
        val buffer = Buffer()
        buffer.putUInt16(0)
        assertEquals(byteArrayOf(0x00, 0x00).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt16_max() {
        val buffer = Buffer()
        buffer.putUInt16(UShort.MAX_VALUE.toInt())
        assertEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt32_valid() {
        val buffer = Buffer()
        buffer.putUInt32(67305985)
        assertEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt32_outOfRange() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt32(-1) }
    }

    @Test
    fun putUInt32_zero() {
        val buffer = Buffer()
        buffer.putUInt32(0)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00).toList(), buffer.readByteArray().toList())
    }

    @Test
    fun putUInt32_max() {
        val buffer = Buffer()
        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { buffer.putUInt32(-1) }
    }

    @Test
    fun putUInt64_valid() {
        val buffer = Buffer()
        buffer.putUInt64(578437695752307201)
        assertEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putUInt64_outOfRange() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putUInt64(-1) }
    }

    @Test
    fun putUInt64_zero() {
        val buffer = Buffer()
        buffer.putUInt64(0)
        assertEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).toList(),
            buffer.readByteArray().toList())
    }

    @Test
    fun putUInt64_max() {
        val buffer = Buffer()
        // The Int input should not be of UInt full range.
        assertFailsWith<IllegalArgumentException> { buffer.putUInt64(-1) }
    }

    @Test
    fun putByte_valid() {
        val buffer = Buffer()
        buffer.putByte(10)
        assertEquals(10, buffer.readByte())
    }

    @Test
    fun putByte_outOfRange_tooHigh() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putByte(128) }
    }

    @Test
    fun putByte_outOfRange_tooLow() {
        val buffer = Buffer()
        assertFailsWith<IllegalArgumentException> { buffer.putByte(-129) }
    }

    @Test
    fun putByte_zero() {
        val buffer = Buffer()
        buffer.putByte(0)
        assertEquals(0, buffer.readByte())
    }

    @Test
    fun putByte_max() {
        val buffer = Buffer()
        buffer.putByte(Byte.MAX_VALUE.toInt())
        assertEquals(Byte.MAX_VALUE, buffer.readByte())
    }

    @Test
    fun putByte_min() {
        val buffer = Buffer()
        buffer.putByte(Byte.MIN_VALUE.toInt())
        assertEquals(Byte.MIN_VALUE, buffer.readByte())
    }
}