package org.multipaz.securearea

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyPurposeTest {
    @Test
    fun keyPurposeEncode() {
        assertEquals(0, KeyPurpose.encodeSet(setOf()))
        assertEquals(1, KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)))
        assertEquals(2, KeyPurpose.encodeSet(setOf(KeyPurpose.AGREE_KEY)))
        assertEquals(3, KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)))
    }

    @Test
    fun keyPurposeDecode() {
        assertEquals(KeyPurpose.decodeSet(0), setOf<KeyPurpose>())
        assertEquals(KeyPurpose.decodeSet(1), setOf(KeyPurpose.SIGN))
        assertEquals(KeyPurpose.decodeSet(2), setOf(KeyPurpose.AGREE_KEY))
        assertEquals(KeyPurpose.decodeSet(3), setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY))
    }
}