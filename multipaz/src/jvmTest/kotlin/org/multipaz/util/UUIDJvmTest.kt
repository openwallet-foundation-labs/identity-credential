package org.multipaz.util

import kotlin.test.Test
import kotlin.test.assertEquals

class UUIDJvmTest {
    @Test
    fun testToFrom() {
        val uuid = UUID.randomUUID()
        val javaUuid: java.util.UUID = uuid.toJavaUuid()
        assertEquals(uuid.toString().lowercase(), javaUuid.toString().lowercase())
        assertEquals(UUID.fromJavaUuid(javaUuid), uuid)
    }
}