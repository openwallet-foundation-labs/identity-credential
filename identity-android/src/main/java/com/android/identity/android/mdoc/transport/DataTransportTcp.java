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

package com.android.identity.android.mdoc.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.nfc.NdefRecord;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc;
import com.android.identity.util.Logger;
import com.android.identity.internal.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * TCP data transport.
 *
 * <p>This is a private non-standardized data transport. It is only here for testing purposes.
 */
public class DataTransportTcp extends DataTransport {
    private static final String TAG = "DataTransportTcp";
    // The maximum message size we support.
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    Socket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    ServerSocket mServerSocket = null;
    Thread mSocketWriterThread;
    private String mHost;
    private int mPort;
    private MessageRewriter mMessageRewriter;

    public DataTransportTcp(@NonNull Context context,
                            @Role int role,
                            @NonNull DataTransportOptions options) {
        super(context, role, options);
    }

    @SuppressWarnings("deprecation")
    static private String getWifiIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    private void connectAsMdoc() {
        try {
            mServerSocket = new ServerSocket(mPort);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mServerSocket.getLocalPort();
        Thread socketServerThread = new Thread() {
            @Override
            public void run() {
                try {
                    // We only accept a single client with this server socket...
                    //
                    mSocket = mServerSocket.accept();
                    mServerSocket = null;

                    setupWritingThread();

                    reportConnected();

                    Throwable e = processMessagesFromSocket();
                    if (e != null) {
                        reportError(e);
                    } else {
                        reportDisconnected();
                    }

                } catch (Exception e) {
                    reportError(e);
                }
            }
        };
        socketServerThread.start();
        if (mHost == null || mHost.length() == 0) {
            mHost = getWifiIpAddress(mContext);
        }
        if (mPort == 0) {
            mPort = port;
        }
    }

    // Should be called from worker thread to handle incoming messages from the peer.
    //
    // Will call reportMessageReceived() when a new message arrives.
    //
    // Returns a Throwable if an error occurred, null if the peer disconnects.
    //
    @SuppressWarnings("ByteBufferBackingArray")
    Throwable processMessagesFromSocket() {
        Throwable errorToReport = null;
        try {
            InputStream inputStream = mSocket.getInputStream();
            while (!mSocket.isClosed()) {
                ByteBuffer encodedHeader = Util.readBytes(inputStream, 8);
                if (encodedHeader == null) {
                    // End Of Stream
                    break;
                }
                if (!(encodedHeader.get(0) == 'G'
                        && encodedHeader.get(1) == 'm'
                        && encodedHeader.get(2) == 'D'
                        && encodedHeader.get(3) == 'L')) {
                    errorToReport = new Error("Unexpected header");
                    break;
                }
                encodedHeader.order(ByteOrder.BIG_ENDIAN);
                int dataLen = encodedHeader.getInt(4);
                if (dataLen > MAX_MESSAGE_SIZE) {
                    // This is mostly to avoid clients trying to fool us into e.g. allocating and
                    // reading 2 GiB worth of data.
                    errorToReport = new Error("Maximum message size exceeded");
                    break;
                }
                ByteBuffer data = Util.readBytes(inputStream, dataLen);
                if (data == null) {
                    // End Of Stream
                    errorToReport = new Error("End of stream, expected " + dataLen + " bytes");
                    break;
                }
                byte[] incomingMessage = data.array();
                if (mMessageRewriter != null) {
                    incomingMessage = mMessageRewriter.rewrite(incomingMessage);
                }
                reportMessageReceived(incomingMessage);
            }
        } catch (IOException e) {
            errorToReport = e;
        }

        return errorToReport;
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public void setHostAndPort(String host, int port) {
        mHost = host;
        mPort = port;
    }

    private void connectAsMdocReader() {
        mSocket = new Socket();
        Thread socketReaderThread = new Thread() {
            @Override
            public void run() {
                SocketAddress endpoint = new InetSocketAddress(mHost, mPort);
                try {
                    mSocket.connect(endpoint);
                } catch (IOException e) {
                    reportError(e);
                    return;
                }

                reportConnected();

                setupWritingThread();

                Throwable e = processMessagesFromSocket();
                if (e != null) {
                    reportError(e);
                } else {
                    reportDisconnected();
                }
            }
        };
        socketReaderThread.start();
    }

    @Override
    public void connect() {
        if (mRole == ROLE_MDOC) {
            connectAsMdoc();
        } else {
            connectAsMdocReader();
        }
    }

    void setupWritingThread() {
        mSocketWriterThread = new Thread() {
            @Override
            public void run() {
                while (mSocket.isConnected()) {
                    byte[] messageToSend = null;
                    try {
                        messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                        if (messageToSend == null) {
                            continue;
                        }
                        // An empty message is used to convey that the writing thread should be
                        // shut down.
                        if (messageToSend.length == 0) {
                            Logger.d(TAG, "Empty message, shutting down writer");
                            break;
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }

                    try {
                        mSocket.getOutputStream().write(messageToSend);
                        reportMessageProgress(messageToSend.length, messageToSend.length);
                    } catch (IOException e) {
                        reportError(e);
                        break;
                    }
                }
            }
        };
        mSocketWriterThread.start();
    }

    @Override
    public void close() {
        inhibitCallbacks();
        if (mSocketWriterThread != null) {
            mWriterQueue.add(new byte[0]);
            try {
                mSocketWriterThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        ByteBuffer bb = ByteBuffer.allocate(8 + data.length);
        bb.put("GmDL".getBytes(UTF_8));
        bb.putInt(data.length);
        bb.put(data);
        mWriterQueue.add(bb.array());
    }

    @Override
    public void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return new ConnectionMethodTcp(mHost, mPort);
    }

    // Function to rewrite incoming messages, used only for testing to inject errors
    // which will cause decryption to fail.
    public void setMessageRewriter(@Nullable MessageRewriter rewriter) {
        mMessageRewriter = rewriter;
    }

    public interface MessageRewriter {
        @NonNull byte[] rewrite(@NonNull byte[] message);
    }

    static @NonNull
    DataTransport fromConnectionMethod(@NonNull Context context,
                                       @NonNull ConnectionMethodTcp cm,
                                       @DataTransport.Role int role,
                                       @NonNull DataTransportOptions options) {
        DataTransportTcp t = new DataTransportTcp(context, role, options);
        t.setHostAndPort(cm.getHost(), cm.getPort());
        return t;
    }

    public static @Nullable
    Pair<NdefRecord, byte[]> toNdefRecord(@NonNull ConnectionMethodTcp cm,
                                          @NonNull List<String> auxiliaryReferences,
                                          boolean isForHandoverSelect) {
        byte[] reference = String.format("%d", ConnectionMethodTcp.METHOD_TYPE).getBytes(StandardCharsets.UTF_8);
        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.android.ic.dmr".getBytes(StandardCharsets.UTF_8),
                reference,
                cm.toDeviceEngagement());

        // From 7.1 Alternative Carrier Record
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(reference.length);
        try {
            baos.write(reference);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        baos.write(0x01); // Number of auxiliary references
        byte[] auxReference = "mdoc".getBytes(StandardCharsets.UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }
}
