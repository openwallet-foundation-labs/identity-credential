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

package com.android.identity.mdoc.connectionmethod;

import com.android.identity.internal.Util;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodWifiAware;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

public class ConnectionMethodTest {

    @Test
    public void testConnectionMethodNfc() {
        ConnectionMethodNfc cm = new ConnectionMethodNfc(4096, 32768);
        ConnectionMethodNfc decoded = (ConnectionMethodNfc) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertEquals(decoded.getCommandDataFieldMaxLength(), decoded.getCommandDataFieldMaxLength());
        Assert.assertEquals(decoded.getResponseDataFieldMaxLength(), decoded.getResponseDataFieldMaxLength());
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
    public void testConnectionMethodBle() {
        UUID uuidPeripheral = new UUID(0, 1);
        UUID uuidCentral = new UUID(123456789, 987654321);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                true,
                true,
                uuidPeripheral,
                uuidCentral);
        ConnectionMethodBle decoded = (ConnectionMethodBle) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertTrue(decoded.getSupportsPeripheralServerMode());
        Assert.assertTrue(decoded.getSupportsCentralClientMode());
        Assert.assertEquals(uuidPeripheral, decoded.getPeripheralServerModeUuid());
        Assert.assertEquals(uuidCentral, decoded.getCentralClientModeUuid());
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

        // For use in NFC, the UUIDs have to be the same
        UUID uuidBoth = new UUID(0, 2);
        cm = new ConnectionMethodBle(
                true,
                true,
                uuidBoth,
                uuidBoth);
    }

    @Test
    public void testConnectionMethodBleOnlyCentralClient() {
        UUID uuid = new UUID(123456789, 987654321);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                false,
                true,
                null,
                uuid);
        ConnectionMethodBle decoded = (ConnectionMethodBle) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertFalse(decoded.getSupportsPeripheralServerMode());
        Assert.assertTrue(decoded.getSupportsCentralClientMode());
        Assert.assertNull(decoded.getPeripheralServerModeUuid());
        Assert.assertEquals(uuid, decoded.getCentralClientModeUuid());
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
    public void testConnectionMethodBleOnlyPeripheralServer() {
        UUID uuid = new UUID(0, 1);
        ConnectionMethodBle cm = new ConnectionMethodBle(
                true,
                false,
                uuid,
                null);
        ConnectionMethodBle decoded = (ConnectionMethodBle) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertTrue(decoded.getSupportsPeripheralServerMode());
        Assert.assertFalse(decoded.getSupportsCentralClientMode());
        Assert.assertEquals(uuid, decoded.getPeripheralServerModeUuid());
        Assert.assertNull(decoded.getCentralClientModeUuid());
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
    public void testConnectionMethodWifiAware() {
        ConnectionMethodWifiAware cm = new ConnectionMethodWifiAware(
                "foobar",
                OptionalLong.of(42),
                OptionalLong.of(43),
                new byte[]{1, 2});
        ConnectionMethodWifiAware decoded = (ConnectionMethodWifiAware) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertEquals("foobar", decoded.getPassphraseInfoPassphrase());
        Assert.assertEquals(42, decoded.getChannelInfoChannelNumber().getAsLong());
        Assert.assertEquals(43, decoded.getChannelInfoOperatingClass().getAsLong());
        Assert.assertArrayEquals(new byte[]{1, 2}, decoded.getBandInfoSupportedBands());
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
    public void testConnectionMethodRestApi() {
        ConnectionMethodHttp cm = new ConnectionMethodHttp("https://www.example.com/mdocReader");
        ConnectionMethodHttp decoded = (ConnectionMethodHttp) ConnectionMethod.fromDeviceEngagement(cm.toDeviceEngagement());
        Assert.assertNotNull(decoded);
        Assert.assertEquals(decoded.getUri(), cm.getUri());
        Assert.assertEquals("[\n" +
                "  4,\n" +
                "  1,\n" +
                "  {\n" +
                "    0 : 'https://www.example.com/mdocReader'\n" +
                "  }\n" +
                "]", Util.cborPrettyPrint(cm.toDeviceEngagement()));
    }

    @Test
    public void testConnectionMethodDisambiguate() {
        ConnectionMethodBle ble;

        List<ConnectionMethod> disambiguated =
                ConnectionMethod.disambiguate(Arrays.asList(
                        new ConnectionMethodHttp("https://www.example.com/mdocReader"),
                        new ConnectionMethodBle(true,
                                true,
                                new UUID(0, 1),
                                new UUID(0, 2))));
        Assert.assertEquals(3, disambiguated.size());
        Assert.assertTrue(disambiguated.get(0) instanceof ConnectionMethodHttp);
        Assert.assertTrue(disambiguated.get(1) instanceof ConnectionMethodBle);
        Assert.assertTrue(disambiguated.get(2) instanceof ConnectionMethodBle);

        ble = (ConnectionMethodBle) disambiguated.get(1);
        Assert.assertFalse(ble.getSupportsPeripheralServerMode());
        Assert.assertTrue(ble.getSupportsCentralClientMode());
        Assert.assertNull(ble.getPeripheralServerModeUuid());
        Assert.assertEquals(new UUID(0, 2), ble.getCentralClientModeUuid());

        ble = (ConnectionMethodBle) disambiguated.get(2);
        Assert.assertTrue(ble.getSupportsPeripheralServerMode());
        Assert.assertFalse(ble.getSupportsCentralClientMode());
        Assert.assertEquals(new UUID(0, 1), ble.getPeripheralServerModeUuid());
        Assert.assertNull(ble.getCentralClientModeUuid());
    }

    @Test
    public void testConnectionMethodCombineUuidNotSame() {
        List<ConnectionMethod> disambiguated = Arrays.asList(
                new ConnectionMethodHttp("https://www.example.com/mdocReader"),
                new ConnectionMethodBle(true,
                        false,
                        new UUID(0, 1),
                        null),
                new ConnectionMethodBle(false,
                        true,
                        null,
                        new UUID(0, 2)));
        try {
            List<ConnectionMethod> combined = ConnectionMethod.combine(disambiguated);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            // This is the expected path
        }
    }

    @Test
    public void testConnectionMethodCombineUuid() {
        UUID uuid = new UUID(0, 3);
        List<ConnectionMethod> disambiguated = Arrays.asList(
                new ConnectionMethodHttp("https://www.example.com/mdocReader"),
                new ConnectionMethodBle(true,
                        false,
                        uuid,
                        null),
                new ConnectionMethodBle(false,
                        true,
                        null,
                        uuid));
        List<ConnectionMethod> combined = ConnectionMethod.combine(disambiguated);
        Assert.assertEquals(2, combined.size());
        ConnectionMethodBle ble = (ConnectionMethodBle) combined.get(1);

        Assert.assertTrue(ble.getSupportsPeripheralServerMode());
        Assert.assertTrue(ble.getSupportsCentralClientMode());
        Assert.assertEquals(uuid, ble.getPeripheralServerModeUuid());
        Assert.assertEquals(uuid, ble.getCentralClientModeUuid());
    }

}