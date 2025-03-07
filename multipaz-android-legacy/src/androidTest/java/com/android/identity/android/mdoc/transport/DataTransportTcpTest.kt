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
package com.android.identity.android.mdoc.transport

import android.os.ConditionVariable
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.multipaz.util.fromHex
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DataTransportTcpTest {
    @Test
    @SmallTest
    fun connectAndListen() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val verifier = DataTransportTcp(
            appContext,
            DataTransport.Role.MDOC_READER,
            DataTransportOptions.Builder().build()
        )
        val prover = DataTransportTcp(
            appContext,
            DataTransport.Role.MDOC,
            DataTransportOptions.Builder().build()
        )
        val messageSentByVerifier = "010203".fromHex()
        val messageSentByProver = "0405".fromHex()
        val messageReceivedByProver = arrayOf<ByteArray?>(null)
        val messageReceivedByVerifier = arrayOf<ByteArray?>(null)
        val proverMessageReceivedCondVar = ConditionVariable()
        val proverPeerConnectedCondVar = ConditionVariable()
        val proverPeerDisconnectedCondVar = ConditionVariable()
        val verifierMessageReceivedCondVar = ConditionVariable()
        val verifierPeerConnectedCondVar = ConditionVariable()
        val executor: Executor = Executors.newSingleThreadExecutor()
        prover.setListener(object : DataTransport.Listener {
            override fun onConnecting() {}
            override fun onConnected() {
                proverPeerConnectedCondVar.open()
            }

            override fun onDisconnected() {
                proverPeerDisconnectedCondVar.open()
            }

            override fun onTransportSpecificSessionTermination() {
                Assert.fail()
            }

            override fun onError(error: Throwable) {
                throw AssertionError(error)
            }

            override fun onMessageReceived() {
                val data = prover.getMessage()
                messageReceivedByProver[0] = data!!.clone()
                proverMessageReceivedCondVar.open()
            }
        }, executor)
        verifier.setListener(object : DataTransport.Listener {
            override fun onConnecting() {}
            override fun onConnected() {
                verifierPeerConnectedCondVar.open()
            }

            override fun onDisconnected() {
                Assert.fail()
            }

            override fun onTransportSpecificSessionTermination() {
                Assert.fail()
            }

            override fun onError(error: Throwable) {
                Assert.fail()
            }

            override fun onMessageReceived() {
                val data = verifier.getMessage()
                messageReceivedByVerifier[0] = data!!.clone()
                verifierMessageReceivedCondVar.open()
            }
        }, executor)
        prover.connect()
        verifier.setHostAndPort(prover.host, prover.port)
        verifier.connect()
        Assert.assertTrue(verifierPeerConnectedCondVar.block(5000))
        verifier.sendMessage(messageSentByVerifier)
        Assert.assertTrue(proverPeerConnectedCondVar.block(5000))
        prover.sendMessage(messageSentByProver)

        // Since we send the message over a socket and this data is received in a separate thread
        // we need to sit here and wait until this data is delivered to the callbacks above.
        //
        Assert.assertTrue(proverMessageReceivedCondVar.block(5000))
        Assert.assertTrue(verifierMessageReceivedCondVar.block(5000))
        Assert.assertArrayEquals(messageSentByVerifier, messageReceivedByProver[0])
        Assert.assertArrayEquals(messageSentByProver, messageReceivedByVerifier[0])
        verifier.close()
        Assert.assertTrue(proverPeerDisconnectedCondVar.block(5000))

        // TODO: also test the path where the prover calls disconnect().
    }
}
