/*
 * Copyright 2024 The Android Open Source Project
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

import android.content.Context;
import android.net.wifi.WifiManager;
import android.nfc.NdefRecord;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * UDP data transport.
 *
 * <p>This is a private non-standardized data transport. It is only here for testing purposes.
 */
public class DataTransportUdp extends DataTransport {
    private static final String TAG = "DataTransportUdp";
    // The maximum message size we support.
    private static final int MAX_MESSAGE_SIZE = 64 * 1024;
    DatagramSocket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    DatagramSocket mServerSocket = null;
    Thread mSocketWriterThread;
    private String mHost;
    private int mPort;

    public DataTransportUdp(@NonNull Context context,
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

    private InetAddress mDestinationAddress = null;
    private int mDestinationPort = 0;

    private void connectAsMdoc() {
        try {
            mServerSocket = new DatagramSocket();
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mServerSocket.getLocalPort();
        Thread socketServerThread = new Thread() {
            @Override
            public void run() {
                try {
                    setupWritingThread(mServerSocket);

                    Throwable e = processMessagesFromSocket(mServerSocket, true);
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
    Throwable processMessagesFromSocket(DatagramSocket socket, boolean isMdoc) {
        Throwable errorToReport = null;
        int numMessagesReceived = 0;
        try {
            while (!socket.isClosed()) {
                byte[] packetData = new byte[MAX_MESSAGE_SIZE];
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length);
                socket.receive(packet);
                byte[] message = Arrays.copyOf(packetData, packet.getLength());

                if (isMdoc && numMessagesReceived == 0) {
                    mDestinationAddress = packet.getAddress();
                    mDestinationPort = packet.getPort();
                    reportConnected();
                }

                reportMessageReceived(message);
                numMessagesReceived++;
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
        try {
            mDestinationAddress = InetAddress.getByName(mHost);
        } catch (UnknownHostException e) {
            reportError(e);
            return;
        }
        mDestinationPort = mPort;

        try {
            mSocket = new DatagramSocket();
        } catch (IOException e) {
            reportError(e);
            return;
        }
        Thread socketReaderThread = new Thread() {
            @Override
            public void run() {
                reportConnected();

                setupWritingThread(mSocket);

                Throwable e = processMessagesFromSocket(mSocket, false);
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

    void setupWritingThread(DatagramSocket socket) {
        mSocketWriterThread = new Thread() {
            @Override
            public void run() {
                while (true) {
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

                    Logger.iHex(TAG, String.format("data to %s port %d", mDestinationAddress, mDestinationPort), messageToSend);
                    DatagramPacket packet = new DatagramPacket(
                            messageToSend,
                            messageToSend.length,
                            mDestinationAddress,
                            mDestinationPort);

                    try {
                        socket.send(packet);
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
            mServerSocket.close();
            mServerSocket = null;
        }
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        mWriterQueue.add(data);
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
        return new ConnectionMethodUdp(mHost, mPort);
    }

    static @NonNull
    DataTransport fromConnectionMethod(@NonNull Context context,
                                       @NonNull ConnectionMethodUdp cm,
                                       @Role int role,
                                       @NonNull DataTransportOptions options) {
        DataTransportUdp t = new DataTransportUdp(context, role, options);
        t.setHostAndPort(cm.getHost(), cm.getPort());
        return t;
    }

    public static @Nullable
    Pair<NdefRecord, byte[]> toNdefRecord(@NonNull ConnectionMethodUdp cm,
                                          @NonNull List<String> auxiliaryReferences,
                                          boolean isForHandoverSelect) {
        byte[] reference = String.format("%d", ConnectionMethodUdp.METHOD_TYPE).getBytes(StandardCharsets.UTF_8);
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
