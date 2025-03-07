package org.multipaz.asn1

import org.multipaz.util.toHex
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class ASN1TestsJvm {

    // Checks that DER encoding routines for Longs behave as expected, that is, that they
    // are encoded the same way as Java's BigInteger.
    //
    @Test
    fun testLongEncodeDecode() {
        for (n in listOf(0, 1, 2, 0xff, 0x100, 0x101, 0xffff, 0x10000, 0x10001) +
                listOf(-1, -2, -0xff, -0x100, -0x101, -0xffff, -0x10000, -0x10001)) {
            val bi = BigInteger.valueOf(n.toLong())
            val encoded = n.toLong().derEncodeToByteArray()
            assertEquals(bi.toByteArray().toHex(), encoded.toHex())
            val decodedN = encoded.derDecodeAsLong()
            assertEquals(n.toLong(), decodedN)
        }
    }
}