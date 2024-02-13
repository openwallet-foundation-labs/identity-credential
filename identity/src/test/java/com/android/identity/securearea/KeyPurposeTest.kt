package com.android.identity.securearea

import org.junit.Assert
import org.junit.Test

class KeyPurposeTest {
    @Test
    fun keyPurposeEncode() {
        Assert.assertEquals(0, KeyPurpose.encodeSet(setOf()))
        Assert.assertEquals(1, KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)))
        Assert.assertEquals(2, KeyPurpose.encodeSet(setOf(KeyPurpose.AGREE_KEY)))
        Assert.assertEquals(3, KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)))
    }

    @Test
    fun keyPurposeDecode() {
        Assert.assertEquals(KeyPurpose.decodeSet(0), setOf<KeyPurpose>())
        Assert.assertEquals(KeyPurpose.decodeSet(1), setOf(KeyPurpose.SIGN))
        Assert.assertEquals(KeyPurpose.decodeSet(2), setOf(KeyPurpose.AGREE_KEY))
        Assert.assertEquals(KeyPurpose.decodeSet(3), setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY))
    }
}