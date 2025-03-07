package org.multipaz.cbor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrayBuilderTests {
    @Test
    fun testEmptyArray() {
        val builder = CborArray.builder()
        assertTrue { builder.isEmpty() }

        val array = builder.end().build()
        assertEquals(CborArray(mutableListOf()), array)
    }

    @Test
    fun testNonEmptyArray() {
        val builder = CborArray.builder()
        builder.add(1)
        builder.add(2)
        assertFalse { builder.isEmpty() }

        val array = builder.end().build()
        assertEquals(CborArray(mutableListOf(1.toDataItem(), 2.toDataItem())), array)
    }
}