/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.nfc.NdefRecord;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.security.identity.Constants.LoggingFlag;
import co.nstant.in.cbor.builder.ArrayBuilder;
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
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * @hide
 */
public class DataTransportTcp extends DataTransport {
    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = -18224;  // Google specific method 0
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_ADDRESS = 0;
    public static final int RETRIEVAL_OPTION_KEY_PORT = 1;
    private static final String TAG = "DataTransportTcp";
    Socket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    ServerSocket mServerSocket = null;

    public DataTransportTcp(@NonNull Context context) {
        super(context);
    }

    static private String getWifiIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    static class DataRetrievalAddressTcp extends DataRetrievalAddress {
        DataRetrievalAddressTcp(String host, int port) {
            this.host = host;
            this.port = port;
        }

        String host;
        int port;

        @Override
        @NonNull DataTransport getDataTransport(
            @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportTcp(context /*, loggingFlags*/);
        }

        @Override
        Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses) {
            byte[] reference = String.format("%d", DEVICE_RETRIEVAL_METHOD_TYPE)
                .getBytes(StandardCharsets.UTF_8);
            NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.android.ic.dmr".getBytes(StandardCharsets.UTF_8),
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
            byte[] auxReference = "mdoc".getBytes(StandardCharsets.UTF_8);
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
        public @NonNull String toString() {
            return "tcp:host=" + host + ":port=" + port;
        }
    }

    static public @Nullable
    List<DataRetrievalAddress> parseDeviceRetrievalMethod(int version, DataItem[] items) {
        if (version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            Log.w(TAG, "Unexpected version " + version + " for retrieval method");
            return null;
        }
        if (items.length < 3 || !(items[2] instanceof Map)) {
            Log.w(TAG, "Item 3 in device retrieval array is not a map");
        }
        Map options = ((Map) items[2]);

        String host = Util.cborMapExtractString(options, RETRIEVAL_OPTION_KEY_ADDRESS);
        int port = Util.cborMapExtractNumber(options, RETRIEVAL_OPTION_KEY_PORT);

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressTcp(host, port));
        return addresses;
    }

    private DataRetrievalAddressTcp mListeningAddress;

    @Override
    public @NonNull DataRetrievalAddress getListeningAddress() {
        return mListeningAddress;
    }

    @Override
    public void listen() {
        int port = 0;
        String address = getWifiIpAddress(mContext);
        try {
            mServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        port = mServerSocket.getLocalPort();
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

                } catch (IOException e) {
                    reportError(e);
                }
            }
        });
        socketServerThread.start();

        mListeningAddress = new DataRetrievalAddressTcp(address, port);

        reportListeningSetupCompleted(mListeningAddress);
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
            boolean keepGoing = true;
            while (!mSocket.isClosed() && keepGoing) {
                ByteBuffer encodedHeader = Util.readBytes(inputStream, 8);
                if (encodedHeader == null) {
                    // End Of Stream
                    keepGoing = false;
                    break;
                }
                if (!(encodedHeader.array()[0] == 'G'
                        && encodedHeader.array()[1] == 'm'
                        && encodedHeader.array()[2] == 'D'
                        && encodedHeader.array()[3] == 'L')) {
                    errorToReport = new Error("Unexpected header");
                    keepGoing = false;
                    break;
                }
                encodedHeader.order(ByteOrder.BIG_ENDIAN);
                int dataLen = encodedHeader.getInt(4);
                ByteBuffer data = Util.readBytes(inputStream, dataLen);
                if (data == null) {
                    // End Of Stream
                    errorToReport = new Error("End of stream, expected " + dataLen + " bytes");
                    keepGoing = false;
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
    public void connect(@NonNull DataRetrievalAddress genericAddress) {
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
                    e.printStackTrace();
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
        Thread socketWriterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mSocket.isConnected()) {
                    byte[] messageToSend = null;
                    try {
                        messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                        if (messageToSend == null) {
                            continue;
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
        socketWriterThread.start();
    }

    @Override
    public void close() {
        inhibitCallbacks();
        // TODO: flush before closing instead of this hack.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        byte[] encodedData = new byte[8 + data.length];
        encodedData[0] = 'G';
        encodedData[1] = 'm';
        encodedData[2] = 'D';
        encodedData[3] = 'L';
        encodedData[4] = (byte) ((data.length >> 24) & 0xff);
        encodedData[5] = (byte) ((data.length >> 16) & 0xff);
        encodedData[6] = (byte) ((data.length >> 8) & 0xff);
        encodedData[7] = (byte) (data.length & 0xff);
        System.arraycopy(data, 0, encodedData, 8, data.length);
        mWriterQueue.add(encodedData);
    }


    @Override
    public void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }
}
