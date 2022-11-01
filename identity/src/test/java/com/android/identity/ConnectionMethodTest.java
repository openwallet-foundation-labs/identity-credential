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

package com.android.identity;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.util.OptionalLong;
import java.util.UUID;

public class ConnectionMethodTest {

    @Test
    @SmallTest
    public void testConnectionMethodNfc() {
        ConnectionMethodNfc cm = new ConnectionMethodNfc(4096, 32768);
        ConnectionMethodNfc decoded = ConnectionMethodNfc.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertEquals(cm.getCommandDataFieldMaxLength(), decoded.getCommandDataFieldMaxLength());
        Assert.assertEquals(cm.getResponseDataFieldMaxLength(), decoded.getResponseDataFieldMaxLength());
        Assert.assertEquals("[\n" +
                "  1,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : 4096,\n" +
                "    1 : 32768\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    @SmallTest
    public void testConnectionMethodBle() {
        UUID uuidPeripheral = new UUID(0, 1);
        UUID uuidCentral = new UUID(123456789, 987654321);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                true,
                true,
                uuidPeripheral,
                uuidCentral);
        ConnectionMethodBle decoded = ConnectionMethodBle.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertTrue(cm.getSupportsPeripheralServerMode());
        Assert.assertTrue(cm.getSupportsCentralClientMode());
        Assert.assertEquals(uuidPeripheral, cm.getPeripheralServerModeUuid());
        Assert.assertEquals(uuidCentral, cm.getCentralClientModeUuid());
        Assert.assertEquals("[\n" +
                "  2,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : true,\n" +
                "    1 : true,\n" +
                "    10 : [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01],\n" +
                "    11 : [0x00, 0x00, 0x00, 0x00, 0x07, 0x5b, 0xcd, 0x15, 0x00, 0x00, 0x00, 0x00, 0x3a, 0xde, 0x68, 0xb1]\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    @SmallTest
    public void testConnectionMethodBleOnlyCentralClient() {
        UUID uuid = new UUID(123456789, 987654321);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                false,
                true,
                null,
                uuid);
        ConnectionMethodBle decoded = ConnectionMethodBle.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertFalse(cm.getSupportsPeripheralServerMode());
        Assert.assertTrue(cm.getSupportsCentralClientMode());
        Assert.assertNull(cm.getPeripheralServerModeUuid());
        Assert.assertEquals(uuid, cm.getCentralClientModeUuid());
        Assert.assertEquals("[\n" +
                "  2,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : false,\n" +
                "    1 : true,\n" +
                "    11 : [0x00, 0x00, 0x00, 0x00, 0x07, 0x5b, 0xcd, 0x15, 0x00, 0x00, 0x00, 0x00, 0x3a, 0xde, 0x68, 0xb1]\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    @SmallTest
    public void testConnectionMethodBleOnlyPeripheralServer() {
        UUID uuid = new UUID(0, 1);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                true,
                false,
                uuid,
                null);
        ConnectionMethodBle decoded = ConnectionMethodBle.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertTrue(cm.getSupportsPeripheralServerMode());
        Assert.assertFalse(cm.getSupportsCentralClientMode());
        Assert.assertEquals(uuid, cm.getPeripheralServerModeUuid());
        Assert.assertNull(cm.getCentralClientModeUuid());
        Assert.assertEquals("[\n" +
                "  2,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : true,\n" +
                "    1 : false,\n" +
                "    10 : [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01]\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    @SmallTest
    public void testConnectionMethodWifiAware() {
        ConnectionMethodWifiAware cm = new ConnectionMethodWifiAware(
                "foobar",
                OptionalLong.of(42),
                OptionalLong.of(43),
                new byte[] {1, 2});
        ConnectionMethodWifiAware decoded = ConnectionMethodWifiAware.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertEquals("foobar", cm.getPassphraseInfoPassphrase());
        Assert.assertEquals(42, cm.getChannelInfoChannelNumber().getAsLong());
        Assert.assertEquals(43, cm.getChannelInfoOperatingClass().getAsLong());
        Assert.assertArrayEquals(new byte[] {1, 2}, cm.getBandInfoSupportedBands());
        Assert.assertEquals("[\n" +
                "  3,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : 'foobar',\n" +
                "    2 : 42,\n" +
                "    1 : 43,\n" +
                "    3 : [0x01, 0x02]\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    @SmallTest
    public void testConnectionMethodRestApi() {
        ConnectionMethodHttp cm = new ConnectionMethodHttp("https://www.example.com/mdocReader");
        ConnectionMethodHttp decoded = ConnectionMethodHttp.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertEquals(decoded.getUri(), cm.getUri());
        Assert.assertEquals("[\n" +
                "  4,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : 'https://www.example.com/mdocReader'\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }
}
