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
package com.android.identity.mdoc.engagement

import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp
import com.android.identity.mdoc.origininfo.OriginInfo
import com.android.identity.mdoc.origininfo.OriginInfoDomain
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security
import java.util.UUID

class EngagementGeneratorTest {
    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testNoConnectionMethodsOrOriginInfos() {
        val eSenderKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val eg = EngagementGenerator(
            eSenderKey.publicKey,
            EngagementGenerator.ENGAGEMENT_VERSION_1_0
        )
        val encodedEngagement = eg.generate()
        val parser = EngagementParser(encodedEngagement)
        val engagement = parser.parse()
        Assert.assertEquals(engagement.eSenderKey, eSenderKey.publicKey)
        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_0, engagement.version)
        Assert.assertEquals(0, engagement.connectionMethods.size.toLong())
        Assert.assertEquals(0, engagement.originInfos.size.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testWebsiteEngagement() {
        val eSenderKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val eg = EngagementGenerator(
            eSenderKey.publicKey,
            EngagementGenerator.ENGAGEMENT_VERSION_1_1
        )
        val connectionMethods = mutableListOf<ConnectionMethod>()
        connectionMethods.add(ConnectionMethodHttp("http://www.example.com/verifier/123"))
        eg.addConnectionMethods(connectionMethods)
        val originInfos = mutableListOf<OriginInfo>()
        originInfos.add(OriginInfoDomain("http://www.example.com/verifier"))
        eg.addOriginInfos(originInfos)
        val encodedEngagement = eg.generate()
        val parser = EngagementParser(encodedEngagement)
        val engagement = parser.parse()
        Assert.assertEquals(engagement.eSenderKey, eSenderKey.publicKey)
        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_1, engagement.version)
        Assert.assertEquals(1, engagement.connectionMethods.size.toLong())
        val cm = engagement.connectionMethods[0] as ConnectionMethodHttp
        Assert.assertEquals("http://www.example.com/verifier/123", cm.uri)
        Assert.assertEquals(1, engagement.originInfos.size.toLong())
        val oi = engagement.originInfos[0] as OriginInfoDomain
        Assert.assertEquals("http://www.example.com/verifier", oi.url)
    }

    @Test
    @Throws(Exception::class)
    fun testDeviceEngagementQrBleCentralClientMode() {
        val eSenderKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val uuid = UUID.randomUUID()
        val eg = EngagementGenerator(
            eSenderKey.publicKey,
            EngagementGenerator.ENGAGEMENT_VERSION_1_0
        )
        val connectionMethods = mutableListOf<ConnectionMethod>()
        connectionMethods.add(
            ConnectionMethodBle(
                false,
                true,
                null,
                uuid
            )
        )
        eg.addConnectionMethods(connectionMethods)
        val encodedEngagement = eg.generate()
        val parser = EngagementParser(encodedEngagement)
        val engagement = parser.parse()
        Assert.assertEquals(engagement.eSenderKey, eSenderKey.publicKey)
        Assert.assertEquals(EngagementGenerator.ENGAGEMENT_VERSION_1_0, engagement.version)
        Assert.assertEquals(1, engagement.connectionMethods.size.toLong())
        val cm = engagement.connectionMethods[0] as ConnectionMethodBle
        Assert.assertFalse(cm.supportsPeripheralServerMode)
        Assert.assertTrue(cm.supportsCentralClientMode)
        Assert.assertNull(cm.peripheralServerModeUuid)
        Assert.assertEquals(uuid, cm.centralClientModeUuid)
        Assert.assertEquals(0, engagement.originInfos.size.toLong())
    }
}
