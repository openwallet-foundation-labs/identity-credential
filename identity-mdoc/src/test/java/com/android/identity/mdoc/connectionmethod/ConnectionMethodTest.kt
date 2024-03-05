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
import org.junit.Assert
import org.junit.Test
import java.util.Arrays
import java.util.OptionalLong
import java.util.UUID

class ConnectionMethodTest {
    @Test
    fun testConnectionMethodNfc() {
        val cm = ConnectionMethodNfc(4096, 32768)
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodNfc?
        Assert.assertNotNull(decoded)
        Assert.assertEquals(decoded!!.commandDataFieldMaxLength, decoded.commandDataFieldMaxLength)
        Assert.assertEquals(decoded.responseDataFieldMaxLength, decoded.responseDataFieldMaxLength)
        Assert.assertEquals(
            """[
  1,
  1,
  {
    0: 4096,
    1: 32768
  }
]""", Cbor.toDiagnostics(cm.toDeviceEngagement(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }

    @Test
    fun testConnectionMethodBle() {
        val uuidPeripheral = UUID(0, 1)
        val uuidCentral = UUID(123456789, 987654321)
        var cm = ConnectionMethodBle(
            true,
            true,
            uuidPeripheral,
            uuidCentral
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        Assert.assertNotNull(decoded)
        Assert.assertTrue(decoded!!.supportsPeripheralServerMode)
        Assert.assertTrue(decoded.supportsCentralClientMode)
        Assert.assertEquals(uuidPeripheral, decoded.peripheralServerModeUuid)
        Assert.assertEquals(uuidCentral, decoded.centralClientModeUuid)
        Assert.assertEquals(
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
        val uuidBoth = UUID(0, 2)
        cm = ConnectionMethodBle(
            true,
            true,
            uuidBoth,
            uuidBoth
        )
    }

    @Test
    fun testConnectionMethodBleOnlyCentralClient() {
        val uuid = UUID(123456789, 987654321)
        val cm = ConnectionMethodBle(
            false,
            true,
            null,
            uuid
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        Assert.assertNotNull(decoded)
        Assert.assertFalse(decoded!!.supportsPeripheralServerMode)
        Assert.assertTrue(decoded.supportsCentralClientMode)
        Assert.assertNull(decoded.peripheralServerModeUuid)
        Assert.assertEquals(uuid, decoded.centralClientModeUuid)
        Assert.assertEquals(
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
    }

    @Test
    fun testConnectionMethodBleOnlyPeripheralServer() {
        val uuid = UUID(0, 1)
        val cm = ConnectionMethodBle(
            true,
            false,
            uuid,
            null
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodBle?
        Assert.assertNotNull(decoded)
        Assert.assertTrue(decoded!!.supportsPeripheralServerMode)
        Assert.assertFalse(decoded.supportsCentralClientMode)
        Assert.assertEquals(uuid, decoded.peripheralServerModeUuid)
        Assert.assertNull(decoded.centralClientModeUuid)
        Assert.assertEquals(
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
    }

    @Test
    fun testConnectionMethodWifiAware() {
        val cm = ConnectionMethodWifiAware(
            "foobar",
            OptionalLong.of(42),
            OptionalLong.of(43), byteArrayOf(1, 2)
        )
        val decoded = ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement()) as ConnectionMethodWifiAware?
        Assert.assertNotNull(decoded)
        Assert.assertEquals("foobar", decoded!!.passphraseInfoPassphrase)
        Assert.assertEquals(42, decoded.channelInfoChannelNumber.asLong)
        Assert.assertEquals(43, decoded.channelInfoOperatingClass.asLong)
        Assert.assertArrayEquals(byteArrayOf(1, 2), decoded.bandInfoSupportedBands)
        Assert.assertEquals(
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
        Assert.assertNotNull(decoded)
        Assert.assertEquals(decoded!!.uri, cm.uri)
        Assert.assertEquals(
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
            Arrays.asList(
                ConnectionMethodHttp("https://www.example.com/mdocReader"),
                ConnectionMethodBle(
                    true,
                    true,
                    UUID(0, 1),
                    UUID(0, 2)
                )
            )
        )
        Assert.assertEquals(3, disambiguated.size.toLong())
        Assert.assertTrue(disambiguated[0] is ConnectionMethodHttp)
        Assert.assertTrue(disambiguated[1] is ConnectionMethodBle)
        Assert.assertTrue(disambiguated[2] is ConnectionMethodBle)
        ble = disambiguated[1] as ConnectionMethodBle
        Assert.assertFalse(ble.supportsPeripheralServerMode)
        Assert.assertTrue(ble.supportsCentralClientMode)
        Assert.assertNull(ble.peripheralServerModeUuid)
        Assert.assertEquals(UUID(0, 2), ble.centralClientModeUuid)
        ble = disambiguated[2] as ConnectionMethodBle
        Assert.assertTrue(ble.supportsPeripheralServerMode)
        Assert.assertFalse(ble.supportsCentralClientMode)
        Assert.assertEquals(UUID(0, 1), ble.peripheralServerModeUuid)
        Assert.assertNull(ble.centralClientModeUuid)
    }

    @Test
    fun testConnectionMethodCombineUuidNotSame() {
        val disambiguated = Arrays.asList(
            ConnectionMethodHttp("https://www.example.com/mdocReader"),
            ConnectionMethodBle(
                true,
                false,
                UUID(0, 1),
                null
            ),
            ConnectionMethodBle(
                false,
                true,
                null,
                UUID(0, 2)
            )
        )
        try {
            val combined = ConnectionMethod.combine(disambiguated)
            Assert.fail()
        } catch (e: IllegalArgumentException) {
            // This is the expected path
        }
    }

    @Test
    fun testConnectionMethodCombineUuid() {
        val uuid = UUID(0, 3)
        val disambiguated = Arrays.asList(
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
        Assert.assertEquals(2, combined.size.toLong())
        val ble = combined[1] as ConnectionMethodBle
        Assert.assertTrue(ble.supportsPeripheralServerMode)
        Assert.assertTrue(ble.supportsCentralClientMode)
        Assert.assertEquals(uuid, ble.peripheralServerModeUuid)
        Assert.assertEquals(uuid, ble.centralClientModeUuid)
    }
}