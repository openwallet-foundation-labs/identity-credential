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

import static com.android.identity.EngagementGenerator.ENGAGEMENT_VERSION_1_0;
import static com.android.identity.EngagementGenerator.ENGAGEMENT_VERSION_1_1;

import android.security.keystore.KeyProperties;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

public class EngagementGeneratorTest {

    @Test
    @SmallTest
    public void testNoConnectionMethodsOrOriginInfos() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                ENGAGEMENT_VERSION_1_0);
        byte[] encodedEngagement = eg.generate();

        EngagementParser parser = new EngagementParser(encodedEngagement);
        EngagementParser.Engagement engagement = parser.parse();

        Assert.assertEquals(engagement.getESenderKey(), eSenderKey.getPublic());
        Assert.assertEquals(ENGAGEMENT_VERSION_1_0, engagement.getVersion());
        Assert.assertEquals(0, engagement.getConnectionMethods().size());
        Assert.assertEquals(0, engagement.getOriginInfos().size());
    }

    @Test
    @SmallTest
    public void testWebsiteEngagement() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                ENGAGEMENT_VERSION_1_1);
        eg.addConnectionMethod(new ConnectionMethodHttp("http://www.example.com/verifier/123"));
        eg.addOriginInfo(new OriginInfoWebsite(OriginInfo.CAT_DELIVERY, "http://www.example.com/verifier"));
        byte[] encodedEngagement = eg.generate();

        EngagementParser parser = new EngagementParser(encodedEngagement);
        EngagementParser.Engagement engagement = parser.parse();

        Assert.assertEquals(engagement.getESenderKey(), eSenderKey.getPublic());
        Assert.assertEquals(ENGAGEMENT_VERSION_1_1, engagement.getVersion());
        Assert.assertEquals(1, engagement.getConnectionMethods().size());
        ConnectionMethodHttp cm = (ConnectionMethodHttp) engagement.getConnectionMethods().get(0);
        Assert.assertEquals("http://www.example.com/verifier/123", cm.getUri());
        Assert.assertEquals(1, engagement.getOriginInfos().size());
        OriginInfoWebsite oi = (OriginInfoWebsite) engagement.getOriginInfos().get(0);
        Assert.assertEquals("http://www.example.com/verifier", oi.getBaseUrl());
    }

    @Test
    @SmallTest
    public void testDeviceEngagementQrBleCentralClientMode() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        UUID uuid = UUID.randomUUID();
        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                ENGAGEMENT_VERSION_1_0);
        eg.addConnectionMethod(new ConnectionMethodBle(
                false,
                true,
                null,
                uuid));
        byte[] encodedEngagement = eg.generate();

        EngagementParser parser = new EngagementParser(encodedEngagement);
        EngagementParser.Engagement engagement = parser.parse();

        Assert.assertEquals(engagement.getESenderKey(), eSenderKey.getPublic());
        Assert.assertEquals(ENGAGEMENT_VERSION_1_0, engagement.getVersion());

        Assert.assertEquals(1, engagement.getConnectionMethods().size());
        ConnectionMethodBle cm = (ConnectionMethodBle) engagement.getConnectionMethods().get(0);
        Assert.assertFalse(cm.getSupportsPeripheralServerMode());
        Assert.assertTrue(cm.getSupportsCentralClientMode());
        Assert.assertNull(cm.getPeripheralServerModeUuid());
        Assert.assertEquals(uuid, cm.getCentralClientModeUuid());

        Assert.assertEquals(0, engagement.getOriginInfos().size());
    }
}
