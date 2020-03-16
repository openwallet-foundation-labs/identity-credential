/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.issuerauthority

import android.content.Context
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class MockIssuerAuthorityTest {
    lateinit var issuerAuthority : IIssuerAuthority
    lateinit var appContext : Context

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        issuerAuthority = MockIssuerAuthority.getInstance(appContext)
    }

    @Test
    fun getCredentialsTest() {
        val credential = issuerAuthority.getCredentials()

        Assert.assertNotNull(credential)
        Assert.assertNotNull(credential?.portraitOfHolder)
    }

    @Test
    fun getProvisionChallengeTest() {
        val provisionChallenge = issuerAuthority.getProvisionChallenge()

        Assert.assertNotNull(provisionChallenge)
        Assert.assertArrayEquals(
            MockIssuerAuthority.PROVISION_CHALLENGE.toByteArray(),
            provisionChallenge)
    }

    @Test
    fun getIssuerSignedDataTest() {
        runBlocking {
            // Create a public key
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC
            )
            val ecSpec =
                ECGenParameterSpec("prime256v1")
            kpg.initialize(ecSpec)
            val mEphemeralKeyPair = kpg.generateKeyPair()
            val publicKey = mEphemeralKeyPair.public

            val issuerAuth = issuerAuthority.getIssuerSignedData(publicKey)
            Assert.assertNotNull(issuerAuth)

            issuerAuth?.let {
                val coseSign1 = CoseSign1.Builder()
                    .decode(issuerAuth)
                    .build()

                Assert.assertNotNull(coseSign1)
                Assert.assertNotNull(coseSign1.payloadData)
                Assert.assertNotNull(coseSign1.dsCertificateBytes)
                Assert.assertNotNull(coseSign1.signature)
                Assert.assertNotNull(coseSign1.toBeSignedSigStructure)

                val issuerNamspaces = issuerAuthority.getIssuerNamespaces(it)
                Assert.assertNotNull(issuerNamspaces)

                issuerNamspaces?.let { ns->
                    Assert.assertNotNull(ns.nameSpaces)
                    Assert.assertTrue(ns.nameSpaces.isNotEmpty())
                }
            }
        }
    }
}