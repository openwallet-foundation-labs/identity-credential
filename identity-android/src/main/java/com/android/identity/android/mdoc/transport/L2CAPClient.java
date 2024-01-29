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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.identity.util.Logger;
import com.android.identity.internal.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

class L2CAPClient {
    private static final String TAG = "L2CAPClient";
    private final Context mContext;
    Listener mListener;

    private BluetoothSocket mSocket;
    private final BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    Thread mWritingThread;

    private boolean mInhibitCallbacks = false;

    L2CAPClient(Context context, @Nullable Listener listener) {
        mContext = context;
        mListener = listener;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void connect(@NonNull BluetoothDevice bluetoothDevice, int psm) {

        // As per https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#cancelDiscovery()
        // we should cancel any ongoing discovery since it interfere with the connection process
        BluetoothAdapter adapter =
                mContext.getSystemService(BluetoothManager.class).getAdapter();
        if (!adapter.cancelDiscovery()) {
            reportError(new Error("Error canceling BluetoothDiscovery"));
            return;
        }

        Logger.d(TAG, "Connecting to " + bluetoothDevice.getAddress() + " and PSM " + psm);
        Thread connectThread = new Thread() {
            @Override
            public void run() {
                try {
                    mSocket = bluetoothDevice.createInsecureL2capChannel(psm);
                    mSocket.connect();
                } catch (IOException e) {
                    Logger.e(TAG, "Error connecting to L2CAP socket: " + e.getMessage(), e);
                    reportError(new Error("Error connecting to L2CAP socket: " + e.getMessage(), e));
                    mSocket = null;
                    return;
                }

                // Start writing thread
                mWritingThread = new Thread(L2CAPClient.this::writeToSocket);
                mWritingThread.start();

                // Let the app know we're connected.
                reportPeerConnected();

                // Reuse this thread for reading
                readFromSocket();
            }
        };
        connectThread.start();
    }

    void disconnect() {
        mInhibitCallbacks = true;
        if (mWritingThread != null) {
            // This instructs the writing thread to shut down. Once all pending writes
            // are done, [mSocket] is closed there.
            mWriterQueue.add(new byte[0]);
            mWritingThread = null;
        }
    }

    void writeToSocket() {
        OutputStream os = null;
        try {
            os = mSocket.getOutputStream();
            while (true) {
                byte[] messageToSend;
                try {
                    messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                    if (messageToSend == null) {
                        continue;
                    }
                    if (messageToSend.length == 0) {
                        Logger.d(TAG, "Empty message, exiting writer thread");
                        break;
                    }
                } catch (InterruptedException e) {
                    continue;
                }

                os.write(messageToSend);
                reportMessageSendProgress(messageToSend.length, messageToSend.length);
            }
        } catch (IOException e) {
            Logger.e(TAG, "Error using L2CAP socket", e);
            reportError(new Error("Error using L2CAP socket", e));
        }

        try {
            // TODO: This is to work around a bug in L2CAP
            Thread.sleep(1000);
            mSocket.close();
        } catch (IOException | InterruptedException e) {
            Logger.e(TAG, "Error closing socket", e);
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
                    reportPeerDisconnected();
                    break;
                }
                pendingDataBaos.write(buf, 0, numBytesRead);

                byte[] dataItemBytes = Util.cborExtractFirstDataItem(pendingDataBaos);
                if (dataItemBytes == null) {
                    continue;
                }
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