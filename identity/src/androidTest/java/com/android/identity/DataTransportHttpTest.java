/*
 * Copyright 2022n The Android Open Source Project
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

import android.content.Context;
import android.os.ConditionVariable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
public class DataTransportHttpTest {

    @Test
    @SmallTest
    public void connectAndListenNoTls() {
        connectAndListen(false);
    }

    // TODO: add tests for TLS support

    @Test
    @SmallTest
    public void uriParsing() {
        Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
        DataTransportOptions options = new DataTransportOptions.Builder().build();

        ConnectionMethodHttp cm = new ConnectionMethodHttp("http://www.example.com/mdlReader/session123");
        DataTransportHttp transport = (DataTransportHttp) cm.createDataTransport(appContext, DataTransport.ROLE_MDOC, options);
        Assert.assertEquals("www.example.com", transport.getHost());
        Assert.assertEquals("/mdlReader/session123", transport.getPath());
        Assert.assertEquals(80, transport.getPort());
        Assert.assertFalse(transport.getUseTls());

        cm = new ConnectionMethodHttp("http://www.example2.com:1234/mdlVerifier/session456");
        transport = (DataTransportHttp) cm.createDataTransport(appContext, DataTransport.ROLE_MDOC, options);
        Assert.assertEquals("www.example2.com", transport.getHost());
        Assert.assertEquals("/mdlVerifier/session456", transport.getPath());
        Assert.assertEquals(1234, transport.getPort());
        Assert.assertFalse(transport.getUseTls());

        cm = new ConnectionMethodHttp("https://www.example3.net/mdocreader/s42");
        transport = (DataTransportHttp) cm.createDataTransport(appContext, DataTransport.ROLE_MDOC, options);
        Assert.assertEquals("www.example3.net", transport.getHost());
        Assert.assertEquals("/mdocreader/s42", transport.getPath());
        Assert.assertEquals(443, transport.getPort());
        Assert.assertTrue(transport.getUseTls());

        cm = new ConnectionMethodHttp("https://www.example.com:8080/mdocreader/s43");
        transport = (DataTransportHttp) cm.createDataTransport(appContext, DataTransport.ROLE_MDOC, options);
        Assert.assertEquals("www.example.com", transport.getHost());
        Assert.assertEquals("/mdocreader/s43", transport.getPath());
        Assert.assertEquals(8080, transport.getPort());
        Assert.assertTrue(transport.getUseTls());

        cm = new ConnectionMethodHttp("unsupported://www.example.com/mdocreader/s43");
        try {
            transport = (DataTransportHttp) cm.createDataTransport(appContext, DataTransport.ROLE_MDOC, options);
            Assert.assertTrue(false);
        } catch (IllegalArgumentException e) {
            // expected path
        }
    }

    void connectAndListen(boolean useTls) {
        Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
        DataTransportHttp verifier = new DataTransportHttp(appContext,
                DataTransport.ROLE_MDOC_READER,
                null,
                new DataTransportOptions.Builder().build());

        byte[] messageSentByVerifier = Util.fromHex("010203");
        byte[] messageSentByProver = Util.fromHex("0405");

        final byte[][] messageReceivedByProver = {null};
        final byte[][] messageReceivedByVerifier = {null};

        final ConditionVariable proverConnectionMethodReadyCondVar = new ConditionVariable();
        final ConditionVariable proverMessageReceivedCondVar = new ConditionVariable();
        final ConditionVariable proverPeerConnectedCondVar = new ConditionVariable();
        final ConditionVariable proverPeerDisconnectedCondVar = new ConditionVariable();
        final ConditionVariable verifierConnectionMethodReadyCondVar = new ConditionVariable();
        final ConditionVariable verifierMessageReceivedCondVar = new ConditionVariable();
        final ConditionVariable verifierPeerConnectedCondVar = new ConditionVariable();
        Executor executor = Executors.newSingleThreadExecutor();

        verifier.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
                verifierConnectionMethodReadyCondVar.open();
            }

            @Override
            public void onConnecting() {
                Assert.fail();
            }

            @Override
            public void onConnected() {
                verifierPeerConnectedCondVar.open();
            }

            @Override
            public void onDisconnected() {
                Assert.fail();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Assert.fail();
            }

            @Override
            public void onMessageReceived() {
                byte[] data = verifier.getMessage();
                messageReceivedByVerifier[0] = data.clone();
                verifierMessageReceivedCondVar.open();
            }
        }, executor);
        verifier.connect();
        Assert.assertTrue(verifierConnectionMethodReadyCondVar.block(5000));

        ConnectionMethodHttp verifierConnectionMethod =
                (ConnectionMethodHttp) verifier.getConnectionMethod();

        DataTransportHttp prover = new DataTransportHttp(appContext,
                DataTransport.ROLE_MDOC,
                verifierConnectionMethod,
                new DataTransportOptions.Builder().build());
        prover.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
                proverConnectionMethodReadyCondVar.open();
            }

            @Override
            public void onConnecting() {
            }

            @Override
            public void onConnected() {
                proverPeerConnectedCondVar.open();
            }

            @Override
            public void onDisconnected() {
                proverPeerDisconnectedCondVar.open();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Assert.fail();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onMessageReceived() {
                byte[] data = prover.getMessage();
                messageReceivedByProver[0] = data.clone();
                proverMessageReceivedCondVar.open();
            }
        }, executor);
        prover.connect();
        Assert.assertTrue(proverConnectionMethodReadyCondVar.block(5000));

        Assert.assertTrue(proverPeerConnectedCondVar.block(5000));
        prover.sendMessage(messageSentByProver);

        Assert.assertTrue(verifierPeerConnectedCondVar.block(5000));
        verifier.sendMessage(messageSentByVerifier);

        Assert.assertTrue(verifierMessageReceivedCondVar.block(5000));
        Assert.assertTrue(proverMessageReceivedCondVar.block(5000));

        Assert.assertArrayEquals(messageSentByProver, messageReceivedByVerifier[0]);
        Assert.assertArrayEquals(messageSentByVerifier, messageReceivedByProver[0]);
    }
}
