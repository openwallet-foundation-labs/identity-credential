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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

class L2CAPClient {
    private static final String TAG = "L2CAPClient";
    private final Context mContext;
    Listener mListener;

    private BluetoothSocket mSocket;
    private BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    Thread mWritingThread;

    private boolean mInhibitCallbacks = false;

    L2CAPClient(Context context, @Nullable Listener listener) {
        mContext = context;
        mListener = listener;
    }

    void disconnect() {
        mInhibitCallbacks = true;
        if (mWritingThread != null) {
            mWriterQueue.add(new byte[0]);
            try {
                mWritingThread.join();
            } catch (InterruptedException e) {
                Logger.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
        try {
            if (mSocket != null) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Logger.e(TAG, " Error closing socket connection " + e.getMessage(), e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void connect(@NonNull BluetoothDevice bluetoothDevice, byte[] psmValue) {
        if (psmValue == null || psmValue.length == 0 || psmValue.length > 4) {
            reportError(new Error("Invalid PSM value received on L2CAP characteristic"));
            return;
        }
        byte[] psmSized = new byte[4];
        if (psmValue.length < 4) {
            // Add 00 on left if psm length is lower than 4
            System.arraycopy(psmValue, 0, psmSized, 4 - psmValue.length, psmValue.length);
        } else {
            psmSized = psmValue;
        }
        int psm = ByteBuffer.wrap(psmSized).getInt();
        Logger.d(TAG, "Received psmValue: " + Util.toHex(psmValue) + " psm: " + psm);

        // As per https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#cancelDiscovery()
        // we should cancel any ongoing discovery since it interfere with the connection process
        BluetoothAdapter adapter =
                ((BluetoothManager) mContext.getSystemService(BluetoothManager.class)).getAdapter();
        if (!adapter.cancelDiscovery()) {
            reportError(new Error("Error canceling BluetoothDiscovery"));
            return;
        }

        Thread connectThread = new Thread() {
            @Override
            public void run() {
                try {
                    // Using insecure L2CAP allows the app to use L2CAP frictionless, otherwise
                    // Android will require bluetooth pairing with other device showing the pairing code
                    mSocket = bluetoothDevice.createInsecureL2capChannel(psm);
                    Logger.d(TAG, "Connecting using L2CAP on PSM: " + psm);
                    mSocket.connect();
                } catch (IOException e) {
                    Logger.e(TAG, "Error connecting to socket L2CAP " + e.getMessage(), e);
                    reportError(new Error("Error connecting to socket L2CAP", e));
                }

                Logger.d(TAG, "Connected using L2CAP");

                // Let the app know we're connected.
                reportPeerConnected();

                // Start writing thread
                mWritingThread = new Thread(L2CAPClient.this::writeToSocket);
                mWritingThread.start();

                // Reuse this thread for reading
                readFromSocket();
            }
        };
        connectThread.start();
    }

    void writeToSocket() {
        while (true) {
            byte[] messageToSend;
            try {
                messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (messageToSend == null) {
                    continue;
                }
                if (messageToSend.length == 0) {
                    Logger.d(TAG, "Empty message, exiting writer thread");
                    return;
                }
            } catch (InterruptedException e) {
                continue;
            }

            try {
                OutputStream os = mSocket.getOutputStream();
                os.write(messageToSend);
                os.flush();
                reportMessageSendProgress(messageToSend.length, messageToSend.length);
                Logger.d(TAG, String.format(Locale.US, "Wrote CBOR data item of size %d bytes",
                        messageToSend.length));
            } catch (IOException e) {
                Logger.e(TAG, "Error writing message on L2CAP socket", e);
                reportError(new Error("Error writing message on L2CAP socket", e));
                return;
            }
            // TODO: If we don't do this we run into problems when sending a session termination
            //   message and then immediately closing the connection, as is usual.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Logger.e(TAG, "Error sleeping after writing", e);
            }
        }
    }

    private void readFromSocket() {
        Logger.d(TAG, "Start reading socket input");
        ByteArrayOutputStream pendingDataBaos = new ByteArrayOutputStream();

        // Keep listening to the InputStream until an exception occurs.
        InputStream inputStream = null;
        try {
            inputStream = mSocket.getInputStream();
        } catch (IOException e) {
            reportError(new Error("Error on listening input stream from socket L2CAP", e));
            return;
        }
        while (true) {
            byte[] buf = new byte[DataTransportBle.L2CAP_BUF_SIZE];
            try {
                int numBytesRead = inputStream.read(buf);
                if (numBytesRead == -1) {
                    Logger.d(TAG, "End of stream reading from socket");
                    reportPeerDisconnected();
                    break;
                }
                pendingDataBaos.write(buf, 0, numBytesRead);

                byte[] dataItemBytes = Util.cborExtractFirstDataItem(pendingDataBaos);
                if (dataItemBytes == null) {
                    continue;
                }
                Logger.d(TAG, String.format(Locale.US, "Received CBOR data item of size %d bytes",
                        dataItemBytes.length));
                reportMessageReceived(dataItemBytes);
            } catch (IOException e) {
                reportError(new Error("Error on listening input stream from socket L2CAP", e));
                break;
            }
        }
    }

    void sendMessage(@NonNull byte[] data) {
        reportMessageSendProgress(0, data.length);
        mWriterQueue.add(data);
    }

    void reportPeerConnected() {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onPeerConnected();
        }
    }

    void reportPeerDisconnected() {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onPeerDisconnected();
        }
    }

    void reportMessageReceived(@NonNull byte[] data) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onMessageReceived(data);
        }
    }

    void reportError(@NonNull Throwable error) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onError(error);
        }
    }

    void reportMessageSendProgress(long progress, long max) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onMessageSendProgress(progress, max);
        }
    }

    interface Listener {
        void onPeerConnected();

        void onPeerDisconnected();

        void onMessageReceived(@NonNull byte[] data);

        void onError(@NonNull Throwable error);

        void onMessageSendProgress(long progress, long max);
    }
}