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

import java.util.List;

public class EngagementParserTest {
    @Test
    @SmallTest
    public void testDeviceRequestEngagementWithVectors() {
        byte[] deviceEngagement = Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_ENGAGEMENT);
        EngagementParser parser = new EngagementParser(deviceEngagement);
        EngagementParser.Engagement engagement = parser.parse();
        Assert.assertEquals("1.0", engagement.getVersion());

        List<ConnectionMethod> connectionMethods = engagement.getConnectionMethods();
        Assert.assertEquals(1, connectionMethods.size());
        Assert.assertTrue(connectionMethods.get(0) instanceof ConnectionMethodBle);
        ConnectionMethodBle cmBle = (ConnectionMethodBle) connectionMethods.get(0);
        Assert.assertFalse(cmBle.getSupportsPeripheralServerMode());
        Assert.assertTrue(cmBle.getSupportsCentralClientMode());
        Assert.assertNull(cmBle.getPeripheralServerModeUuid());
        Assert.assertEquals("45efef74-2b2c-4837-a9a3-b0e1d05a6917",
                cmBle.getCentralClientModeUuid().toString());

        byte[] eDeviceKeyBytes = engagement.getESenderKeyBytes();
        Assert.assertEquals(TestVectors.ISO_18013_5_ANNEX_D_E_DEVICE_KEY_BYTES, Util.toHex(eDeviceKeyBytes));
    }
}
