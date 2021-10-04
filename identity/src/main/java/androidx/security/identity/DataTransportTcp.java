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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    byte[] mEncodedDeviceRetrievalMethod;
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

    // (TODO: remove before landing b/c application/vnd.android.ic.dmr is not registered)
    //
    @Override
    public @Nullable
    Pair<NdefRecord, byte[]> getNdefRecords() {
        byte[] reference = String.format("%d", DEVICE_RETRIEVAL_METHOD_TYPE)
                .getBytes(StandardCharsets.UTF_8);
        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.android.ic.dmr".getBytes(StandardCharsets.UTF_8),
                reference,
                mEncodedDeviceRetrievalMethod);

        // From 7.1 Alternative Carrier Record
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(reference.length); // Length of carrier data reference ("0")
        try {
            baos.write(reference);
        } catch (IOException e) {
            reportError(e);
            return null;
        }
        baos.write(0x01); // Number of auxiliary references
        baos.write(0x04); // Length of auxiliary reference 0 data ("mdoc");
        byte[] auxReference = "mdoc".getBytes(StandardCharsets.UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }

    private Pair<String, Integer> parseDeviceRetrievalMethod(byte[] encodedDeviceRetrievalMethod) {
        DataItem d = Util.cborDecode(encodedDeviceRetrievalMethod);
        if (!(d instanceof Array)) {
            throw new IllegalArgumentException("Given CBOR is not an array");
        }
        DataItem[] items = ((Array) d).getDataItems().toArray(new DataItem[0]);
        if (items.length != 3) {
            throw new IllegalArgumentException("Expected three elements, found " + items.length);
        }
        if (!(items[0] instanceof Number)
                || !(items[1] instanceof Number)
                || !(items[2] instanceof Map)) {
            throw new IllegalArgumentException("Items not of required type");
        }
        int type = ((Number) items[0]).getValue().intValue();
        int version = ((Number) items[1]).getValue().intValue();
        if (type != DEVICE_RETRIEVAL_METHOD_TYPE
                || version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            throw new IllegalArgumentException("Unexpected type or version");
        }
        String address = Util.cborMapExtractString(items[2], RETRIEVAL_OPTION_KEY_ADDRESS);
        int port = Util.cborMapExtractNumber(items[2], RETRIEVAL_OPTION_KEY_PORT);
        return Pair.create(address, port);
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

        mEncodedDeviceRetrievalMethod = buildDeviceRetrievalMethod(address, port);
        reportListeningSetupCompleted(mEncodedDeviceRetrievalMethod);
    }

    private byte[] buildDeviceRetrievalMethod(String address, int port) {
        mEncodedDeviceRetrievalMethod = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                .addMap()
                .put(RETRIEVAL_OPTION_KEY_ADDRESS, address)
                .put(RETRIEVAL_OPTION_KEY_PORT, port)
                .end()
                .end()
                .build().get(0));
        return mEncodedDeviceRetrievalMethod;
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
    public void connect(@NonNull byte[] encodedDeviceRetrievalMethod) {
        mEncodedDeviceRetrievalMethod = encodedDeviceRetrievalMethod;
        Pair<String, Integer> addressAndPort =
                parseDeviceRetrievalMethod(encodedDeviceRetrievalMethod);

        // This hack is here solely here to work around that the app apparently can only
        // connect to its own port using 127.0.0.1 and not IP on the Wifi interface.
        //
        final String address =
                addressAndPort.first.equals(getWifiIpAddress(mContext)) ? "127.0.0.1" :
                        addressAndPort.first;
        int port = addressAndPort.second;

        mSocket = new Socket();
        Thread socketReaderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                SocketAddress endpoint = new InetSocketAddress(address, port);
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

    @Override
    public @NonNull
    byte[] getEncodedDeviceRetrievalMethod() {
        return mEncodedDeviceRetrievalMethod;
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
