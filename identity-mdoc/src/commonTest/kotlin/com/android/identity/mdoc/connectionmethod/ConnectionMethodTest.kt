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
package com.android.identity.mdoc.connectionmethod

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.mdoc.transport.MdocTransport
import com.android.identity.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionMethodTest {
    @Test
    fun testConnectionMethodNfc() {
        val cm = ConnectionMethodNfc(4096, 32768)
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodNfc?
        assertNotNull(decoded)
        assertEquals(decoded.commandDataFieldMaxLength, decoded.commandDataFieldMaxLength)
        assertEquals(decoded.responseDataFieldMaxLength, decoded.responseDataFieldMaxLength)
        assertEquals(
            """[
  1,
  1,
  {
    0: 4096,
    1: 32768
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
        val ndefRecord = cm.toNdefRecord(listOf(), MdocTransport.Role.MDOC, false)!!.first
        val decodedNfc = ConnectionMethodNfc.fromNdefRecord(ndefRecord, MdocTransport.Role.MDOC)!!
        assertEquals(cm, decodedNfc)
    }

    @Test
    fun testConnectionMethodBle() {
        val uuidPeripheral = UUID(0U, 1U)
        val uuidCentral = UUID(123456789U, 987654321U)
        var cm = ConnectionMethodBle(
            true,
            true,
            uuidPeripheral,
            uuidCentral
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        assertNotNull(decoded)
        assertTrue(decoded.supportsPeripheralServerMode)
        assertTrue(decoded.supportsCentralClientMode)
        assertEquals(uuidPeripheral, decoded.peripheralServerModeUuid)
        assertEquals(uuidCentral, decoded.centralClientModeUuid)
        assertEquals(
            """[
  2,
  1,
  {
    0: true,
    1: true,
    10: h'00000000000000000000000000000001',
    11: h'00000000075bcd15000000003ade68b1'
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )

        // For use in NFC, the UUIDs have to be the same
        val uuidBoth = UUID(0U, 2U)
        cm = ConnectionMethodBle(
            true,
            true,
            uuidBoth,
            uuidBoth
        )
        val ndefRecord = cm.toNdefRecord(listOf(), MdocTransport.Role.MDOC, false).first
        val decodedNfc = ConnectionMethodBle.fromNdefRecord(ndefRecord, MdocTransport.Role.MDOC, null)!!
        assertEquals(cm, decodedNfc)
    }

    @Test
    fun testConnectionMethodBleOnlyCentralClient() {
        val uuid = UUID(123456789U, 987654321U)
        val cm = ConnectionMethodBle(
            false,
            true,
            null,
            uuid
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        assertNotNull(decoded)
        assertFalse(decoded.supportsPeripheralServerMode)
        assertTrue(decoded.supportsCentralClientMode)
        assertNull(decoded.peripheralServerModeUuid)
        assertEquals(uuid, decoded.centralClientModeUuid)
        assertEquals(
            """[
  2,
  1,
  {
    0: false,
    1: true,
    11: h'00000000075bcd15000000003ade68b1'
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )

        val ndefRecord = cm.toNdefRecord(listOf(), MdocTransport.Role.MDOC, false).first
        val decodedNfc = ConnectionMethodBle.fromNdefRecord(ndefRecord, MdocTransport.Role.MDOC, null)!!
        assertEquals(cm, decodedNfc)
    }

    @Test
    fun testConnectionMethodBleOnlyPeripheralServer() {
        val uuid = UUID(0U, 1U)
        val cm = ConnectionMethodBle(
            true,
            false,
            uuid,
            null
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        assertNotNull(decoded)
        assertTrue(decoded.supportsPeripheralServerMode)
        assertFalse(decoded.supportsCentralClientMode)
        assertEquals(uuid, decoded.peripheralServerModeUuid)
        assertNull(decoded.centralClientModeUuid)
        assertEquals(
            """[
  2,
  1,
  {
    0: true,
    1: false,
    10: h'00000000000000000000000000000001'
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )

        val ndefRecord = cm.toNdefRecord(listOf(), MdocTransport.Role.MDOC, false).first
        val decodedNfc = ConnectionMethodBle.fromNdefRecord(ndefRecord, MdocTransport.Role.MDOC, null)!!
        assertEquals(cm, decodedNfc)
    }

    @Test
    fun testConnectionMethodWifiAware() {
        val cm = ConnectionMethodWifiAware(
            "foobar",
            42,
            43,
            byteArrayOf(1, 2)
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodWifiAware?
        assertNotNull(decoded)
        assertEquals("foobar", decoded!!.passphraseInfoPassphrase)
        assertEquals(42, decoded.channelInfoChannelNumber)
        assertEquals(43, decoded.channelInfoOperatingClass)
        assertContentEquals(byteArrayOf(1, 2), decoded.bandInfoSupportedBands)
        assertEquals(
            """[
  3,
  1,
  {
    0: "foobar",
    2: 42,
    1: 43,
    3: h'0102'
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }

    @Test
    fun testConnectionMethodRestApi() {
        val cm = ConnectionMethodHttp("https://www.example.com/mdocReader")
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodHttp?
        assertNotNull(decoded)
        assertEquals(decoded.uri, cm.uri)
        assertEquals(
            """[
  4,
  1,
  {
    0: "https://www.example.com/mdocReader"
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }

    @Test
    fun testConnectionMethodDisambiguate() {
        var ble: ConnectionMethodBle
        val disambiguated = ConnectionMethod.disambiguate(
            listOf(
                ConnectionMethodHttp("https://www.example.com/mdocReader"),
                ConnectionMethodBle(
                    true,
                    true,
                    UUID(0U, 1U),
                    UUID(0U, 2U)
                )
            )
        )
        assertEquals(3, disambiguated.size.toLong())
        assertTrue(disambiguated[0] is ConnectionMethodHttp)
        assertTrue(disambiguated[1] is ConnectionMethodBle)
        assertTrue(disambiguated[2] is ConnectionMethodBle)
        ble = disambiguated[1] as ConnectionMethodBle
        assertFalse(ble.supportsPeripheralServerMode)
        assertTrue(ble.supportsCentralClientMode)
        assertNull(ble.peripheralServerModeUuid)
        assertEquals(UUID(0U, 2U), ble.centralClientModeUuid)
        ble = disambiguated[2] as ConnectionMethodBle
        assertTrue(ble.supportsPeripheralServerMode)
        assertFalse(ble.supportsCentralClientMode)
        assertEquals(UUID(0U, 1U), ble.peripheralServerModeUuid)
        assertNull(ble.centralClientModeUuid)
    }

    @Test
    fun testConnectionMethodCombineUuidNotSame() {
        val disambiguated = listOf(
            ConnectionMethodHttp("https://www.example.com/mdocReader"),
            ConnectionMethodBle(
                true,
                false,
                UUID(0U, 1U),
                null
            ),
            ConnectionMethodBle(
                false,
                true,
                null,
                UUID(0U, 2U)
            )
        )
        assertFailsWith<IllegalArgumentException> {
            val combined = ConnectionMethod.combine(disambiguated)
        }
    }

    @Test
    fun testConnectionMethodCombineUuid() {
        val uuid = UUID(0U, 3U)
        val disambiguated = listOf(
            ConnectionMethodHttp("https://www.example.com/mdocReader"),
            ConnectionMethodBle(
                true,
                false,
                uuid,
                null
            ),
            ConnectionMethodBle(
                false,
                true,
                null,
                uuid
            )
        )
        val combined = ConnectionMethod.combine(disambiguated)
        assertEquals(2, combined.size.toLong())
        val ble = combined[0] as ConnectionMethodBle
        assertTrue(ble.supportsPeripheralServerMode)
        assertTrue(ble.supportsCentralClientMode)
        assertEquals(uuid, ble.peripheralServerModeUuid)
        assertEquals(uuid, ble.centralClientModeUuid)
    }
}