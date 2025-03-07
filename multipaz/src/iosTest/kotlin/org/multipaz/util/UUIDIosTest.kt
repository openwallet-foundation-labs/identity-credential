package org.multipaz.util

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSUUID

class UUIDIosTest {
    @Test
    fun testToFrom() {
        val uuid = UUID.randomUUID()
        val nsUuid: NSUUID = uuid.toNSUUID()
        assertEquals(uuid.toString().lowercase(), nsUuid.toString().lowercase())
        assertEquals(UUID.fromNSUUID(nsUuid), uuid)
    }
}