/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multipaz.mdoc.engagement

import org.multipaz.mdoc.TestVectors
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.util.fromHex
import org.multipaz.util.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngagementParserTest {
    @Test
    fun testDeviceRequestEngagementWithVectors() {
        val deviceEngagement = TestVectors.ISO_18013_5_ANNEX_D_DEVICE_ENGAGEMENT.fromHex()
        val parser = EngagementParser(deviceEngagement)
        val engagement = parser.parse()
        assertEquals("1.0", engagement.version)
        val connectionMethods = engagement.connectionMethods
        assertEquals(1, connectionMethods.size.toLong())
        assertTrue(connectionMethods[0] is MdocConnectionMethodBle)
        val cmBle = connectionMethods[0] as MdocConnectionMethodBle
        assertFalse(cmBle.supportsPeripheralServerMode)
        assertTrue(cmBle.supportsCentralClientMode)
        assertNull(cmBle.peripheralServerModeUuid)
        assertEquals(
            "45efef74-2b2c-4837-a9a3-b0e1d05a6917",
            cmBle.centralClientModeUuid.toString()
        )
        val eDeviceKeyBytes = engagement.eSenderKeyBytes
        assertEquals(
            TestVectors.ISO_18013_5_ANNEX_D_E_DEVICE_KEY_BYTES,
            eDeviceKeyBytes.toHex()
        )
    }
}
