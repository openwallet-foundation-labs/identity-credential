package org.multipaz.util

import kotlin.test.Test
import kotlin.test.assertEquals

class UUIDTest {

    @Test
    fun toFromString() {
        val uuidHigh = 0x123456789abcdef0UL
        val uuidLow = 0x0fedbca987654321UL
        assertEquals("00000000-0000-0000-0000-000000000000", UUID(0UL, 0UL).toString())
        assertEquals("12345678-9abc-def0-0fed-bca987654321", UUID(uuidHigh, uuidLow).toString())

        UUID.fromString("00000000-0000-0000-0000-000000000000").let {
            assertEquals(it.mostSignificantBits, 0UL)
            assertEquals(it.leastSignificantBits, 0UL)
        }

        UUID.fromString("12345678-9abc-def0-0fed-bca987654321").let {
            assertEquals(it.mostSignificantBits, uuidHigh)
            assertEquals(it.leastSignificantBits, uuidLow)
        }
    }
}
