package org.multipaz.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompressionTests {
    
    @Test fun roundTripLevel0() { roundTrip(0) }
    @Test fun roundTripLevel1() { roundTrip(1) }
    @Test fun roundTripLevel2() { roundTrip(2) }
    @Test fun roundTripLevel3() { roundTrip(3) }
    @Test fun roundTripLevel4() { roundTrip(4) }
    @Test fun roundTripLevel5() { roundTrip(5) }
    @Test fun roundTripLevel6() { roundTrip(6) }
    @Test fun roundTripLevel7() { roundTrip(7) }
    @Test fun roundTripLevel8() { roundTrip(8) }
    @Test fun roundTripLevel9() { roundTrip(9) }

    fun roundTrip(level: Int) {
        val sb = StringBuilder()
        repeat(1000) {
            sb.append("Hello Multipaz!\n")
        }
        val data = sb.toString().encodeToByteArray()

        val compressedData = deflate(data, level)
        if (level > 0) {
            assertTrue(compressedData.size < data.size)
        }
        val decompressedData = inflate(compressedData)
        assertContentEquals(decompressedData, data)
    }

    @Test
    fun testVector() {
        val sb = StringBuilder()
        repeat(1000) {
            sb.append("Hello Multipaz!\n")
        }
        val expectedData = sb.toString().encodeToByteArray()

        val knownCompressedDataBase64Url =
            "7cehDcAgEABA3ynoNm8YAoFo8gkIMEzPIL1zFz1zlLpzfbOd9wl3d3d3d3d3d3d3d3d3d3d3d3d3d3d3d3d3d3d3d3f_5S8"
        val decompressedData = inflate(knownCompressedDataBase64Url.fromBase64Url())
        assertContentEquals(expectedData, decompressedData)
    }

    @Test
    fun inflateWithUnsupportedLevel() {
        assertFailsWith(IllegalArgumentException::class) { deflate(byteArrayOf(1, 2), -10) }
        assertFailsWith(IllegalArgumentException::class) { deflate(byteArrayOf(1, 2), -1) }
        assertFailsWith(IllegalArgumentException::class) { deflate(byteArrayOf(1, 2), 10) }
        assertFailsWith(IllegalArgumentException::class) { deflate(byteArrayOf(1, 2), 11) }
    }

    @Test
    fun inflateWithInvalidData() {
        assertFailsWith(IllegalArgumentException::class) {
            inflate(byteArrayOf(1, 2))
        }
    }
}