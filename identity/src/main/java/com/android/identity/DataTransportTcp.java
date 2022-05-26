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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.nfc.NdefRecord;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.identity.Constants.LoggingFlag;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

/**
 * TCP data transport.
 *
 * <p>This is a private non-standardized data transport. It is only here for testing purposes.
 */
class DataTransportTcp extends DataTransport {
    private static final String TAG = "DataTransportTcp";
    static final int DEVICE_RETRIEVAL_METHOD_TYPE = -18224;  // Google specific method 0
    static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    static final int RETRIEVAL_OPTION_KEY_ADDRESS = 0;
    static final int RETRIEVAL_OPTION_KEY_PORT = 1;
    // The maximum message size we support.
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    final Util.Logger mLog;
    Socket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    ServerSocket mServerSocket = null;
    private DataRetrievalAddressTcp mListeningAddress;
    Thread mSocketWriterThread;

    public DataTransportTcp(@NonNull Context context, @LoggingFlag int loggingFlags) {
        super(context);
        mLog = new Util.Logger(TAG, loggingFlags);
    }

    @SuppressWarnings("deprecation")
    static private String getWifiIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    }

    static @Nullable
    List<DataRetrievalAddress> parseDeviceRetrievalMethod(int version, @NonNull DataItem[] items) {
        if (version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            Log.w(TAG, "Unexpected version " + version + " for retrieval method");
            return null;
        }
        if (items.length < 3 || !(items[2] instanceof Map)) {
            Log.w(TAG, "Item 3 in device retrieval array is not a map");
            return null;
        }
        Map options = ((Map) items[2]);

        String host = Util.cborMapExtractString(options, RETRIEVAL_OPTION_KEY_ADDRESS);
        int port = Util.cborMapExtractNumber(options, RETRIEVAL_OPTION_KEY_PORT);

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressTcp(host, port));
        return addresses;
    }

    static byte[] buildDeviceRetrievalMethod(String address, int port) {
        byte[] encodedDeviceRetrievalMethod = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                .addMap()
                .put(RETRIEVAL_OPTION_KEY_ADDRESS, address)
                .put(RETRIEVAL_OPTION_KEY_PORT, port)
                .end()
                .end()
                .build().get(0));
        return encodedDeviceRetrievalMethod;
    }

    @Override
    void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    @Override
    @NonNull
    DataRetrievalAddress getListeningAddress() {
        return mListeningAddress;
    }

    @Override
    void listen() {
        String address = getWifiIpAddress(mContext);
        try {
            mServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mServerSocket.getLocalPort();
        Thread socketServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // We only accept a single client with this server socket...
                    //
                    mSocket = mServerSocket.accept();
                    mServerSocket = null;

                    setupWritingThread();

                    reportListeningPeerConnecting();
                    reportListeningPeerConnected();

                    Throwable e = processMessagesFromSocket();
                    if (e != null) {
                        reportError(e);
                    } else {
                        reportListeningPeerDisconnected();
                    }

                } catch (Exception e) {
                    reportError(e);
                }
            }
        });
        socketServerThread.start();

        mListeningAddress = new DataRetrievalAddressTcp(address, port);
        reportListeningSetupCompleted(mListeningAddress);
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
                reportMessageReceived(data.array());
            }
        } catch (IOException e) {
            errorToReport = e;
        }

        return errorToReport;
    }

    @Override
    void connect(@NonNull DataRetrievalAddress genericAddress) {
        DataRetrievalAddressTcp address = (DataRetrievalAddressTcp) genericAddress;

        // This hack is here solely here to work around that the app apparently can only
        // connect to its own port using 127.0.0.1 and not IP on the Wifi interface.
        //
        final String ipAddress =
                address.host.equals(getWifiIpAddress(mContext)) ? "127.0.0.1" : address.host;
        int port = address.port;

        mSocket = new Socket();
        Thread socketReaderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                SocketAddress endpoint = new InetSocketAddress(ipAddress, port);
                try {
                    mSocket.connect(endpoint);
                } catch (IOException e) {
                    reportConnectionResult(e);
                    return;
                }

                reportConnectionResult(null);

                setupWritingThread();

                Throwable e = processMessagesFromSocket();
                if (e != null) {
                    reportError(e);
                } else {
                    reportConnectionDisconnected();
                }
            }
        });
        socketReaderThread.start();
    }

    void setupWritingThread() {
        mSocketWriterThread = new Thread(new Runnable() {
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
                            mLog.transportVerbose("Empty message, shutting down writer");
                            break;
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }

                    try {
                        mSocket.getOutputStream().write(messageToSend);
                    } catch (IOException e) {
                        reportError(e);
                        break;
                    }
                }
            }
        });
        mSocketWriterThread.start();
    }

    @Override
    void close() {
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
    void sendMessage(@NonNull byte[] data) {
        ByteBuffer bb = ByteBuffer.allocate(8 + data.length);
        bb.put("GmDL".getBytes(UTF_8))
        bb.putInt(data.length);
        bb.put(data);
        mWriterQueue.add(bb.array());
    }

    @Override
    void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }

    static class DataRetrievalAddressTcp extends DataRetrievalAddress {
        String host;
        int port;
        DataRetrievalAddressTcp(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        @NonNull
        DataTransport createDataTransport(
                @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportTcp(context, loggingFlags);
        }

        @Override
        Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses) {
            byte[] reference = String.format("%d", DEVICE_RETRIEVAL_METHOD_TYPE)
                    .getBytes(UTF_8);
            NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                    "application/vnd.android.ic.dmr".getBytes(UTF_8),
                    reference,
                    buildDeviceRetrievalMethod(host, port));

            // From 7.1 Alternative Carrier Record
            //
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x01); // CPS: active
            baos.write(reference.length); // Length of carrier data reference ("0")
            try {
                baos.write(reference);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            baos.write(0x01); // Number of auxiliary references
            byte[] auxReference = "mdoc".getBytes(UTF_8);
            baos.write(auxReference.length);
            baos.write(auxReference, 0, auxReference.length);
            byte[] acRecordPayload = baos.toByteArray();

            return new Pair<>(record, acRecordPayload);
        }

        @Override
        void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
                List<DataRetrievalAddress> listeningAddresses) {
            arrayBuilder.addArray()
                    .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                    .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                    .addMap()
                    .put(RETRIEVAL_OPTION_KEY_ADDRESS, host)
                    .put(RETRIEVAL_OPTION_KEY_PORT, port)
                    .end()
                    .end();
        }

        @Override
        @NonNull
        public String toString() {
            return "tcp:host=" + host + ":port=" + port;
        }
    }
}
