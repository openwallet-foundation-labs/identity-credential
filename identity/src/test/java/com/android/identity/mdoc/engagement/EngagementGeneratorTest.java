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

package com.android.identity.mdoc.engagement;

import static com.android.identity.mdoc.engagement.EngagementGenerator.ENGAGEMENT_VERSION_1_0;
import static com.android.identity.mdoc.engagement.EngagementGenerator.ENGAGEMENT_VERSION_1_1;

import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.origininfo.OriginInfo;
import com.android.identity.mdoc.origininfo.OriginInfoDomain;
import com.android.identity.securearea.SecureArea;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EngagementGeneratorTest {

    @Before
    public void setup() {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    @Test
    public void testNoConnectionMethodsOrOriginInfos() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                SecureArea.EC_CURVE_P256,
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
    public void testWebsiteEngagement() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                SecureArea.EC_CURVE_P256,
                ENGAGEMENT_VERSION_1_1);
        List<ConnectionMethod> connectionMethods = new ArrayList<>();
        connectionMethods.add(new ConnectionMethodHttp("http://www.example.com/verifier/123"));
        eg.setConnectionMethods(connectionMethods);
        List<OriginInfo> originInfos = new ArrayList<>();
        originInfos.add(new OriginInfoDomain("http://www.example.com/verifier"));
        eg.setOriginInfos(originInfos);
        byte[] encodedEngagement = eg.generate();

        EngagementParser parser = new EngagementParser(encodedEngagement);
        EngagementParser.Engagement engagement = parser.parse();

        Assert.assertEquals(engagement.getESenderKey(), eSenderKey.getPublic());
        Assert.assertEquals(ENGAGEMENT_VERSION_1_1, engagement.getVersion());
        Assert.assertEquals(1, engagement.getConnectionMethods().size());
        ConnectionMethodHttp cm = (ConnectionMethodHttp) engagement.getConnectionMethods().get(0);
        Assert.assertEquals("http://www.example.com/verifier/123", cm.getUri());
        Assert.assertEquals(1, engagement.getOriginInfos().size());
        OriginInfoDomain oi = (OriginInfoDomain) engagement.getOriginInfos().get(0);
        Assert.assertEquals("http://www.example.com/verifier", oi.getUrl());
    }

    @Test
    public void testDeviceEngagementQrBleCentralClientMode() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair eSenderKey = kpg.generateKeyPair();

        UUID uuid = UUID.randomUUID();
        EngagementGenerator eg = new EngagementGenerator(eSenderKey.getPublic(),
                SecureArea.EC_CURVE_P256,
                ENGAGEMENT_VERSION_1_0);
        List<ConnectionMethod> connectionMethods = new ArrayList<>();
        connectionMethods.add(new ConnectionMethodBle(
                false,
                true,
                null,
                uuid));
        eg.setConnectionMethods(connectionMethods);
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
